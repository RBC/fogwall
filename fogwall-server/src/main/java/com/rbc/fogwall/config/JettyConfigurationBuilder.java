package com.rbc.fogwall.config;

import com.rbc.fogwall.approval.ApprovalGateway;
import com.rbc.fogwall.approval.AutoApprovalGateway;
import com.rbc.fogwall.approval.UiApprovalGateway;
import com.rbc.fogwall.db.CompositeUrlRuleRegistry;
import com.rbc.fogwall.db.FetchStore;
import com.rbc.fogwall.db.MongoStoreFactory;
import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.PushStoreFactory;
import com.rbc.fogwall.db.UrlRuleRegistry;
import com.rbc.fogwall.db.jdbc.DataSourceFactory;
import com.rbc.fogwall.db.jdbc.JdbcFetchStore;
import com.rbc.fogwall.db.jdbc.JdbcUrlRuleRegistry;
import com.rbc.fogwall.db.memory.InMemoryUrlRuleRegistry;
import com.rbc.fogwall.db.model.AccessRule;
import com.rbc.fogwall.db.model.MatchTarget;
import com.rbc.fogwall.db.model.MatchType;
import com.rbc.fogwall.git.LocalRepositoryCache;
import com.rbc.fogwall.jetty.FogwallContext;
import com.rbc.fogwall.jetty.reload.ConfigHolder;
import com.rbc.fogwall.jetty.reload.LiveConfigLoader;
import com.rbc.fogwall.permission.JdbcRepoPermissionStore;
import com.rbc.fogwall.permission.PermissionStore;
import com.rbc.fogwall.permission.RepoPermission;
import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.provider.*;
import com.rbc.fogwall.service.CachingTokenPushIdentityResolver;
import com.rbc.fogwall.service.JdbcScmTokenCache;
import com.rbc.fogwall.service.PushIdentityResolver;
import com.rbc.fogwall.service.ScmTokenCache;
import com.rbc.fogwall.service.TokenPushIdentityResolver;
import com.rbc.fogwall.tls.SslUtil;
import com.rbc.fogwall.user.CompositeUserStore;
import com.rbc.fogwall.user.JdbcUserStore;
import com.rbc.fogwall.user.ReadOnlyUserStore;
import com.rbc.fogwall.user.ScmIdentity;
import com.rbc.fogwall.user.StaticUserStore;
import com.rbc.fogwall.user.UserEntry;
import com.rbc.fogwall.user.UserStore;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;

/**
 * Constructs runtime objects ({@link FogwallProvider}, {@link CommitConfig}, {@link PushStore}, etc.) from the parsed
 * {@link FogwallConfig}. All map-drilling and type-unsafe casting is gone — this class now just reads typed fields and
 * constructs objects.
 */
@Slf4j
public class JettyConfigurationBuilder {

    private final FogwallConfig config;
    private List<FogwallProvider> cachedProviders;
    private ProviderRegistry cachedProviderRegistry;
    private DataSource cachedDataSource;
    private MongoStoreFactory cachedMongoStoreFactory;
    private PushStore cachedPushStore;
    private FetchStore cachedFetchStore;
    private UserStore cachedUserStore;
    private ScmTokenCache cachedTokenCache;
    private RepoPermissionService cachedRepoPermissionService;
    private UrlRuleRegistry cachedUrlRuleRegistry;
    private ConfigHolder cachedConfigHolder;

    public JettyConfigurationBuilder(FogwallConfig config) {
        this.config = config;
    }

    /** Returns the configured server port. */
    public int getServerPort() {
        return config.getServer().getPort();
    }

    /** Returns the heartbeat interval in seconds (0 = disabled). */
    public int getHeartbeatIntervalSeconds() {
        return config.getServer().getHeartbeatIntervalSeconds();
    }

    /** Returns whether fail-fast validation is enabled (stop after first failure). */
    public boolean isFailFast() {
        return config.getServer().isFailFast();
    }

    /** Returns the S&amp;F upstream connect timeout in seconds (0 = no timeout). */
    public int getUpstreamConnectTimeoutSeconds() {
        return config.getServer().getUpstreamConnectTimeoutSeconds();
    }

    /** Returns the transparent-proxy connect timeout in seconds (0 = no timeout). */
    public int getProxyConnectTimeoutSeconds() {
        return config.getServer().getProxyConnectTimeoutSeconds();
    }

