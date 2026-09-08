package com.rbc.fogwall.jetty;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.rbc.fogwall.approval.ApprovalGateway;
import com.rbc.fogwall.config.BinaryBlobConfig;
import com.rbc.fogwall.config.CommitConfig;
import com.rbc.fogwall.config.ContentPatternConfig;
import com.rbc.fogwall.config.DiffScanConfig;
import com.rbc.fogwall.config.FogwallConfig;
import com.rbc.fogwall.config.JettyConfigurationBuilder;
import com.rbc.fogwall.config.ScmOAuthConfig;
import com.rbc.fogwall.config.SecretScanConfig;
import com.rbc.fogwall.db.FetchStore;
import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.UrlRuleRegistry;
import com.rbc.fogwall.git.AuthorEmailValidationHook;
import com.rbc.fogwall.git.BinaryBlobDetectionHook;
import com.rbc.fogwall.git.BitbucketCredentialRewriteHook;
import com.rbc.fogwall.git.CheckEmptyBranchHook;
import com.rbc.fogwall.git.CheckHiddenCommitsHook;
import com.rbc.fogwall.git.CheckUserPushPermissionHook;
import com.rbc.fogwall.git.CommitAttributionPolicyHook;
import com.rbc.fogwall.git.CommitMessageValidationHook;
import com.rbc.fogwall.git.ContentPatternCommitMessageHook;
import com.rbc.fogwall.git.ContentPatternDiffHook;
import com.rbc.fogwall.git.DiffGenerationHook;
import com.rbc.fogwall.git.DiffScanningHook;
import com.rbc.fogwall.git.FogwallHook;
import com.rbc.fogwall.git.GpgSignatureHook;
import com.rbc.fogwall.git.LocalRepositoryCache;
import com.rbc.fogwall.git.PriorPushEnrichmentHook;
import com.rbc.fogwall.git.ProxyPreReceiveHook;
import com.rbc.fogwall.git.PushContext;
import com.rbc.fogwall.git.RepositoryUrlRuleHook;
import com.rbc.fogwall.git.SecretScanningHook;
import com.rbc.fogwall.git.ServerReceivePackFactory;
import com.rbc.fogwall.git.TrailerPolicyValidationHook;
import com.rbc.fogwall.git.ValidationContext;
import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.provider.BitbucketProvider;
import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.provider.GitHubProvider;
import com.rbc.fogwall.service.PushIdentityResolver;
import com.rbc.fogwall.servlet.filter.AllowApprovedPushFilter;
import com.rbc.fogwall.servlet.filter.AuditLogFilter;
import com.rbc.fogwall.servlet.filter.BinaryBlobFilter;
import com.rbc.fogwall.servlet.filter.BitbucketIdentityFilter;
import com.rbc.fogwall.servlet.filter.CheckAuthorEmailsFilter;
import com.rbc.fogwall.servlet.filter.CheckCommitMessagesFilter;
import com.rbc.fogwall.servlet.filter.CheckEmptyBranchFilter;
import com.rbc.fogwall.servlet.filter.CheckHiddenCommitsFilter;
import com.rbc.fogwall.servlet.filter.CheckTrailersFilter;
import com.rbc.fogwall.servlet.filter.CheckUserPushPermissionFilter;
import com.rbc.fogwall.servlet.filter.CommitAttributionPolicyFilter;
import com.rbc.fogwall.servlet.filter.ContentPatternDiffFilter;
import com.rbc.fogwall.servlet.filter.ContentPatternMessageFilter;
import com.rbc.fogwall.servlet.filter.EnrichPushCommitsFilter;
import com.rbc.fogwall.servlet.filter.FetchFinalizerFilter;
import com.rbc.fogwall.servlet.filter.FogwallFilter;
import com.rbc.fogwall.servlet.filter.GpgSignatureFilter;
import com.rbc.fogwall.servlet.filter.ParseGitRequestFilter;
import com.rbc.fogwall.servlet.filter.PushFinalizerFilter;
import com.rbc.fogwall.servlet.filter.ScanDiffFilter;
import com.rbc.fogwall.servlet.filter.SecretScanningFilter;
import com.rbc.fogwall.servlet.filter.UrlRuleAggregateFilter;
import com.rbc.fogwall.servlet.filter.ValidationSummaryFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Guards against the transparent-proxy filter chain and the server-mode hook chain silently drifting apart: a
 * validation step added to one mode but not the other. Ordering is out of scope — only roster coverage is checked.
 *
 * <p>Hooks and filters share no naming convention ({@link FogwallHook#getName()} defaults to the class's simple name;
 * filters use short, deliberate step names), so there is no string to compare. {@link #HOOK_TO_FILTER} is this test's
 * own record of which hook class and filter class implement the same validation concern; {@link #SERVER_ONLY} and
 * {@link #PROXY_ONLY} declare, with a reason, the steps that legitimately exist on only one side. Both chains are built
 * once per provider actually used by their conditional branches ({@link GitHubProvider}, {@link BitbucketProvider}) so
 * provider-gated steps are exercised too.
 */
class ValidationStepParityTest {

    /** Each entry: a server-mode hook and the transparent-proxy filter that implement the same validation concern. */
    private static final Map<Class<? extends FogwallHook>, Class<? extends FogwallFilter>> HOOK_TO_FILTER =
            Map.ofEntries(
                    Map.entry(RepositoryUrlRuleHook.class, UrlRuleAggregateFilter.class),
                    Map.entry(CheckUserPushPermissionHook.class, CheckUserPushPermissionFilter.class),
                    Map.entry(CommitAttributionPolicyHook.class, CommitAttributionPolicyFilter.class),
                    Map.entry(CheckEmptyBranchHook.class, CheckEmptyBranchFilter.class),
                    Map.entry(CheckHiddenCommitsHook.class, CheckHiddenCommitsFilter.class),
                    Map.entry(AuthorEmailValidationHook.class, CheckAuthorEmailsFilter.class),
                    Map.entry(TrailerPolicyValidationHook.class, CheckTrailersFilter.class),
                    Map.entry(CommitMessageValidationHook.class, CheckCommitMessagesFilter.class),
                    Map.entry(ContentPatternCommitMessageHook.class, ContentPatternMessageFilter.class),
                    Map.entry(BinaryBlobDetectionHook.class, BinaryBlobFilter.class),
                    Map.entry(DiffScanningHook.class, ScanDiffFilter.class),
                    Map.entry(GpgSignatureHook.class, GpgSignatureFilter.class),
                    Map.entry(SecretScanningHook.class, SecretScanningFilter.class),
                    Map.entry(ContentPatternDiffHook.class, ContentPatternDiffFilter.class),
                    Map.entry(BitbucketCredentialRewriteHook.class, BitbucketIdentityFilter.class));

    /** Server-mode hooks with no proxy-filter counterpart, and why. */
    private static final Map<Class<? extends FogwallHook>, String> SERVER_ONLY = Map.of(
            ProxyPreReceiveHook.class,
                    "JGit commit inspection specific to the pre-receive transport; no proxy-mode equivalent object model",
            DiffGenerationHook.class,
                    "Generates the diff server mode's own hooks scan; the proxy computes diffs inline in its own"
                            + " filters instead of as a separate step",
            PriorPushEnrichmentHook.class,
                    "Backfills push-record history a store-and-forward push always has; the proxy has no equivalent"
                            + " persisted record to enrich at this stage");

    /** Proxy filters with no server-mode hook counterpart, and why. */
    private static final Map<Class<? extends FogwallFilter>, String> PROXY_ONLY = Map.ofEntries(
            Map.entry(
                    ParseGitRequestFilter.class,
                    "Parses the HTTP request into a git operation and repo path; server mode's equivalent parsing"
                            + " happens inside JGit's GitServlet/ReceivePack machinery before any hook runs"),
            Map.entry(
                    EnrichPushCommitsFilter.class,
                    "Buffers and enriches commit data for later filters since the proxy can't re-read the pack;"
                            + " server-mode hooks read the JGit Repository directly instead"),
            Map.entry(
                    AllowApprovedPushFilter.class,
                    "Lets a developer's second push through after dashboard approval without repeating validation —"
                            + " a re-push mechanism specific to the proxy's buffer-then-block model. Server mode"
                            + " blocks synchronously within one connection (ApprovalPreReceiveHook) and never needs a"
                            + " second push"),
            Map.entry(
                    ValidationSummaryFilter.class,
                    "Renders accumulated per-step results into the one buffered HTTP response the proxy can send;"
                            + " server mode streams messages live per hook instead of aggregating them"),
            Map.entry(
                    FetchFinalizerFilter.class,
                    "Finalizes proxied fetch (read) requests; server mode's fetch path is upload-pack, entirely"
                            + " separate from ReceivePack and this hook chain"),
            Map.entry(
                    PushFinalizerFilter.class,
                    "Sends the final buffered response and triggers forwarding for the proxy's single-response"
                            + " model; server mode's forwarding is a separate post-receive hook, not part of the"
                            + " pre-receive validation roster this test captures"),
            Map.entry(
                    AuditLogFilter.class,
                    "Request-level audit logging for the proxy; server mode's audit trail is"
                            + " PushStorePersistenceHook, a pinned lifecycle hook outside the orderable validation"
                            + " roster this test captures"));

    @Test
    void everyServerHookIsCoveredByAFilterOrADeclaredException() {
        for (FogwallHook hook : allServerHooks()) {
            Class<?> hookClass = hook.getClass();
            boolean covered = HOOK_TO_FILTER.containsKey(hookClass) || SERVER_ONLY.containsKey(hookClass);
            assertTrue(
                    covered,
                    hookClass.getSimpleName()
                            + " is in the server-mode hook chain but has no matching entry in HOOK_TO_FILTER or"
                            + " SERVER_ONLY in this test — add one, or declare why it's server-only");
        }
    }

    @Test
    void everyProxyFilterIsCoveredByAHookOrADeclaredException() {
        Set<Class<? extends FogwallFilter>> mappedFilterClasses = Set.copyOf(HOOK_TO_FILTER.values());
        for (FogwallFilter filter : allProxyFilters()) {
            Class<?> filterClass = filter.getClass();
            boolean covered = mappedFilterClasses.contains(filterClass) || PROXY_ONLY.containsKey(filterClass);
            assertTrue(
                    covered,
                    filterClass.getSimpleName()
                            + " is in the proxy filter chain but has no matching entry in HOOK_TO_FILTER or PROXY_ONLY"
                            + " in this test — add one, or declare why it's proxy-only");
        }
    }

    @Test
    void everyDeclaredHookAndFilterIsActuallyInTheRespectiveChain() {
        Set<Class<?>> hookClasses = classesOf(allServerHooks());
        Set<Class<?>> filterClasses = classesOf(allProxyFilters());

        for (Class<? extends FogwallHook> declaredHook : HOOK_TO_FILTER.keySet()) {
            assertTrue(
                    hookClasses.contains(declaredHook),
                    declaredHook.getSimpleName() + " is declared in this test but no longer built by "
                            + "ServerReceivePackFactory — remove or update this test");
        }
        for (Class<? extends FogwallFilter> declaredFilter : HOOK_TO_FILTER.values()) {
            assertTrue(
                    filterClasses.contains(declaredFilter),
                    declaredFilter.getSimpleName() + " is declared in this test but no longer built by "
                            + "FogwallServletRegistrar — remove or update this test");
        }
    }

    private static Set<Class<?>> classesOf(List<?> instances) {
        return instances.stream().map(Object::getClass).collect(Collectors.toSet());
    }

    /** Every hook class the factory can produce, across both a generic provider and the Bitbucket-gated branch. */
    private static List<FogwallHook> allServerHooks() {
        List<FogwallHook> all = new ArrayList<>(buildServerHooks(new GitHubProvider("/github")));
        all.addAll(buildServerHooks(new BitbucketProvider("/bitbucket")));
        return all;
    }

    /** Every filter class the registrar can produce, across both a generic provider and the Bitbucket-gated branch. */
    private static List<FogwallFilter> allProxyFilters() {
        List<FogwallFilter> all = new ArrayList<>(buildProxyFilters(new GitHubProvider("/github")));
        all.addAll(buildProxyFilters(new BitbucketProvider("/bitbucket")));
        return all;
    }

    private static List<FogwallHook> buildServerHooks(FogwallProvider provider) {
        var factory = new ServerReceivePackFactory(
                provider, CommitConfig.defaultConfig(), mock(PushStore.class), mock(ApprovalGateway.class));
        return factory.buildValidationHooks(
                CommitConfig.defaultConfig(),
                DiffScanConfig.defaultConfig(),
                SecretScanConfig.defaultConfig(),
                BinaryBlobConfig.defaultConfig(),
                new ValidationContext(),
                new PushContext());
    }

    private static List<FogwallFilter> buildProxyFilters(FogwallProvider provider) {
        var configBuilder = new JettyConfigurationBuilder(new FogwallConfig());
        return FogwallServletRegistrar.buildCoreFilters(
                provider,
                mock(LocalRepositoryCache.class),
                configBuilder,
                CommitConfig::defaultConfig,
                DiffScanConfig::defaultConfig,
                SecretScanConfig::defaultConfig,
                BinaryBlobConfig::defaultConfig,
                ContentPatternConfig.defaultConfig(),
                mock(PushStore.class),
                "https://fogwall.example.com",
                mock(ApprovalGateway.class),
                mock(PushIdentityResolver.class),
                mock(RepoPermissionService.class),
                mock(FetchStore.class),
                mock(UrlRuleRegistry.class),
                ScmOAuthConfig.defaultConfig());
    }
}
