package com.rbc.fogwall.git;

import com.rbc.fogwall.approval.ApprovalGateway;
import com.rbc.fogwall.config.BinaryBlobConfig;
import com.rbc.fogwall.config.CommitConfig;
import com.rbc.fogwall.config.ContentPatternConfig;
import com.rbc.fogwall.config.DiffScanConfig;
import com.rbc.fogwall.config.GpgConfig;
import com.rbc.fogwall.config.SecretScanConfig;
import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.UrlRuleRegistry;
import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.provider.BitbucketProvider;
import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.service.PushIdentityResolver;
import com.rbc.fogwall.service.SshScmIdentityEnricher;
import com.rbc.fogwall.user.UserEntry;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

/**
 * Factory that creates {@link ReceivePack} instances for store-and-forward push handling. Extracts credentials from the
 * HTTP request's Basic auth header and wires up the pre/post receive hooks.
 *
 * <p>This factory creates new hook instances per request since each push has its own credentials.
 */
@Slf4j
public class StoreAndForwardReceivePackFactory implements ReceivePackFactory<HttpServletRequest> {

    private static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofSeconds(10);
    private static final Duration DEFAULT_APPROVAL_TIMEOUT = Duration.ofMinutes(30);

    private final FogwallProvider provider;
    private final Supplier<CommitConfig> commitConfigSupplier;
    private final Supplier<DiffScanConfig> diffScanConfigSupplier;
    private final Supplier<SecretScanConfig> secretScanConfigSupplier;
    private final Supplier<BinaryBlobConfig> binaryBlobConfigSupplier;
    private final ContentPatternConfig contentPatternConfig;
    private final GpgConfig gpgConfig;
    private final RepoPermissionService repoPermissionService;
    private final PushIdentityResolver pushIdentityResolver;
    private SshScmIdentityEnricher sshScmIdentityEnricher;
    private final PushStore pushStore;
    private final ApprovalGateway approvalGateway;
    private final String serviceUrl;
    private final Duration heartbeatInterval;
    private final UrlRuleRegistry urlRuleRegistry;
    private LocalRepositoryCache cache;

    /** Stop the validation hook chain after the first failure (see {@link ServerConfig#isFailFast()}). */
    private boolean failFast = false;

    /** Connect timeout in seconds passed to the JGit {@link org.eclipse.jgit.transport.Transport} (0 = no timeout). */
    private int connectTimeoutSeconds = 0;

    /** Maximum time a push waits for human review before being marked timed out (see {@link ServerConfig}). */
    private Duration approvalTimeout = DEFAULT_APPROVAL_TIMEOUT;

    /** Enable fail-fast mode. Call after construction before the factory handles any requests. */
    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    /** Set the upstream connect timeout. Call after construction before the factory handles any requests. */
    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    /** Set the local repository cache for invalidation on forward failure. */
    public void setCache(LocalRepositoryCache cache) {
        this.cache = cache;
    }

    public void setSshScmIdentityEnricher(SshScmIdentityEnricher enricher) {
        this.sshScmIdentityEnricher = enricher;
    }

    /** Set the approval-wait timeout. Call after construction before the factory handles any requests. */
    public void setApprovalTimeout(Duration approvalTimeout) {
        this.approvalTimeout = approvalTimeout != null ? approvalTimeout : DEFAULT_APPROVAL_TIMEOUT;
    }

    /** Fixed-config constructors for use in tests and simple setups (no URL rule enforcement). */
    public StoreAndForwardReceivePackFactory(
            FogwallProvider provider, CommitConfig commitConfig, PushStore pushStore, ApprovalGateway approvalGateway) {
        this(
                provider,
                () -> commitConfig,
                DiffScanConfig::defaultConfig,
                SecretScanConfig::defaultConfig,
                BinaryBlobConfig::defaultConfig,
                ContentPatternConfig.defaultConfig(),
                GpgConfig.defaultConfig(),
                null,
                null,
                pushStore,
                approvalGateway,
                null,
                DEFAULT_HEARTBEAT_INTERVAL,
                null);
    }

    public StoreAndForwardReceivePackFactory(
            FogwallProvider provider,
            CommitConfig commitConfig,
            GpgConfig gpgConfig,
            RepoPermissionService repoPermissionService,
            PushIdentityResolver pushIdentityResolver,
            PushStore pushStore,
            ApprovalGateway approvalGateway,
            String serviceUrl,
            Duration heartbeatInterval) {
        this(
                provider,
                () -> commitConfig,
                DiffScanConfig::defaultConfig,
                SecretScanConfig::defaultConfig,
                BinaryBlobConfig::defaultConfig,
                ContentPatternConfig.defaultConfig(),
                gpgConfig,
                repoPermissionService,
                pushIdentityResolver,
                pushStore,
                approvalGateway,
                serviceUrl,
                heartbeatInterval,
                null);
    }