    /**
     * Returns the live {@link ConfigHolder} pre-populated with the initial commit config. All filters and hooks that
     * support live reload receive a {@code Supplier<CommitConfig>} backed by this holder. When {@link LiveConfigLoader}
     * fires a reload it calls {@link ConfigHolder#update} on the same instance, so all in-flight and future pushes
     * immediately see the new config.
     */
    public ConfigHolder buildConfigHolder() {
        if (cachedConfigHolder == null) {
            cachedConfigHolder = new ConfigHolder(
                    buildCommitConfig(), buildDiffScanConfig(), buildSecretScanConfig(), buildAttestations(config));
        }
        return cachedConfigHolder;
    }

    /**
     * Builds the global attestation-questions list from the top-level {@code attestations:} YAML section. Used at
     * startup and during hot-reload. Per-provider variants are not supported in this release.
     */
    public List<AttestationQuestion> buildAttestations(FogwallConfig cfg) {
        if (cfg.getAttestations() == null) return List.of();
        return List.copyOf(cfg.getAttestations());
    }

    /** Returns the {@link ReloadConfig} from the parsed config file. */
    public ReloadConfig getReloadConfig() {
        return config.getReload();
    }

    /** Returns the service URL for dashboard links, defaulting to {@code http://localhost:<port>/dashboard}. */
    public String getServiceUrl() {
        String url = config.getServer().getServiceUrl();
        return (url != null && !url.isBlank()) ? url : "http://localhost:" + getServerPort() + "/dashboard";
    }

    /** Creates the list of enabled providers from configuration. Result is cached. */
    public List<FogwallProvider> buildProviders() {
        if (cachedProviders != null) return cachedProviders;
        List<FogwallProvider> providers = new ArrayList<>();

        config.getProviders().forEach((name, providerConfig) -> {
            if (!providerConfig.isEnabled()) {
                log.info("Provider '{}' is disabled, skipping", name);
                return;
            }
            FogwallProvider provider = createProvider(name, providerConfig);
            if (provider != null) {
                providers.add(provider);
                log.info("Configured provider: {} -> {}", provider.getName(), provider.getUri());
            }
        });

        if (providers.isEmpty()) {
            log.warn("No providers configured. Add providers to fogwall.yml to enable proxying.");
        }
        cachedProviders = providers;
        return cachedProviders;
    }

    /** Builds and caches a {@link ProviderRegistry} keyed by provider name. */
    public ProviderRegistry buildProviderRegistry() {
        if (cachedProviderRegistry != null) return cachedProviderRegistry;
        Map<String, FogwallProvider> byName = new LinkedHashMap<>();
        buildProviders().forEach(p -> byName.put(p.getName(), p));
        cachedProviderRegistry = new InMemoryProviderRegistry(byName);
        return cachedProviderRegistry;
    }

    /**
     * Validates all cross-references to providers in the loaded config — {@code permissions:}, {@code rules:}, and
     * {@code users.scm-identities:} — against the configured providers list. Call this immediately after constructing
     * the builder (before any DB or server setup) so the app crashes with a clear message rather than failing later
     * deep in the startup sequence.
     *
     * <p>This is a read-only pass: it builds the {@link ProviderRegistry} (cheap) and checks every reference, but
     * writes nothing and opens no resources.
     *
     * @throws IllegalStateException if any provider reference cannot be resolved
     */
    public void validateProviderReferences() {
        buildProviderRegistry(); // warm the cache

        config.getUsers()
                .forEach(uc -> uc.getScmIdentities().forEach(s -> {
                    if (!"proxy".equals(s.getProvider())) {
                        resolveProviderName("User '" + uc.getUsername() + "' scm-identity", s.getProvider());
                    }
                }));

        config.getPermissions()
                .forEach(p -> resolveProviderName("Permission for user '" + p.getUsername() + "'", p.getProvider()));

        config.getRules()
                .getAllow()
                .forEach(rule -> resolveProviderName("ALLOW rule (order=" + rule.getOrder() + ")", rule.getProvider()));

        config.getRules()
                .getDeny()
                .forEach(rule -> resolveProviderName("DENY rule (order=" + rule.getOrder() + ")", rule.getProvider()));

        log.debug(
                "Provider reference validation passed ({} users, {} permissions, {} allow rules, {} deny rules)",
                config.getUsers().size(),
                config.getPermissions().size(),
                config.getRules().getAllow().size(),
                config.getRules().getDeny().size());
    }

