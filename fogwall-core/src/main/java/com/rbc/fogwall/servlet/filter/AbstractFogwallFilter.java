package com.rbc.fogwall.servlet.filter;

import com.rbc.fogwall.git.HttpOperation;
import com.rbc.fogwall.git.LifecycleStage;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.function.Predicate;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Base class for built-in filters. Holds the filter's {@link LifecycleStage} and the {@link HttpOperation}s it applies
 * to. The fail-fast and pre-approval short-circuits live in the shared {@link FogwallFilter#doFilter} template; this
 * class only exposes the configured fail-fast flag to it.
 */
@Slf4j
public abstract sealed class AbstractFogwallFilter implements MandatoryFogwallFilter
        permits ProviderAwareFogwallFilter,
                AllowApprovedPushFilter,
                AuditLogFilter,
                BinaryBlobFilter,
                CheckAuthorEmailsFilter,
                CheckCommitMessagesFilter,
                CheckEmptyBranchFilter,
                CheckHiddenCommitsFilter,
                CheckTrailersFilter,
                CheckUserPushPermissionFilter,
                CommitAttributionPolicyFilter,
                ContentPatternDiffFilter,
                ContentPatternMessageFilter,
                FetchFinalizerFilter,
                GpgSignatureFilter,
                PushFinalizerFilter,
                ScanDiffFilter,
                SecretScanningFilter,
                ValidationSummaryFilter {
    protected final LifecycleStage stage;
    protected final Set<HttpOperation> applicableOperations;

    /**
     * When {@code true}, skip this filter if a prior filter has already recorded a rejection. Only takes effect for
     * {@link LifecycleStage#MANDATORY_PROCESSING} filters; pre/post-stage filters always run (see
     * {@link FogwallFilter#doFilter}).
     */
    @Setter
    private boolean failFast = false;

    /** Applies this filter to all git operations. */
    protected AbstractFogwallFilter(LifecycleStage stage) {
        this.stage = stage;
        this.applicableOperations = ALL_OPERATIONS;
    }

    protected AbstractFogwallFilter(LifecycleStage stage, Set<HttpOperation> applicableOperations) {
        this.stage = stage;
        this.applicableOperations = applicableOperations;
    }

    @Override
    public LifecycleStage stage() {
        return this.stage;
    }

    @Override
    public boolean failFast() {
        return this.failFast;
    }

    @Override
    public Predicate<HttpServletRequest> shouldFilter() {
        return (HttpServletRequest request) -> applicableOperations.contains(determineOperation(request));
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" + "stage="
                + stage + ", appliedOperations="
                + applicableOperations + '}';
    }
}