    public StoreAndForwardReceivePackFactory(
            FogwallProvider provider,
            Supplier<CommitConfig> commitConfigSupplier,
            Supplier<DiffScanConfig> diffScanConfigSupplier,
            Supplier<SecretScanConfig> secretScanConfigSupplier,
            Supplier<BinaryBlobConfig> binaryBlobConfigSupplier,
            ContentPatternConfig contentPatternConfig,
            GpgConfig gpgConfig,
            RepoPermissionService repoPermissionService,
            PushIdentityResolver pushIdentityResolver,
            PushStore pushStore,
            ApprovalGateway approvalGateway,
            String serviceUrl,
            Duration heartbeatInterval,
            UrlRuleRegistry urlRuleRegistry) {
        this.provider = provider;
        this.commitConfigSupplier = commitConfigSupplier;
        this.diffScanConfigSupplier =
                diffScanConfigSupplier != null ? diffScanConfigSupplier : DiffScanConfig::defaultConfig;
        this.secretScanConfigSupplier =
                secretScanConfigSupplier != null ? secretScanConfigSupplier : SecretScanConfig::defaultConfig;
        this.binaryBlobConfigSupplier =
                binaryBlobConfigSupplier != null ? binaryBlobConfigSupplier : BinaryBlobConfig::defaultConfig;
        this.contentPatternConfig =
                contentPatternConfig != null ? contentPatternConfig : ContentPatternConfig.defaultConfig();
        this.gpgConfig = gpgConfig != null ? gpgConfig : GpgConfig.defaultConfig();
        this.repoPermissionService = repoPermissionService;
        this.pushIdentityResolver = pushIdentityResolver;
        this.pushStore = pushStore;
        this.approvalGateway = approvalGateway;
        this.serviceUrl = serviceUrl;
        this.heartbeatInterval = heartbeatInterval != null ? heartbeatInterval : DEFAULT_HEARTBEAT_INTERVAL;
        this.urlRuleRegistry = urlRuleRegistry;
    }

    @Override
    public ReceivePack create(HttpServletRequest req, Repository db)
            throws ServiceNotEnabledException, ServiceNotAuthorizedException {

        CredentialsProvider creds =
                (CredentialsProvider) req.getAttribute(StoreAndForwardRepositoryResolver.CREDENTIALS_ATTRIBUTE);
        if (creds == null) {
            creds = extractBasicAuth(req);
        }

        String[] userPass = extractUserPass(req);
        String pushUser = userPass != null ? userPass[0] : null;
        String pushToken = userPass != null ? userPass[1] : null;

        String repoSlug = null;
        String pathInfo = req.getPathInfo();
        if (pathInfo != null) {
            String slug = pathInfo.replaceAll("\\.git$", "");
            String[] segments = slug.split("/", 4);
            if (segments.length >= 3) {
                slug = "/" + segments[1] + "/" + segments[2];
            }
            repoSlug = slug;
        }

        return buildReceivePack(db, creds, pushUser, pushToken, repoSlug, PushTransport.http());
    }

    /**
     * Builds a {@link ReceivePack} for an SSH push. The {@code transport} carries the {@link UserEntry} identified
     * during public-key authentication and the per-push SSH session factory for upstream forwarding.
     */
    public ReceivePack createForSsh(Repository db, String pushUser, String repoSlug, PushTransport.Ssh transport)
            throws ServiceNotEnabledException, ServiceNotAuthorizedException {
        return buildReceivePack(db, null, pushUser, null, repoSlug, transport);
    }