    /**
     * Validates that {@code name} refers to a configured provider and returns it. Returns {@code null} for null/blank
     * input (meaning "applies to all providers"). Throws {@link IllegalStateException} on startup if unknown —
     * misconfiguration must be caught early.
     */
    private String resolveProviderName(String context, String name) {
        if (name == null || name.isBlank()) return null;
        return buildProviderRegistry()
                .resolveProvider(name)
                .orElseThrow(() -> {
                    String known = buildProviderRegistry().getProviders().stream()
                            .map(p -> "'" + p.getName() + "'")
                            .collect(Collectors.joining(", "));
                    return new IllegalStateException(String.format(
                            "%s references unknown provider '%s'. "
                                    + "Use the provider name from providers: config (e.g. 'github'). "
                                    + "Configured providers: %s",
                            context, name, known));
                })
                .getName();
    }

    /**
     * Builds all config-sourced URL access rules (both allow and deny). Rules are provider-scoped via the
     * {@code provider} field — {@code null} means the rule applies to all providers. Call
     * {@link UrlRuleRegistry#seedFromConfig} with the result at startup.
     */
    public List<AccessRule> buildConfigRules() {
        List<AccessRule> rules = new ArrayList<>();
        appendAccessRules(rules, config.getRules().getAllow(), AccessRule.Access.ALLOW);
        appendAccessRules(rules, config.getRules().getDeny(), AccessRule.Access.DENY);
        return rules;
    }

    /**
     * Builds a {@link CommitConfig} from the {@code commit:} YAML section. Pattern strings are compiled here; absent or
     * blank strings produce permissive defaults (no restriction).
     */
    public CommitConfig buildCommitConfig() {
        CommitSettings cs = config.getCommit();

        CommitConfig.AuthorConfig authorConfig =
                buildAuthorConfig(cs.getAuthor().getEmail());
        CommitConfig.CommitterConfig committerConfig =
                buildCommitterConfig(cs.getCommitter().getEmail());

        CommitConfig.MessageConfig messageConfig = CommitConfig.MessageConfig.builder()
                .block(buildBlockConfig(cs.getMessage().getBlock()))
                .build();

        CommitSettings.IdentityVerificationSettings ivs = cs.getIdentityVerification();
        CommitConfig.IdentityVerificationConfig identityVerificationConfig =
                CommitConfig.IdentityVerificationConfig.builder()
                        .committer(CommitConfig.IdentityVerificationMode.fromString(ivs.getCommitter()))
                        .author(CommitConfig.IdentityVerificationMode.fromString(ivs.getAuthor()))
                        .build();

        CommitConfig commitConfig = CommitConfig.builder()
                .identityVerification(identityVerificationConfig)
                .author(authorConfig)
                .committer(committerConfig)
                .message(messageConfig)
                .build();

        log.info(
                "Loaded commit config: committer.domain.allow={}, committer.local.block={}, message.literals={}, message.patterns={}",
                cs.getCommitter().getEmail().getDomain().getAllow(),
                cs.getCommitter().getEmail().getLocal().getBlock(),
                commitConfig.getMessage().getBlock().getLiterals().size(),
                commitConfig.getMessage().getBlock().getPatterns().size());

        return commitConfig;
    }

    private CommitConfig.AuthorConfig buildAuthorConfig(CommitSettings.EmailSettings email) {
        return CommitConfig.AuthorConfig.builder()
                .email(buildEmailConfig(email))
                .build();
    }

    private CommitConfig.CommitterConfig buildCommitterConfig(CommitSettings.EmailSettings email) {
        return CommitConfig.CommitterConfig.builder()
                .email(buildEmailConfig(email))
                .build();
    }

    private CommitConfig.EmailConfig buildEmailConfig(CommitSettings.EmailSettings email) {
        String domainAllow = email.getDomain().getAllow();
        CommitConfig.DomainConfig domainConfig = (domainAllow != null && !domainAllow.isBlank())
                ? CommitConfig.DomainConfig.builder()
                        .allow(Pattern.compile(domainAllow))
                        .build()
                : CommitConfig.DomainConfig.builder().build();

        String localBlock = email.getLocal().getBlock();
        CommitConfig.LocalConfig localConfig = (localBlock != null && !localBlock.isBlank())
                ? CommitConfig.LocalConfig.builder()
                        .block(Pattern.compile(localBlock))
                        .build()
                : CommitConfig.LocalConfig.builder().build();

        return CommitConfig.EmailConfig.builder()
                .domain(domainConfig)
                .local(localConfig)
                .build();
    }

