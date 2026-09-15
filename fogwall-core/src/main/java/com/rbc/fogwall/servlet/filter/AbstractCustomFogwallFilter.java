package com.rbc.fogwall.servlet.filter;

import com.rbc.fogwall.git.HttpOperation;
import com.rbc.fogwall.git.LifecycleStage;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.function.Predicate;
import lombok.Setter;

/**
 * Convenience base class for {@link CustomFogwallFilter}s, mirroring {@link AbstractFogwallFilter}: it holds the
 * filter's {@link LifecycleStage} and applicable {@link HttpOperation}s and exposes the fail-fast flag to the shared
 * {@link FogwallFilter#doFilter} template. A custom filter must run in a custom stage
 * ({@link LifecycleStage#CUSTOM_PRE} or {@link LifecycleStage#CUSTOM_POST}).
 */
public abstract class AbstractCustomFogwallFilter implements CustomFogwallFilter {
    protected final LifecycleStage stage;
    protected final Set<HttpOperation> applicableOperations;

    @Setter
    private boolean failFast = false;

    protected AbstractCustomFogwallFilter(LifecycleStage stage) {
        this(stage, ALL_OPERATIONS);
    }

    protected AbstractCustomFogwallFilter(LifecycleStage stage, Set<HttpOperation> applicableOperations) {
        if (stage != LifecycleStage.CUSTOM_PRE && stage != LifecycleStage.CUSTOM_POST) {
            throw new IllegalArgumentException("A custom filter must run in a custom stage, but was: " + stage);
        }
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
