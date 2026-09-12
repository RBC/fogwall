package com.rbc.fogwall.observability;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rbc.fogwall.db.model.ScmApiActionStatus;
import com.rbc.fogwall.servlet.ScmApiRequestContext;
import io.opentelemetry.api.OpenTelemetry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class ScmApiObservabilityFilterTest {

    private FogwallTelemetry enabledMock() {
        FogwallTelemetry telemetry = mock(FogwallTelemetry.class);
        when(telemetry.isEnabled()).thenReturn(true);
        // A real no-op OpenTelemetry gives a working propagator (empty extract) and a no-op span.
        when(telemetry.openTelemetry()).thenReturn(OpenTelemetry.noop());
        when(telemetry.tracer()).thenReturn(OpenTelemetry.noop().getTracer(FogwallTelemetry.SCOPE));
        return telemetry;
    }

    private HttpServletRequest requestWith(ScmApiRequestContext context) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeaderNames()).thenReturn(Collections.emptyEnumeration());
        when(req.getAttribute(ScmApiRequestContext.SCM_API_REQUEST_ATTR)).thenReturn(context);
        return req;
    }

    @Test
    void disabled_passesThrough_withNoRecording() throws Exception {
        FogwallTelemetry telemetry = mock(FogwallTelemetry.class);
        when(telemetry.isEnabled()).thenReturn(false);
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        new ScmApiObservabilityFilter(telemetry, "github").doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        verify(telemetry, never()).recordScmApiDuration(anyDouble(), anyString(), anyString());
    }

    @Test
    void mutation_recordsDurationByProviderAndOperation() throws Exception {
        FogwallTelemetry telemetry = enabledMock();
        ScmApiRequestContext context = new ScmApiRequestContext();
        context.setMutationField("createIssue");
        context.setStatus(ScmApiActionStatus.FORWARDED);
        HttpServletRequest req = requestWith(context);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getStatus()).thenReturn(200);
        FilterChain chain = mock(FilterChain.class);

        new ScmApiObservabilityFilter(telemetry, "github").doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        verify(telemetry).recordScmApiDuration(anyDouble(), eq("github"), eq("issue.create"));
    }

    @Test
    void read_recordsDurationByClassifiedResource() throws Exception {
        FogwallTelemetry telemetry = enabledMock();
        // A read has no mutation field; the gate filter classifies its resource onto the context instead.
        ScmApiRequestContext context = new ScmApiRequestContext();
        context.setReadResource("issue.read");
        HttpServletRequest req = requestWith(context);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getStatus()).thenReturn(200);
        FilterChain chain = mock(FilterChain.class);

        new ScmApiObservabilityFilter(telemetry, "gitlab").doFilter(req, resp, chain);

        verify(telemetry).recordScmApiDuration(anyDouble(), eq("gitlab"), eq("issue.read"));
    }

    @Test
    void stillFinalizesWhenChainThrows() throws Exception {
        FogwallTelemetry telemetry = enabledMock();
        ScmApiRequestContext context = new ScmApiRequestContext();
        context.setMutationField("mergePullRequest");
        HttpServletRequest req = requestWith(context);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getStatus()).thenReturn(500);
        FilterChain chain = mock(FilterChain.class);
        doThrow(new IOException("boom")).when(chain).doFilter(req, resp);

        ScmApiObservabilityFilter filter = new ScmApiObservabilityFilter(telemetry, "github");
        try {
            filter.doFilter(req, resp, chain);
        } catch (IOException expected) {
            // rethrown after the span is closed
        }

        // Duration is recorded even when the chain fails.
        verify(telemetry).recordScmApiDuration(anyDouble(), eq("github"), eq("proposal.merge"));
    }
}
