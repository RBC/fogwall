package com.rbc.fogwall.observability;

import com.rbc.fogwall.servlet.ScmApiRequestContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

/**
 * Outermost servlet filter on an SCM API proxy listener: opens the per-request parent span and records the request
 * duration ({@code fogwall.scmapi.duration}). One instance is registered per provider, ahead of
 * {@link com.rbc.fogwall.servlet.filter.ScmApiAuditFilter}, so the {@code provider} label needs no parsing and the span
 * wraps the whole chain — authentication, the dialect gate, content inspection, and the upstream forward.
 *
 * <p>The span is a {@link SpanKind#SERVER} span whose parent is extracted from the inbound request's W3C
 * {@code traceparent} header, so an SCM API call that arrives from an already-traced CLI invocation continues that
 * trace. It is made current for the request, and the Log4j2 context-data bridge reads the current span to stamp
 * {@code trace_id}/{@code span_id} onto the request's log lines — the same correlation the git push path gets.
 *
 * <p>Unlike the transparent-proxy git path, the SCM API forward is synchronous: the dialect forward servlet blocks on
 * the upstream call and the {@code ScmApiRequestContext}'s outcome is known by the time the chain unwinds, so the span
 * is finalized inline in a {@code finally} block rather than from an async listener. Operation and outcome are read
 * from the shared {@link ScmApiRequestContext} at that point; a pure read (no mutation field) is recorded with the
 * operation {@code read}.
 */
public class ScmApiObservabilityFilter implements Filter {

    private static final TextMapGetter<HttpServletRequest> HEADER_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(HttpServletRequest carrier) {
            return Collections.list(carrier.getHeaderNames());
        }

        @Override
        public String get(HttpServletRequest carrier, String key) {
            return carrier == null ? null : carrier.getHeader(key);
        }
    };

    private final FogwallTelemetry telemetry;
    private final String provider;

    public ScmApiObservabilityFilter(FogwallTelemetry telemetry, String provider) {
        this.telemetry = telemetry;
        this.provider = provider;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!telemetry.isEnabled()
                || !(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }

        Context parent = telemetry
                .openTelemetry()
                .getPropagators()
                .getTextMapPropagator()
                .extract(Context.current(), httpRequest, HEADER_GETTER);

        Span span = telemetry
                .tracer()
                .spanBuilder("scmapi " + provider)
                .setSpanKind(SpanKind.SERVER)
                .setParent(parent)
                .setAttribute(FogwallTelemetry.PROVIDER.getKey(), provider)
                .startSpan();

        long startNanos = System.nanoTime();
        try (Scope ignored = Context.current().with(span).makeCurrent()) {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            span.recordException(e);
            throw e;
        } finally {
            complete(span, startNanos, httpRequest, httpResponse);
        }
    }

    private void complete(Span span, long startNanos, HttpServletRequest request, HttpServletResponse response) {
        double seconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
        ScmApiRequestContext context =
                (ScmApiRequestContext) request.getAttribute(ScmApiRequestContext.SCM_API_REQUEST_ATTR);
        String operation = operationOf(context);
        telemetry.recordScmApiDuration(seconds, provider, operation);

        span.setAttribute(FogwallTelemetry.PROVIDER.getKey(), provider);
        span.setAttribute(FogwallTelemetry.SCM_API_OPERATION.getKey(), operation);
        if (context != null && context.getStatus() != null) {
            span.setAttribute(
                    FogwallTelemetry.SCM_API_OUTCOME.getKey(),
                    context.getStatus().name());
        }
        int status = response.getStatus();
        span.setAttribute("http.response.status_code", status);
        if (status >= 500) {
            span.setStatus(StatusCode.ERROR);
        }
        span.end();
    }

    /**
     * The operation label: a mutation's field normalized to a provider-agnostic name, else the read's classified
     * resource ({@code issue.read}, …) set by the gate filter, else a bare {@code read}.
     */
    private static String operationOf(ScmApiRequestContext context) {
        if (context == null) {
            return "read";
        }
        if (context.getMutationField() != null) {
            return ScmApiOperations.normalize(context.getMutationField());
        }
        return context.getReadResource() != null ? context.getReadResource() : "read";
    }
}