    /**
     * Builds the {@link DiffScanConfig} from {@code diff-scan:} in fogwall.yml. Compiles literal and regex-pattern
     * block lists applied against push diff added-lines.
     */
    public DiffScanConfig buildDiffScanConfig() {
        DiffScanConfig cfg = DiffScanConfig.builder()
                .block(buildBlockConfig(config.getDiffScan().getBlock()))
                .build();
        log.info(
                "Loaded diff-scan config: literals={}, patterns={}",
                cfg.getBlock().getLiterals().size(),
                cfg.getBlock().getPatterns().size());
        return cfg;
    }

    /** Builds the {@link SecretScanConfig} from {@code secret-scan:} in fogwall.yml. */
    public SecretScanConfig buildSecretScanConfig() {
        SecretScanSettings ss = config.getSecretScan();
        String inlineConfig = ss.getInlineConfig();
        String configFile = ss.getConfigFile();
        if (inlineConfig != null && !inlineConfig.isBlank() && configFile != null && !configFile.isBlank()) {
            log.warn("secret-scan: both inline-config and config-file are set — inline-config takes precedence");
        }
        SecretScanConfig cfg = SecretScanConfig.builder()
                .enabled(ss.isEnabled())
                .autoInstall(ss.isAutoInstall())
                .installDir(ss.getInstallDir())
                .version(ss.getVersion())
                .scannerPath(ss.getScannerPath())
                .configFile(configFile)
                .inlineConfig(inlineConfig)
                .timeoutSeconds(ss.getTimeoutSeconds())
                .build();
        log.info("Loaded secret-scan config: enabled={}", cfg.isEnabled());
        return cfg;
    }

    /**
     * Builds the complete {@link FogwallContext} using the config-derived {@link ApprovalGateway} (based on
     * {@code server.approval-mode}).
     */
    public FogwallContext buildProxyContext() throws IOException {
        PushStore ps = buildPushStore();
        return buildProxyContextWith(buildApprovalGateway(ps));
    }

    /**
     * Builds the complete {@link FogwallContext} with a caller-supplied {@link ApprovalGateway}. Used by the dashboard
     * application, which always forces {@link UiApprovalGateway} regardless of config.
     */
    public FogwallContext buildProxyContext(ApprovalGateway approvalGateway) throws IOException {
        return buildProxyContextWith(approvalGateway);
    }

    private FogwallContext buildProxyContextWith(ApprovalGateway approvalGateway) throws IOException {
        PushStore ps = buildPushStore();
        FetchStore fs = buildFetchStore();
        UserStore us = buildUserStore();
        UrlRuleRegistry rr = buildUrlRuleRegistry();
        var storeForwardCache = new LocalRepositoryCache(Files.createTempDirectory("fogwall-sf-"), 0, true);
        log.info("Initialized store-and-forward LocalRepositoryCache (full clone)");
        var proxyCache = new LocalRepositoryCache();
        log.info("Initialized proxy LocalRepositoryCache (shallow clone)");
        return new FogwallContext(
                ps,
                fs,
                us,
                rr,
                buildRepoPermissionService(),
                buildPushIdentityResolver(us),
                approvalGateway,
                buildCommitConfig(),
                getServiceUrl(),
                getHeartbeatIntervalSeconds(),
                isFailFast(),
                getUpstreamConnectTimeoutSeconds(),
                getProxyConnectTimeoutSeconds(),
                storeForwardCache,
                proxyCache,
                buildUpstreamTls(),
                buildProviderRegistry());
    }

    /**
     * Builds the upstream {@link SslUtil.UpstreamTls} from the configured CA bundle, or returns {@code null} if no
     * custom trust is configured (JVM defaults will be used).
     */
    public SslUtil.UpstreamTls buildUpstreamTls() {
        var tls = config.getServer().getTls();
        if (!tls.isUpstreamTrustConfigured()) {
            return null;
        }
        try {
            Path bundle = Path.of(tls.getTrustCaBundle());
            SslUtil.UpstreamTls result = SslUtil.buildUpstreamTls(bundle);
            log.info("Loaded upstream CA trust bundle from {}", bundle);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load upstream CA bundle: " + tls.getTrustCaBundle(), e);
        }
    }

    /** Returns the TLS config for the server listener. */
    public TlsConfig getTlsConfig() {
        return config.getServer().getTls();
    }

    /** Builds a {@link PermissionStore} backed by the configured database. */
    public PermissionStore<RepoPermission> buildRepoPermissionStore() {
        String type = config.getDatabase().getType();
        if ("mongo".equals(type)) {
            return requireMongoStoreFactory().repoPermissionStore();
        }
        return new JdbcRepoPermissionStore(requireJdbcDataSource());
    }

