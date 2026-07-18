package com.rbc.fogwall.jetty;

import static org.eclipse.jgit.transport.HttpTransport.setConnectionFactory;

import com.rbc.fogwall.approval.ApprovalGateway;
import com.rbc.fogwall.config.BinaryBlobConfig;
import com.rbc.fogwall.config.CommitConfig;
import com.rbc.fogwall.config.DiffScanConfig;
import com.rbc.fogwall.config.GpgConfig;
import com.rbc.fogwall.config.JettyConfigurationBuilder;
import com.rbc.fogwall.config.SecretScanConfig;
import com.rbc.fogwall.db.FetchStore;
import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.UrlRuleRegistry;
import com.rbc.fogwall.git.LocalRepositoryCache;
import com.rbc.fogwall.git.StoreAndForwardReceivePackFactory;
import com.rbc.fogwall.git.StoreAndForwardRepositoryResolver;
import com.rbc.fogwall.git.StoreAndForwardUploadPackFactory;
import com.rbc.fogwall.jetty.reload.ConfigHolder;
import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.provider.BitbucketProvider;
import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.service.PushIdentityResolver;
import com.rbc.fogwall.servlet.FogwallServlet;
import com.rbc.fogwall.servlet.filter.*;
import com.rbc.fogwall.tls.SslAwareHttpConnectionFactory;
import com.rbc.fogwall.tls.SslUtil;
import jakarta.servlet.DispatcherType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.ee11.servlet.FilterHolder;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jgit.http.server.GitServlet;

/**
 * Utility class that registers the git proxy servlets and filters onto a Jetty {@link ServletContextHandler}. Shared
 * between the standalone server ({@link FogwallJettyApplication}) and the server-with-dashboard application in
 * {@code fogwall-dashboard}.
 */
@Slf4j
public final class FogwallServletRegistrar {

    public static final String PUSH_PATH_PREFIX = "/push";
    public static final String PROXY_PATH_PREFIX = "/proxy";

    private FogwallServletRegistrar() {}

    /**
     * Registers git servlets, proxy servlets, and filter chains for every provider. This is the primary entry point for
     * both the standalone and dashboard applications.
     */
    public static void registerProviders(
            ServletContextHandler context,
            FogwallContext fogwallContext,
            JettyConfigurationBuilder configBuilder,
            List<FogwallProvider> providers) {
        // Wire up JGit's HTTP transport factory once for all store-and-forward connections
        if (fogwallContext.upstreamTls() != null) {
            setConnectionFactory(new SslAwareHttpConnectionFactory(
                    fogwallContext.upstreamTls().trustManagers()));
            log.info("Custom upstream SSL trust applied to JGit HTTP transport");
        }
        // ForceGitClientFilter is registered once at the top-level proxy and push paths so it covers
        // any path with the right prefix, including paths that don't match a configured provider.
        var forceGitClientHolder = new FilterHolder(new ForceGitClientFilter());
        forceGitClientHolder.setAsyncSupported(true);
        context.addFilter(forceGitClientHolder, PROXY_PATH_PREFIX + "/*", EnumSet.of(DispatcherType.REQUEST));
        context.addFilter(forceGitClientHolder, PUSH_PATH_PREFIX + "/*", EnumSet.of(DispatcherType.REQUEST));

        ConfigHolder configHolder = configBuilder.buildConfigHolder();
        Supplier<CommitConfig> commitConfigSupplier = configHolder::getCommitConfig;
        Supplier<DiffScanConfig> diffScanConfigSupplier = configHolder::getDiffScanConfig;
        Supplier<SecretScanConfig> secretScanConfigSupplier = configHolder::getSecretScanConfig;
        Supplier<BinaryBlobConfig> binaryBlobConfigSupplier = configHolder::getBinaryBlobConfig;

        // Seed config rules once — registry is the single source of truth for all rule evaluation
        fogwallContext.urlRuleRegistry().seedFromConfig(configBuilder.buildConfigRules());

        for (FogwallProvider provider : providers) {
            log.info("Registering provider: {}", provider.getName());
            if (isHttpProvider(provider)) {
                registerGitServlet(
                        context,
                        provider,
                        fogwallContext.storeForwardCache(),
                        commitConfigSupplier,
                        diffScanConfigSupplier,
                        secretScanConfigSupplier,
                        binaryBlobConfigSupplier,
                        fogwallContext.pushStore(),
                        fogwallContext.serviceUrl(),
                        fogwallContext.approvalGateway(),
                        fogwallContext.pushIdentityResolver(),
                        fogwallContext.repoPermissionService(),
                        fogwallContext.heartbeatIntervalSeconds(),
                        fogwallContext.failFast(),
                        fogwallContext.upstreamConnectTimeoutSeconds(),
                        fogwallContext.urlRuleRegistry(),
                        fogwallContext.fetchStore());
                registerProxyServlet(
                        context,
                        provider,
                        fogwallContext.pushStore(),
                        fogwallContext.proxyConnectTimeoutSeconds(),
                        fogwallContext.upstreamTls());
                registerCoreFilters(
                        context,
                        provider,
                        fogwallContext.proxyCache(),
                        configBuilder,
                        commitConfigSupplier,
                        diffScanConfigSupplier,
                        secretScanConfigSupplier,
                        binaryBlobConfigSupplier,
                        fogwallContext.pushStore(),
                        fogwallContext.serviceUrl(),
                        fogwallContext.approvalGateway(),
                        fogwallContext.pushIdentityResolver(),
                        fogwallContext.repoPermissionService(),
                        fogwallContext.fetchStore(),
                        fogwallContext.urlRuleRegistry());
            } else {
                log.info(
                        "Skipping HTTP servlet registration for {} — SSH provider (scheme={})",
                        provider.getName(),
                        provider.getUri().getScheme());
            }
        }
    }

