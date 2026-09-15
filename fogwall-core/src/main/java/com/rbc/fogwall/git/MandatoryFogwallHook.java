package com.rbc.fogwall.git;

/**
 * A built-in server-mode hook that runs in a mandatory lifecycle stage — the static core of fogwall, closed to external
 * extension. This type is {@code sealed} to {@code fogwall-core}: external code cannot implement a mandatory-stage
 * hook, so the only extension surface is {@link CustomFogwallHook}.
 */
public sealed interface MandatoryFogwallHook extends FogwallHook
        permits AuthorEmailValidationHook,
                BinaryBlobDetectionHook,
                BitbucketCredentialRewriteHook,
                CheckEmptyBranchHook,
                CheckHiddenCommitsHook,
                CheckUserPushPermissionHook,
                CommitAttributionPolicyHook,
                CommitMessageValidationHook,
                ContentPatternCommitMessageHook,
                ContentPatternDiffHook,
                DiffGenerationHook,
                DiffScanningHook,
                GpgSignatureHook,
                PriorPushEnrichmentHook,
                ProxyPreReceiveHook,
                RepositoryUrlRuleHook,
                SecretScanningHook,
                TrailerPolicyValidationHook {}