    /**
     * Builds and caches the {@link RepoPermissionService}. CONFIG-sourced entries from the {@code permissions:} YAML
     * section are seeded on first call; subsequent calls return the same instance.
     */
    public RepoPermissionService buildRepoPermissionService() {
        if (cachedRepoPermissionService != null) return cachedRepoPermissionService;
        PermissionStore<RepoPermission> store = buildRepoPermissionStore();
        store.initialize();
        cachedRepoPermissionService = new RepoPermissionService(store);

        List<RepoPermission> configPerms = buildConfigPermissions(config);
        ensurePermissionUsersExist(configPerms);
        cachedRepoPermissionService.seedFromConfig(configPerms);

        log.info("RepoPermissionService initialized with {} config permission(s)", configPerms.size());
        return cachedRepoPermissionService;
    }

    private void ensurePermissionUsersExist(List<RepoPermission> permissions) {
        UserStore us = buildUserStore();
        permissions.stream().map(RepoPermission::getUsername).distinct().forEach(us::upsertUser);
    }

    /**
     * Creates the {@link ApprovalGateway} based on the {@code server.approval-mode} config key.
     *
     * <ul>
     *   <li>{@code auto} (default) — immediately approves every clean push; no dashboard required
     *   <li>{@code ui} — polls the push store waiting for a human reviewer via the REST API
     *   <li>{@code servicenow} — delegates to a ServiceNow approval workflow
     * </ul>
     */
    public ApprovalGateway buildApprovalGateway(PushStore pushStore) {
        String mode = config.getServer().getApprovalMode();
        return switch (mode) {
            case "ui" -> {
                log.info("Approval mode: ui (push store polling)");
                yield new UiApprovalGateway(pushStore);
            }
            default -> {
                if (!"auto".equals(mode)) {
                    log.warn("Unknown approval-mode '{}', defaulting to 'auto'", mode);
                } else {
                    log.info("Approval mode: auto (no human review required)");
                }
                yield new AutoApprovalGateway(pushStore);
            }
        };
    }

    /** Creates a {@link PushStore} based on the database configuration. */
    public PushStore buildPushStore() {
        if (cachedPushStore != null) return cachedPushStore;
        DatabaseConfig db = config.getDatabase();
        log.info("Initializing push store: type={}", db.getType());
        cachedPushStore = switch (db.getType()) {
            case "h2-mem", "h2-file", "postgres" -> PushStoreFactory.fromDataSource(requireJdbcDataSource());
            case "mongo" -> requireMongoStoreFactory().pushStore();
            default ->
                throw new IllegalArgumentException(
                        "Unknown database type: " + db.getType() + ". Supported: h2-mem, h2-file, postgres, mongo");
        };
        return cachedPushStore;
    }

    /**
     * Builds the list of CONFIG-sourced {@link AccessRule}s from the {@code rules:} YAML section. Used both at startup
     * (seeding the registry) and during hot-reload (re-seeding via {@link UrlRuleRegistry#seedFromConfig}).
     */
    public List<AccessRule> buildConfigRules(FogwallConfig cfg) {
        List<AccessRule> rules = new ArrayList<>();
        appendAccessRules(rules, cfg.getRules().getAllow(), AccessRule.Access.ALLOW);
        appendAccessRules(rules, cfg.getRules().getDeny(), AccessRule.Access.DENY);
        return rules;
    }

    /**
     * Builds the list of CONFIG-sourced {@link RepoPermission}s from the {@code permissions:} YAML section. Used both
     * at startup (seeding the service) and during hot-reload.
     */
    public List<RepoPermission> buildConfigPermissions(FogwallConfig cfg) {
        return cfg.getPermissions().stream()
                .map(p -> {
                    String resolvedId =
                            resolveProviderName("Permission for user '" + p.getUsername() + "'", p.getProvider());
                    MatchConfig m = p.getMatch();
                    return (RepoPermission) RepoPermission.builder()
                            .username(p.getUsername())
                            .provider(resolvedId)
                            .target(MatchTarget.valueOf(m.getTarget().toUpperCase()))
                            .value(m.getValue())
                            .matchType(MatchType.valueOf((m.getType() != null ? m.getType() : "GLOB").toUpperCase()))
                            .grant(RepoPermission.Grant.valueOf(p.getGrant().toUpperCase()))
                            .source(RepoPermission.Source.CONFIG)
                            .build();
                })
                .toList();
    }

