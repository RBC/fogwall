package com.rbc.fogwall.observability;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rbc.fogwall.servlet.FogwallServlet;
import io.opentelemetry.api.OpenTelemetry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class ObservabilityFilterTest {

    private FogwallTelemetry enabledMock() {
        FogwallTelemetry telemetry = mock(FogwallTelemetry.class);
        when(telemetry.isEnabled()).thenReturn(true);
        // A real no-op OpenTelemetry gives a working propagator (empty extract) and a no-op span.
        when(telemetry.openTelemetry()).thenReturn(OpenTelemetry.noop());
        when(telemetry.tracer()).thenReturn(OpenTelemetry.noop().getTracer(FogwallTelemetry.SCOPE));
        return telemetry;
    }

    private HttpServletRequest syncRequest() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeaderNames()).thenReturn(Collections.emptyEnumeration());
        when(req.getAttribute(anyString())).thenReturn(null);
        when(req.isAsyncStarted()).thenReturn(false);
        return req;
    }

    @Test
    void disabled_passesThrough_withNoRecording() throws Exception {
        FogwallTelemetry telemetry = mock(FogwallTelemetry.class);
        when(telemetry.isEnabled()).thenReturn(false);
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        new ObservabilityFilter(telemetry, "server").doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        verify(telemetry, never()).recordActive(anyLong(), anyString());
    }

    @Test
    void syncRequest_incrementsAndDecrementsActive_andRecordsDuration() throws Exception {
        FogwallTelemetry telemetry = enabledMock();
        HttpServletRequest req = syncRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getStatus()).thenReturn(200);
        FilterChain chain = mock(FilterChain.class);

        new ObservabilityFilter(telemetry, "server").doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        verify(telemetry).recordActive(eq(1L), eq("server"));
        verify(telemetry).recordActive(eq(-1L), eq("server"));
        // Provider is unknown because no GitRequestDetails attribute was set.
        verify(telemetry).recordDuration(anyDouble(), eq("server"), eq("unknown"));
        // The span context is published for the async proxy-forward callbacks to re-activate.
        verify(req).setAttribute(eq(FogwallServlet.OTEL_CONTEXT_ATTR), any());
    }

    @Test
    void syncRequest_stillFinalizesWhenChainThrows() throws Exception {
        FogwallTelemetry telemetry = enabledMock();
        HttpServletRequest req = syncRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getStatus()).thenReturn(500);
        FilterChain chain = mock(FilterChain.class);
        doThrow(new IOException("boom")).when(chain).doFilter(req, resp);

        ObservabilityFilter filter = new ObservabilityFilter(telemetry, "proxy");
        try {
            filter.doFilter(req, resp, chain);
        } catch (IOException expected) {
            // rethrown after the span is closed
        }

        // Active gauge is balanced and duration is recorded even on failure.
        verify(telemetry).recordActive(eq(1L), eq("proxy"));
        verify(telemetry).recordActive(eq(-1L), eq("proxy"));
        verify(telemetry).recordDuration(anyDouble(), eq("proxy"), eq("unknown"));
    }
}