    /** Returns {@code true} when the provider URI uses an HTTP/HTTPS scheme and HTTP servlets should be registered. */
    static boolean isHttpProvider(FogwallProvider provider) {
        String scheme = provider.getUri().getScheme();
        return scheme != null && (scheme.equals("http") || scheme.equals("https"));
    }

    /**
     * Builds a {@link StoreAndForwardReceivePackFactory} for the given provider using the current context and config.
     * Used by the SSH server to share the same factory the HTTP push servlet uses.
     */
    public static StoreAndForwardReceivePackFactory buildReceivePackFactory(
            FogwallContext fogwallContext, JettyConfigurationBuilder configBuilder, FogwallProvider provider) {
        ConfigHolder configHolder = configBuilder.buildConfigHolder();
        var factory = new StoreAndForwardReceivePackFactory(
                provider,
                configHolder::getCommitConfig,
                configHolder::getDiffScanConfig,
                configHolder::getSecretScanConfig,
                configHolder::getBinaryBlobConfig,
                GpgConfig.defaultConfig(),
                fogwallContext.repoPermissionService(),
                fogwallContext.pushIdentityResolver(),
                fogwallContext.pushStore(),
                fogwallContext.approvalGateway(),
                fogwallContext.serviceUrl(),
                Duration.ofSeconds(fogwallContext.heartbeatIntervalSeconds()),
                fogwallContext.urlRuleRegistry());
        factory.setFailFast(configBuilder.isFailFast());
        factory.setConnectTimeoutSeconds(fogwallContext.upstreamConnectTimeoutSeconds());
        factory.setCache(fogwallContext.storeForwardCache());
        factory.setSshScmIdentityEnricher(fogwallContext.sshScmIdentityEnricher());
        return factory;
    }