    public UrlRuleRegistry buildUrlRuleRegistry() {
        if (cachedUrlRuleRegistry != null) return cachedUrlRuleRegistry;
        // CONFIG rules live only in memory — never written to DB, no stale duplicates on restart.
        InMemoryUrlRuleRegistry configRegistry = new InMemoryUrlRuleRegistry();
        buildConfigRules(config).forEach(configRegistry::save);

        String type = config.getDatabase().getType();
        UrlRuleRegistry dbRegistry;
        if ("mongo".equals(type)) {
            dbRegistry = requireMongoStoreFactory().repoRegistry();
        } else {
            dbRegistry = new JdbcUrlRuleRegistry(requireJdbcDataSource());
        }

        cachedUrlRuleRegistry = new CompositeUrlRuleRegistry(configRegistry, dbRegistry);
        cachedUrlRuleRegistry.initialize();
        log.info(
                "RepoRegistry initialized ({} config rules, {} db rules)",
                configRegistry.findAll().size(),
                dbRegistry.findAll().size());
        return cachedUrlRuleRegistry;
    }

    /** Builds a {@link FetchStore}. JDBC backends share the same {@link DataSource} as the push store. */
    public FetchStore buildFetchStore() {
        if (cachedFetchStore != null) return cachedFetchStore;
        String type = config.getDatabase().getType();
        FetchStore store;
        if ("mongo".equals(type)) {
            store = requireMongoStoreFactory().fetchStore();
        } else {
            store = new JdbcFetchStore(requireJdbcDataSource());
        }
        store.initialize();
        cachedFetchStore = store;
        return cachedFetchStore;
    }

    private void appendAccessRules(List<AccessRule> result, List<RuleConfig> rules, AccessRule.Access access) {
        for (RuleConfig rule : rules) {
            if (!rule.isEnabled()) continue;
            AccessRule.Operation ops = toOperations(rule.getOperation());
            String rawProvider = rule.getProvider().isBlank() ? null : rule.getProvider();
            String resolvedId =
                    resolveProviderName(access.name() + " rule (order=" + rule.getOrder() + ")", rawProvider);
            MatchConfig m = rule.getMatch();
            result.add(AccessRule.builder()
                    .provider(resolvedId)
                    .target(MatchTarget.valueOf(m.getTarget().toUpperCase()))
                    .value(m.getValue())
                    .matchType(MatchType.valueOf((m.getType() != null ? m.getType() : "GLOB").toUpperCase()))
                    .access(access)
                    .operation(ops)
                    .source(AccessRule.Source.CONFIG)
                    .ruleOrder(rule.getOrder())
                    .build());
        }
    }

    private static AccessRule.Operation toOperations(String ops) {
        if (ops == null || ops.isBlank()) return AccessRule.Operation.BOTH;
        return switch (ops.toUpperCase()) {
            case "FETCH" -> AccessRule.Operation.FETCH;
            case "PUSH" -> AccessRule.Operation.PUSH;
            default -> AccessRule.Operation.BOTH;
        };
    }

    /** Builds a {@link UserStore} from config. JDBC backends share the same {@link DataSource} as the push store. */
    public UserStore buildUserStore() {
        if (cachedUserStore != null) return cachedUserStore;
        List<UserEntry> staticUsers = config.getUsers().stream()
                .map(uc -> {
                    List<ScmIdentity> scmIdentities = new ArrayList<>();
                    uc.getScmIdentities().stream()
                            .map(s -> {
                                // "proxy" is a synthetic provider for push-username lookup — no resolution needed
                                String resolvedProvider = "proxy".equals(s.getProvider())
                                        ? "proxy"
                                        : resolveProviderName(
                                                "User '" + uc.getUsername() + "' scm-identity", s.getProvider());
                                return ScmIdentity.builder()
                                        .provider(resolvedProvider)
                                        .username(s.getUsername())
                                        .build();
                            })
                            .forEach(scmIdentities::add);
                    // push-usernames are stored as SCM identities under the synthetic "proxy" provider.
                    // Reserved for SCM providers (e.g. Bitbucket) that cannot return a login from a token alone.
                    uc.getPushUsernames().stream()
                            .map(pushName -> ScmIdentity.builder()
                                    .provider("proxy")
                                    .username(pushName)
                                    .build())
                            .forEach(scmIdentities::add);
                    List<String> roles = uc.getRoles().isEmpty() ? List.of("USER") : uc.getRoles();
                    return UserEntry.builder()
                            .username(uc.getUsername())
                            .passwordHash(uc.getPasswordHash())
                            .emails(uc.getEmails())
                            .scmIdentities(scmIdentities)
                            .roles(roles)
                            .build();
                })
                .toList();

        String type = config.getDatabase().getType();
        if ("mongo".equals(type)) {
            cachedTokenCache = buildTokenCache();
            var mongoStore = requireMongoStoreFactory().userStore(cachedTokenCache);
            var configStore = new StaticUserStore(staticUsers);
            log.info("Using composite user store ({} config users + MongoDB)", staticUsers.size());
            cachedUserStore = new CompositeUserStore(configStore, mongoStore);
        } else {
            cachedTokenCache = buildTokenCache();
            var jdbcStore = new JdbcUserStore(requireJdbcDataSource(), cachedTokenCache);
            var configStore = new StaticUserStore(staticUsers);
            log.info("Using composite user store ({} config users + JDBC)", staticUsers.size());
            cachedUserStore = new CompositeUserStore(configStore, jdbcStore);
        }
        return cachedUserStore;
    }

