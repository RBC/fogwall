package com.rbc.fogwall.jetty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.rbc.fogwall.approval.ApprovalGateway;
import com.rbc.fogwall.approval.SelfApprovalPolicy;
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
import com.rbc.fogwall.git.CustomFogwallHook;
import com.rbc.fogwall.git.FogwallHook;
import com.rbc.fogwall.git.LifecycleStage;
import com.rbc.fogwall.git.LocalRepositoryCache;
import com.rbc.fogwall.git.MandatoryFogwallHook;
import com.rbc.fogwall.git.ProxyMode;
import com.rbc.fogwall.git.PushContext;
import com.rbc.fogwall.git.PushStepKind;
import com.rbc.fogwall.git.ServerReceivePackFactory;
import com.rbc.fogwall.git.ValidationContext;
import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.provider.BitbucketProvider;
import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.provider.GitHubProvider;
import com.rbc.fogwall.service.PushIdentityResolver;
import com.rbc.fogwall.servlet.filter.CustomFogwallFilter;
import com.rbc.fogwall.servlet.filter.FogwallFilter;
import com.rbc.fogwall.servlet.filter.MandatoryFogwallFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Guards against the transparent-proxy filter chain and the server-mode hook chain silently drifting apart: a step
 * added to one mode but not the other. Ordering is out of scope — only roster coverage is checked.
 *
 * <p>Each hook and filter declares a {@link PushStepKind}, and each kind declares the {@link ProxyMode}s it runs in, so
 * parity is a set comparison: the kinds a built chain actually contains must equal the kinds that name that mode. A
 * single-mode step is declared once, on the kind, rather than in a per-class exception map here. Both chains are built
 * once per provider used by their conditional branches ({@link GitHubProvider}, {@link BitbucketProvider}) so
 * provider-gated steps are exercised too.
 */
class ValidationStepParityTest {

    @Test
    void everyServerHookDeclaresAStepKind() {
        for (FogwallHook hook : allServerHooks()) {
            assertTrue(
                    hook.stepKind().isPresent(),
                    hook.getClass().getSimpleName()
                            + " is built into the server-mode hook chain but declares no PushStepKind — add a"
                            + " stepKind() override (or, if it is genuinely not an audited step, exclude it from the"
                            + " validation roster)");
        }
    }

    @Test
    void everyProxyFilterDeclaresAStepKind() {
        for (FogwallFilter filter : allProxyFilters()) {
            assertTrue(
                    filter.stepKind().isPresent(),
                    filter.getClass().getSimpleName()
                            + " is built into the transparent-proxy filter chain but declares no PushStepKind — add a"
                            + " stepKind() override (or, if it is genuinely not an audited step, exclude it from the"
                            + " core filter chain)");
        }
    }