    private ReceivePack buildReceivePack(
            Repository db,
            CredentialsProvider creds,
            String pushUser,
            String pushToken,
            String repoSlug,
            PushTransport transport)
            throws ServiceNotEnabledException, ServiceNotAuthorizedException {

        ReceivePack rp = new ReceivePack(db);
        rp.setBiDirectionalPipe(false);

        // Per-request shared contexts
        var validationContext = new ValidationContext();
        var pushContext = new PushContext();

        pushContext.setPushUser(pushUser);
        pushContext.setPushToken(pushToken);
        pushContext.setRepoSlug(repoSlug);
        pushContext.setTransport(transport);

        // Persistence hook (records push to database)
        var persistenceHook = pushStore != null ? new PushStorePersistenceHook(pushStore, provider) : null;
        if (persistenceHook != null) {
            persistenceHook.setPushContext(pushContext);
            persistenceHook.setServiceUrl(serviceUrl);
            persistenceHook.setAutoApproval(approvalGateway != null && approvalGateway.approvesImmediately());
        }

        // Orderable validation hooks - sorted by getOrder() before chaining.
        // Lifecycle hooks (persistence, approval) are pinned outside this list.
        //
        // Authorization range (0-199):
        //   RepositoryUrlRuleHook           (100) - URL rule PASS (resolver already validated)
        //   CheckUserPushPermissionHook     (150) - push user authorization
        // Content filtering range (200-399):
        //   CheckEmptyBranchHook            (210) - reject if no commits introduced (short-circuit)
        //   CheckHiddenCommitsHook          (220) - reject if pack contains commits outside push range
        //   AuthorEmailValidationHook       (250) - validates emails
        //   CommitMessageValidationHook     (260) - validates messages
        //   ContentPatternCommitMessageHook (265) - WARN-only PII/identifier scan of commit messages
        //   ProxyPreReceiveHook             (270) - commit inspection
        //   DiffGenerationHook              (280) - generates diffs for scanning and persistence
        //   DiffScanningHook                (300) - scans diff added-lines for blocked content
        //   GpgSignatureHook                (320) - checks GPG signatures
        //   SecretScanningHook              (340) - pipes diff to gitleaks
        //   ContentPatternDiffHook          (345) - WARN-only PII/identifier scan of the diff
        //
        // Pinned lifecycle hooks (not orderable):
        //   [pre]  PushStorePersistenceHook.preReceive      - record RECEIVED
        //   [post-validation] PushStorePersistenceHook.validationResult - save APPROVED/PENDING
        //   [post-validation] ApprovalPreReceiveHook        - blocks until approved or timeout
        //
        // Post-receive:
        //   ForwardingPostReceiveHook       - forwards to upstream
        //   PushStorePersistenceHook.postReceive - save FORWARDED/ERROR

        var permissionHook = new CheckUserPushPermissionHook(
                pushIdentityResolver,
                repoPermissionService,
                validationContext,
                pushContext,
                provider,
                serviceUrl,
                sshScmIdentityEnricher);

        // Snapshot current config for this push — all hooks in one push see the same config even if a reload fires
        // mid-push.
        CommitConfig commitConfig = commitConfigSupplier.get();
        DiffScanConfig diffScanConfig = diffScanConfigSupplier.get();
        SecretScanConfig secretScanConfig = secretScanConfigSupplier.get();
        BinaryBlobConfig binaryBlobConfig = binaryBlobConfigSupplier.get();

        var identityVerificationHook = new IdentityVerificationHook(
                pushIdentityResolver, commitConfig.getIdentityVerification(), validationContext, pushContext, provider);

        // Build and sort the orderable validation hook list
        List<FogwallHook> validationHooks = new ArrayList<>(List.of(
                new RepositoryUrlRuleHook(urlRuleRegistry, provider, validationContext, pushContext),
                permissionHook,
                identityVerificationHook,
                new CheckEmptyBranchHook(pushContext),
                new CheckHiddenCommitsHook(pushContext),
                new AuthorEmailValidationHook(commitConfig, validationContext, pushContext),
                new CommitMessageValidationHook(commitConfig, validationContext, pushContext),
                new ContentPatternCommitMessageHook(contentPatternConfig, pushContext),
                new ProxyPreReceiveHook(pushContext),
                new DiffGenerationHook(pushContext),
                new BinaryBlobDetectionHook(binaryBlobConfig, validationContext, pushContext),
                new DiffScanningHook(diffScanConfig, validationContext, pushContext),
                new GpgSignatureHook(gpgConfig, validationContext, pushContext),
                new SecretScanningHook(secretScanConfig, validationContext, pushContext),
                new ContentPatternDiffHook(contentPatternConfig, pushContext)));
        if (provider instanceof BitbucketProvider bitbucketProvider) {
            validationHooks.add(new BitbucketCredentialRewriteHook(bitbucketProvider, pushContext));
        }
        if (pushStore != null) {
            validationHooks.add(new PriorPushEnrichmentHook(pushStore, pushContext));
        }
        validationHooks.sort(Comparator.comparingInt(FogwallHook::getOrder));

        PreReceiveHook[] preHooks;
        if (persistenceHook != null) {
            List<PreReceiveHook> hooks = new ArrayList<>();
            hooks.add(persistenceHook.preReceiveHook());
            hooks.addAll(validationHooks);
            hooks.add(persistenceHook.validationResultHook(validationContext));
            hooks.add(new ApprovalPreReceiveHook(
                    pushStore, approvalGateway, approvalTimeout, serviceUrl, repoPermissionService, pushContext));
            preHooks = hooks.toArray(PreReceiveHook[]::new);
        } else {
            preHooks = validationHooks.toArray(PreReceiveHook[]::new);
        }

        Runnable disconnectCallback = null;
        if (persistenceHook != null) {
            final PushContext capturedContext = pushContext;
            disconnectCallback = () -> {
                String recordId = capturedContext.getValidationRecordId();
                if (recordId == null) recordId = capturedContext.getPushId();
                if (recordId != null) {
                    try {
                        pushStore.cancel(recordId, null);
                        log.info("Push {} marked CANCELED: client disconnected mid-push", recordId);
                    } catch (Exception e) {
                        log.warn("Failed to mark push {} as CANCELED after client disconnect", recordId, e);
                    }
                }
            };
        }

        rp.setPreReceiveHook(
                chainPreReceiveHooks(heartbeatInterval, validationContext, failFast, disconnectCallback, preHooks));

        // Post-receive: forward to upstream, then record final status
        var forwardingHook = new ForwardingPostReceiveHook(provider, creds, pushContext, connectTimeoutSeconds, cache);
        if (persistenceHook != null) {
            rp.setPostReceiveHook(chainPostReceiveHooks(forwardingHook, persistenceHook.postReceiveHook()));
        } else {
            rp.setPostReceiveHook(forwardingHook);
        }

        log.debug("Created ReceivePack for {} with {} auth", provider.getName(), creds != null ? "credentials" : "no");

        return rp;
    }