    /**
     * Builds the {@link PushIdentityResolver}. When users are configured, returns a token-based resolver that calls the
     * SCM provider API to map a PAT to an SCM login, then looks up the proxy user via SCM identity. Returns null when
     * no users are configured (open/permissive mode).
     *
     * <p>HTTP Basic-auth username is intentionally NOT used for identity resolution — it is an unverifiable claim and
     * would violate compliance guarantees. Bitbucket is a known exception (the Bitbucket API does not return a login
     * from a token alone) and must be handled separately if/when Bitbucket support is added.
     *
     * <p>For JDBC backends, the token resolver is wrapped with {@link CachingTokenPushIdentityResolver} to avoid
     * repeated SCM API calls for the same token. The cache max age defaults to 7 days and can be overridden via the
     * {@code FOGWALL_SCM_CACHE_MAX_AGE_DAYS} environment variable.
     */
    public PushIdentityResolver buildPushIdentityResolver(ReadOnlyUserStore userStore) {
        if (config.getUsers().isEmpty()) return null;

        PushIdentityResolver tokenResolver = new TokenPushIdentityResolver(userStore);

        ScmTokenCache tokenCache = cachedTokenCache != null ? cachedTokenCache : buildTokenCache();
        tokenResolver = new CachingTokenPushIdentityResolver(tokenResolver, tokenCache, userStore);

        return tokenResolver;
    }

    private ScmTokenCache buildTokenCache() {
        long maxAgeDays = Optional.ofNullable(System.getenv("FOGWALL_SCM_CACHE_MAX_AGE_DAYS"))
                .map(Long::parseLong)
                .orElse(7L);
        Duration maxAge = Duration.ofDays(maxAgeDays);
        log.info("SCM token identity cache enabled (max age {} days)", maxAgeDays);
        if ("mongo".equals(config.getDatabase().getType())) {
            return requireMongoStoreFactory().tokenCache(maxAge);
        }
        return new JdbcScmTokenCache(requireJdbcDataSource(), maxAge);
    }

    private MongoStoreFactory requireMongoStoreFactory() {
        if (cachedMongoStoreFactory == null) {
            DatabaseConfig db = config.getDatabase();
            cachedMongoStoreFactory = new MongoStoreFactory(db.getUrl(), mongoDbName(db));
        }
        return cachedMongoStoreFactory;
    }

    /**
     * Returns the JDBC {@link DataSource} for JDBC-backed database types ({@code h2-mem}, {@code h2-file},
     * {@code postgres}). Returns {@code null} for {@code mongo} — callers that need a DataSource for features like
     * session persistence should check for null and either skip or fail gracefully.
     */
    public DataSource getJdbcDataSourceOrNull() {
        if ("mongo".equals(config.getDatabase().getType())) return null;
        return requireJdbcDataSource();
    }

    /**
     * Returns the shared {@link MongoStoreFactory} if {@code database.type=mongo}, else {@code null}. Callers that need
     * a {@link com.mongodb.client.MongoClient} (e.g. the dashboard's session store wiring) should go through this
     * accessor so the underlying connection pool is reused across stores.
     */
    public MongoStoreFactory getMongoStoreFactoryOrNull() {
        if (!"mongo".equals(config.getDatabase().getType())) return null;
        return requireMongoStoreFactory();
    }