    public static void registerGitServlet(
            ServletContextHandler context,
            FogwallProvider provider,
            LocalRepositoryCache cache,
            Supplier<CommitConfig> commitConfigSupplier,
            Supplier<DiffScanConfig> diffScanConfigSupplier,
            Supplier<SecretScanConfig> secretScanConfigSupplier,
            Supplier<BinaryBlobConfig> binaryBlobConfigSupplier,
            PushStore pushStore,
            String serviceUrl,
            ApprovalGateway approvalGateway,
            PushIdentityResolver pushIdentityResolver,
            RepoPermissionService repoPermissionService,
            int heartbeatIntervalSeconds,
            boolean failFast,
            int connectTimeoutSeconds,
            UrlRuleRegistry urlRuleRegistry,
            FetchStore fetchStore) {
        var resolver = new StoreAndForwardRepositoryResolver(cache, provider);

        var factory = new StoreAndForwardReceivePackFactory(
                provider,
                commitConfigSupplier,
                diffScanConfigSupplier,
                secretScanConfigSupplier,
                binaryBlobConfigSupplier,
                GpgConfig.defaultConfig(),
                repoPermissionService,
                pushIdentityResolver,
                pushStore,
                approvalGateway,
                serviceUrl,
                Duration.ofSeconds(heartbeatIntervalSeconds),
                urlRuleRegistry);
        factory.setFailFast(failFast);
        factory.setConnectTimeoutSeconds(connectTimeoutSeconds);
        factory.setCache(cache);

        var gitServlet = new GitServlet();
        gitServlet.setRepositoryResolver(resolver);
        gitServlet.setReceivePackFactory(factory);
        gitServlet.setUploadPackFactory(new StoreAndForwardUploadPackFactory());

        String pushPath = PUSH_PATH_PREFIX + provider.servletPath();
        String pushMapping = pushPath + "/*";

        var holder = new ServletHolder(gitServlet);
        holder.setName("git-" + provider.getName());
        context.addServlet(holder, pushMapping);

        context.addFilter(
                new FilterHolder(new SmartHttpErrorFilter()), pushMapping, EnumSet.of(DispatcherType.REQUEST));
        context.addFilter(
                new FilterHolder(new BasicAuthChallengeFilter()), pushMapping, EnumSet.of(DispatcherType.REQUEST));
        context.addFilter(
                new FilterHolder(new ParseGitRequestFilter(provider, PUSH_PATH_PREFIX)),
                pushMapping,
                EnumSet.of(DispatcherType.REQUEST));
        context.addFilter(
                new FilterHolder(
                        new UrlRuleAggregateFilter(100, provider, PUSH_PATH_PREFIX, fetchStore, urlRuleRegistry)),
                pushMapping,
                EnumSet.of(DispatcherType.REQUEST));

        log.info("Registered GitServlet for {} at {}", provider.getName(), pushMapping);
    }

    public static void registerProxyServlet(
            ServletContextHandler context,
            FogwallProvider provider,
            PushStore pushStore,
            int connectTimeoutSeconds,
            SslUtil.UpstreamTls upstreamTls) {
        String proxyPath = PROXY_PATH_PREFIX + provider.servletPath();
        String proxyMapping = proxyPath + "/*";

        var proxyServlet = new FogwallServlet(pushStore, upstreamTls != null ? upstreamTls.sslContext() : null);
        var proxyHolder = new ServletHolder(proxyServlet);
        proxyHolder.setName("proxy-" + provider.getName());
        proxyHolder.setInitParameter("proxyTo", provider.getUri().toString());
        proxyHolder.setInitParameter("prefix", proxyPath);
        proxyHolder.setInitParameter("hostHeader", provider.getUri().getHost());
        proxyHolder.setInitParameter("preserveHost", "false");
        if (connectTimeoutSeconds > 0) {
            proxyHolder.setInitParameter("connectTimeout", String.valueOf(connectTimeoutSeconds * 1000L));
        }
        context.addServlet(proxyHolder, proxyMapping);

        log.info("Registered proxy servlet for {} at {}", provider.getName(), proxyMapping);
    }

