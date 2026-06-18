package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;

import com.rbc.fogwall.git.GitRequestDetails;
import com.rbc.fogwall.git.HttpOperation;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Set;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public abstract class AbstractFogwallFilter implements FogwallFilter {
    protected final int order;
    protected final Set<HttpOperation> applicableOperations;

    /**
     * When {@code true}, skip this filter if a prior filter has already set the push result to
     * {@link GitRequestDetails.GitResult#REJECTED}. Only applies to non-system, non-terminal filters (order 0 through
     * {@code Integer.MAX_VALUE - 100}).
     */
    @Setter
    private boolean failFast = false;

    /**
     * Apply this fogwallFilter against all Git operations.
     *
     * @param order Order the filter is applied
     */
    public AbstractFogwallFilter(int order) {
        this.applicableOperations = ALL_OPERATIONS;
        this.order = order;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (failFast && order >= 0 && order < Integer.MAX_VALUE - 100) {
            var details = (GitRequestDetails) ((HttpServletRequest) request).getAttribute(GIT_REQUEST_ATTR);
            if (details != null && details.getResult() == GitRequestDetails.GitResult.REJECTED) {
                chain.doFilter(request, response);
                return;
            }
        }
        FogwallFilter.super.doFilter(request, response, chain);
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    @Override
    public Predicate<HttpServletRequest> shouldFilter() {
        return (HttpServletRequest request) -> applicableOperations.contains(determineOperation(request));
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" + "order="
                + order + ", appliedOperations="
                + applicableOperations + '}';
    }
}