    private DataSource requireJdbcDataSource() {
        if (cachedDataSource == null) {
            DatabaseConfig db = config.getDatabase();
            var pool = db.getPool();
            log.info(
                    "Database pool: maximumPoolSize={} minimumIdle={} connectionTimeout={}ms idleTimeout={}ms maxLifetime={}ms",
                    pool.getMaximumPoolSize(),
                    pool.getMinimumIdle() >= 0 ? pool.getMinimumIdle() : "(default: matches maximumPoolSize)",
                    pool.getConnectionTimeout(),
                    pool.getIdleTimeout(),
                    pool.getMaxLifetime());
            cachedDataSource = switch (db.getType()) {
                case "h2-mem" -> DataSourceFactory.h2InMemory(db.getName(), pool);
                case "h2-file" ->
                    DataSourceFactory.h2File(db.getPath().isBlank() ? "./.data/" + db.getName() : db.getPath(), pool);
                case "postgres" -> {
                    if (!db.getUrl().isBlank()) {
                        log.info("Postgres: using connection URL (individual host/port/name fields ignored)");
                        yield DataSourceFactory.fromUrl(db.getUrl(), db.getUsername(), db.getPassword(), pool);
                    }
                    yield DataSourceFactory.postgres(
                            db.getHost(), db.getPort(), db.getName(), db.getUsername(), db.getPassword(), pool);
                }
                default -> throw new IllegalStateException("No JDBC DataSource for db type: " + db.getType());
            };
        }
        return cachedDataSource;
    }

    /**
     * Resolves the MongoDB database name. If {@code name} is non-blank, uses it directly. Otherwise attempts to extract
     * the database name from the URI path (e.g. {@code mongodb://host/mydb} → {@code mydb}), falling back to
     * {@code "fogwall"}.
     */
    private static String mongoDbName(DatabaseConfig db) {
        String name = db.getName();
        if (!name.isBlank()) return name;
        try {
            String path = URI.create(db.getUrl()).getPath();
            if (path != null && path.length() > 1) {
                String extracted = path.substring(1);
                int q = extracted.indexOf('?');
                return q >= 0 ? extracted.substring(0, q) : extracted;
            }
        } catch (Exception ignored) {
        }
        return "fogwall";
    }

    private FogwallProvider createProvider(String name, ProviderConfig providerConfig) {
        String explicitType = providerConfig.getType();
        // Use explicit type if set; otherwise accept only exact built-in names, not fuzzy name inference.
        String resolvedType = (explicitType != null && !explicitType.isBlank())
                ? explicitType.toLowerCase().trim()
                : name.toLowerCase();

        String uri = providerConfig.getUri();
        String path = providerConfig.getServletPath();
        URI parsedUri = (uri != null && !uri.isBlank()) ? URI.create(uri) : null;

        switch (resolvedType) {
            case "github" -> {
                return GitHubProvider.builder()
                        .name(name)
                        .uri(parsedUri)
                        .basePath(path)
                        .build();
            }
            case "gitlab" -> {
                return GitLabProvider.builder()
                        .name(name)
                        .uri(parsedUri)
                        .basePath(path)
                        .build();
            }
            case "bitbucket" -> {
                return BitbucketProvider.builder()
                        .name(name)
                        .uri(parsedUri)
                        .basePath(path)
                        .build();
            }
            case "codeberg", "gitea" -> {
                URI defaultUri = ForgejoProvider.WELL_KNOWN.get(resolvedType);
                return ForgejoProvider.builder()
                        .name(name)
                        .uri(parsedUri != null ? parsedUri : defaultUri)
                        .basePath(path)
                        .build();
            }
            case "forgejo" -> {
                if (parsedUri == null) {
                    log.warn(
                            "Provider '{}' has type 'forgejo' but no URI — Forgejo has no canonical public host. Add 'uri'. Skipping.",
                            name);
                    return null;
                }
                return ForgejoProvider.builder()
                        .name(name)
                        .uri(parsedUri)
                        .basePath(path)
                        .build();
            }
            default -> {
                if (parsedUri != null) {
                    return GenericProxyProvider.builder()
                            .name(name)
                            .uri(parsedUri)
                            .basePath(path)
                            .blockedInfoRefsStatus(providerConfig.getBlockedInfoRefsStatus())
                            .build();
                }
                log.warn(
                        "Provider '{}' has no URI and is not a known built-in name (github/gitlab/bitbucket/codeberg/forgejo/gitea). Set 'type' and 'uri' for custom providers. Skipping.",
                        name);
                return null;
            }
        }
    }

    private static CommitConfig.BlockConfig buildBlockConfig(BlockSettings block) {
        List<Pattern> patterns =
                block.getPatterns().stream().map(Pattern::compile).collect(Collectors.toList());
        return CommitConfig.BlockConfig.builder()
                .literals(new ArrayList<>(block.getLiterals()))
                .patterns(patterns)
                .build();
    }
}