    /**
     * Registers the core proxy filter chain for the given provider. Covers all content validation including
     * {@link AllowApprovedPushFilter}, which is harmless in standalone mode because no push records are ever set to
     * {@code APPROVED} via the transparent-proxy re-push flow when running without a dashboard.
     */
    public static void registerCoreFilters(
            ServletContextHandler context,
            FogwallProvider provider,
            LocalRepositoryCache repositoryCache,
            JettyConfigurationBuilder configBuilder,
            Supplier<CommitConfig> commitConfigSupplier,
            Supplier<DiffScanConfig> diffScanConfigSupplier,
            Supplier<SecretScanConfig> secretScanConfigSupplier,
            Supplier<BinaryBlobConfig> binaryBlobConfigSupplier,
            PushStore pushStore,
            String serviceUrl,
            ApprovalGateway approvalGateway,
            PushIdentityResolver pushIdentityResolver,
            RepoPermissionService repoPermissionService,
            FetchStore fetchStore,
            UrlRuleRegistry urlRuleRegistry) {
        String urlPattern = PROXY_PATH_PREFIX + provider.servletPath() + "/*";

        // PushStoreAuditFilter wraps the entire chain via try-finally; must be registered first.
        var pushStoreAuditFilterHolder = new FilterHolder(new PushStoreAuditFilter(pushStore));
        pushStoreAuditFilterHolder.setAsyncSupported(true);
        context.addFilter(pushStoreAuditFilterHolder, urlPattern, EnumSet.of(DispatcherType.REQUEST));

        // Build the orderable filter list. Sorted by getOrder() before registration so the Jetty chain
        // execution order matches the documented order ranges in fogwallFilter.
        List<FogwallFilter> filters = new ArrayList<>();
        filters.add(new ParseGitRequestFilter(provider, PROXY_PATH_PREFIX));
        filters.add(new EnrichPushCommitsFilter(provider, repositoryCache, PROXY_PATH_PREFIX));
        filters.add(new AllowApprovedPushFilter(pushStore, serviceUrl));

        filters.add(new UrlRuleAggregateFilter(100, provider, PROXY_PATH_PREFIX, fetchStore, urlRuleRegistry));

        if (provider instanceof BitbucketProvider bitbucketProvider) {
            filters.add(new BitbucketIdentityFilter(bitbucketProvider, PROXY_PATH_PREFIX));
        }
        filters.add(new CheckUserPushPermissionFilter(pushIdentityResolver, repoPermissionService));
        filters.add(new IdentityVerificationFilter(pushIdentityResolver, commitConfigSupplier));
        filters.add(new CheckEmptyBranchFilter());
        filters.add(new CheckHiddenCommitsFilter(provider, PROXY_PATH_PREFIX));
        filters.add(new CheckAuthorEmailsFilter(commitConfigSupplier));
        filters.add(new CheckCommitMessagesFilter(commitConfigSupplier));
        filters.add(new BinaryBlobFilter(provider, PROXY_PATH_PREFIX, binaryBlobConfigSupplier));
        filters.add(new ScanDiffFilter(provider, PROXY_PATH_PREFIX, diffScanConfigSupplier));
        filters.add(new SecretScanningFilter(secretScanConfigSupplier));
        filters.add(new GpgSignatureFilter(GpgConfig.defaultConfig()));
        filters.add(new ValidationSummaryFilter());
        filters.add(new FetchFinalizerFilter());
        filters.add(new PushFinalizerFilter(serviceUrl, approvalGateway));
        filters.add(new AuditLogFilter());

        boolean failFast = configBuilder != null && configBuilder.isFailFast();
        if (failFast) {
            filters.forEach(f -> {
                if (f instanceof AbstractFogwallFilter af) af.setFailFast(true);
            });
        }

        filters.sort(Comparator.comparingInt(FogwallFilter::getOrder));

        for (FogwallFilter filter : filters) {
            var holder = new FilterHolder(filter);
            holder.setAsyncSupported(true);
            context.addFilter(holder, urlPattern, EnumSet.of(DispatcherType.REQUEST));
        }

        log.info("Registered {} proxy filters for provider {}", filters.size(), provider.getName());
    }
}