    @Test
    void serverHookKindsMatchKindsDeclaredForServerMode() {
        Set<PushStepKind> declared = kindsForMode(ProxyMode.SERVER);
        Set<PushStepKind> actual = allServerHooks().stream()
                .map(FogwallHook::stepKind)
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());
        assertEquals(
                declared,
                actual,
                "server-mode hook chain does not match the kinds declaring ProxyMode.SERVER — a step was added or"
                        + " removed on only one side, or a kind's modes() is wrong");
    }

    @Test
    void proxyFilterKindsMatchKindsDeclaredForTransparentMode() {
        Set<PushStepKind> declared = kindsForMode(ProxyMode.TRANSPARENT);
        Set<PushStepKind> actual = allProxyFilters().stream()
                .map(FogwallFilter::stepKind)
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());
        assertEquals(
                declared,
                actual,
                "transparent-proxy filter chain does not match the kinds declaring ProxyMode.TRANSPARENT — a step was"
                        + " added or removed on only one side, or a kind's modes() is wrong");
    }

    // --- Lifecycle stage model (#456) + declared termination (#602) ---

    private static final Set<LifecycleStage> MANDATORY_STAGES =
            Set.of(LifecycleStage.MANDATORY_PRE, LifecycleStage.MANDATORY_PROCESSING, LifecycleStage.MANDATORY_POST);

    @Test
    void everyBuiltInFilterRunsInAMandatoryStage() {
        for (FogwallFilter filter : allProxyFilters()) {
            assertTrue(
                    MANDATORY_STAGES.contains(filter.stage()),
                    filter.getClass().getSimpleName()
                            + " is a built-in and must run in a mandatory stage, but declared " + filter.stage());
        }
    }

    @Test
    void everyBuiltInHookRunsInAMandatoryStage() {
        for (FogwallHook hook : allServerHooks()) {
            assertTrue(
                    MANDATORY_STAGES.contains(hook.stage()),
                    hook.getClass().getSimpleName() + " is a built-in and must run in a mandatory stage, but declared "
                            + hook.stage());
        }
    }

    @Test
    void proxyFilterChainIsGroupedByStageInPrecedenceOrder() {
        List<FogwallFilter> filters = buildProxyFilters(new GitHubProvider("/github"));
        int previous = Integer.MIN_VALUE;
        for (FogwallFilter filter : filters) {
            int ordinal = filter.stage().ordinal();
            assertTrue(
                    ordinal >= previous,
                    "filter chain is not grouped by stage: " + filter.getClass().getSimpleName() + " (" + filter.stage()
                            + ") follows a later stage");
            previous = ordinal;
        }
    }

    @Test
    void terminatingProxyFiltersAreExactlyTheAccessControlAndStructuralGuards() {
        Set<String> terminating = allProxyFilters().stream()
                .filter(FogwallFilter::terminatesChainOnFailure)
                .map(f -> f.getClass().getSimpleName())
                .collect(Collectors.toSet());
        assertEquals(
                Set.of(
                        "UrlRuleAggregateFilter",
                        "CheckUserPushPermissionFilter",
                        "CheckEmptyBranchFilter",
                        "CheckHiddenCommitsFilter"),
                terminating,
                "the set of chain-terminating proxy filters changed — confirm this is intended and preserves behavior");
    }

    @Test
    void terminatingServerHooksAreExactlyTheStructuralGuards() {
        Set<String> terminating = allServerHooks().stream()
                .filter(FogwallHook::terminatesChainOnFailure)
                .map(h -> h.getClass().getSimpleName())
                .collect(Collectors.toSet());
        assertEquals(
                Set.of("CheckEmptyBranchHook", "CheckHiddenCommitsHook"),
                terminating,
                "the set of chain-terminating server hooks changed — URL-rule and permission hooks are deliberately"
                        + " non-terminating in server mode, unlike their proxy twins");
    }

    @Test
    void mandatoryStageTypesAreSealedToTheCoreAndCustomTypesAreOpen() {
        // Implementing a mandatory-stage type outside fogwall-core is a compile-time error; sealing is the mechanism.
        assertTrue(MandatoryFogwallFilter.class.isSealed(), "MandatoryFogwallFilter must be sealed");
        assertTrue(MandatoryFogwallHook.class.isSealed(), "MandatoryFogwallHook must be sealed");
        assertFalse(CustomFogwallFilter.class.isSealed(), "CustomFogwallFilter is the open extension surface");
        assertFalse(CustomFogwallHook.class.isSealed(), "CustomFogwallHook is the open extension surface");
    }

    private static Set<PushStepKind> kindsForMode(ProxyMode mode) {
        return Arrays.stream(PushStepKind.values())
                .filter(kind -> kind.runsIn(mode))
                .collect(Collectors.toSet());
    }

    /** Every hook the factory can produce, across both a generic provider and the Bitbucket-gated branch. */
    private static List<FogwallHook> allServerHooks() {
        List<FogwallHook> all = new ArrayList<>(buildServerHooks(new GitHubProvider("/github")));
        all.addAll(buildServerHooks(new BitbucketProvider("/bitbucket")));
        return all;
    }

    /** Every filter the registrar can produce, across both a generic provider and the Bitbucket-gated branch. */
    private static List<FogwallFilter> allProxyFilters() {
        List<FogwallFilter> all = new ArrayList<>(buildProxyFilters(new GitHubProvider("/github")));
        all.addAll(buildProxyFilters(new BitbucketProvider("/bitbucket")));
        return all;
    }

    private static List<FogwallHook> buildServerHooks(FogwallProvider provider) {
        var factory = new ServerReceivePackFactory(
                provider,
                CommitConfig.defaultConfig(),
                mock(PushStore.class),
                mock(ApprovalGateway.class),
                mock(SelfApprovalPolicy.class));
        return factory.buildValidationHooks(
                CommitConfig.defaultConfig(),
                DiffScanConfig.defaultConfig(),
                SecretScanConfig.defaultConfig(),
                BinaryBlobConfig.defaultConfig(),
                ContentPatternConfig.defaultConfig(),
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
                ContentPatternConfig::defaultConfig,
                mock(PushStore.class),
                "https://fogwall.example.com",
                mock(ApprovalGateway.class),
                mock(PushIdentityResolver.class),
                mock(RepoPermissionService.class),
                mock(SelfApprovalPolicy.class),
                mock(FetchStore.class),
                mock(UrlRuleRegistry.class),
                ScmOAuthConfig.defaultConfig());
    }
}
