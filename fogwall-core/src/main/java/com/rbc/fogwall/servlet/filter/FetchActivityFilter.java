package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;

import com.rbc.fogwall.db.FetchActivityRecorder;
import com.rbc.fogwall.db.model.FetchActivity;
import com.rbc.fogwall.git.GitRequestDetails;
import com.rbc.fogwall.git.HttpOperation;
import com.rbc.fogwall.git.ProxyMode;
import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.servlet.FetchDecision;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;

/**
 * Counts the {@link FetchDecision} made on an HTTP clone or fetch request. Wraps the rest of the chain and counts in
 * its {@code finally}, so a decision is counted however the request ended, and only after every check that could refuse
 * it has run. Counting is in memory; see {@link FetchActivityRecorder}.
 *
 * <p>An allowed discovery request ({@code /info/refs}) is not counted: the pack request that follows it is. A refused
 * one is, since git stops there.
 */
@RequiredArgsConstructor
public class FetchActivityFilter implements Filter {

    private final FogwallProvider provider;
    private final ProxyMode mode;
    private final FetchActivityRecorder recorder;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        } finally {
            count(request);
        }
    }

    private void count(ServletRequest request) {
        var decision = FetchDecision.of(request);
        if (decision.isEmpty()
                || !(request.getAttribute(GIT_REQUEST_ATTR) instanceof GitRequestDetails details)
                || details.getRepoRef() == null) {
            return;
        }
        if (details.getOperation() == HttpOperation.INFO && decision.get().result() == FetchActivity.Result.ALLOWED) {
            return;
        }
        recorder.record(
                provider.getProviderId(),
                details.getRepoRef().getOwner(),
                details.getRepoRef().getName(),
                FetchActivity.Transport.HTTP,
                mode,
                decision.get().result(),
                decision.get().refusal(),
                decision.get().ruleId());
    }
}