    private static PreReceiveHook chainPreReceiveHooks(
            Duration heartbeatInterval,
            ValidationContext validationContext,
            boolean failFast,
            Runnable disconnectCallback,
            PreReceiveHook... hooks) {
        return (ReceivePack rp, Collection<ReceiveCommand> commands) -> {
            try (HeartbeatSender heartbeat = new HeartbeatSender(rp, heartbeatInterval, disconnectCallback)) {
                heartbeat.start();
                boolean skipValidationHooks = false;
                for (PreReceiveHook hook : hooks) {
                    // Fail-fast: skip remaining fogwallHook (validation) hooks after first issue.
                    // Lifecycle hooks (persistence, approval) do not implement fogwallHook and always run.
                    if (skipValidationHooks && hook instanceof FogwallHook) {
                        continue;
                    }
                    // Pause heartbeat dots while the approval hook streams its own progress messages to
                    // the client; dots interleave with gateway messages without this guard.
                    boolean isApprovalHook = hook instanceof ApprovalPreReceiveHook;
                    if (isApprovalHook) {
                        heartbeat.pause();
                    }
                    try {
                        hook.onPreReceive(rp, commands);
                    } finally {
                        if (isApprovalHook) {
                            heartbeat.resume();
                        }
                    }
                    // Flush sideband after each hook so messages stream to the client in real time
                    // (JGit's sendMessage() doesn't flush - without this, all output batches up)
                    try {
                        rp.getMessageOutputStream().flush();
                    } catch (IOException e) {
                        log.warn("Failed to flush sideband stream", e);
                    }
                    // Stop chain if any command was rejected (e.g. by a lifecycle hook)
                    if (commands.stream().anyMatch(cmd -> cmd.getResult() != ReceiveCommand.Result.NOT_ATTEMPTED)) {
                        return;
                    }
                    // After a validation hook reports an issue, mark remaining validation hooks to skip
                    if (failFast && hook instanceof FogwallHook && validationContext.hasIssues()) {
                        skipValidationHooks = true;
                    }
                }
            }
        };
    }

    private static PostReceiveHook chainPostReceiveHooks(PostReceiveHook... hooks) {
        return (ReceivePack rp, Collection<ReceiveCommand> commands) -> {
            for (PostReceiveHook hook : hooks) {
                hook.onPostReceive(rp, commands);
            }
        };
    }

    private CredentialsProvider extractBasicAuth(HttpServletRequest req) {
        String[] userPass = extractUserPass(req);
        if (userPass == null) return null;
        return new UsernamePasswordCredentialsProvider(userPass[0], userPass[1]);
    }

    private String[] extractUserPass(HttpServletRequest req) {
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return null;
        }

        try {
            String base64 = authHeader.substring("Basic ".length()).trim();
            String decoded = new String(Base64.getDecoder().decode(base64));
            int colonIndex = decoded.indexOf(':');
            if (colonIndex < 0) {
                log.warn("Invalid Basic auth format (no colon separator)");
                return null;
            }
            return new String[] {decoded.substring(0, colonIndex), decoded.substring(colonIndex + 1)};
        } catch (IllegalArgumentException e) {
            log.warn("Invalid Base64 in Authorization header", e);
            return null;
        }
    }
}
