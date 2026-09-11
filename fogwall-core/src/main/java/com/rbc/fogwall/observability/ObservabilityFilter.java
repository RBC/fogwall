package com.rbc.fogwall.observability;

import com.rbc.fogwall.git.GitRequestDetails;
import com.rbc.fogwall.servlet.FogwallServlet;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Outermost servlet filter that opens the per-request parent span and records the transport-level push metrics
 * ({@code fogwall.push.active} gauge and {@code fogwall.push.duration} histogram). One instance is registered per proxy
 * mode (server, proxy) so the {@code mode} label needs no path parsing.
 *
 * <p>The span is a {@link SpanKind#SERVER} span whose parent is extracted from the inbound request's W3C
 * {@code traceparent} header, so a push that arrives from an already-traced client continues that trace. It is made
 * current for the synchronous portion of the request, where fogwall's validation runs and logs, and its context is also
 * published as a request attribute ({@link FogwallServlet#OTEL_CONTEXT_ATTR}) so the transparent proxy's async forward
 * callbacks can re-activate it on their own thread. Either way the Log4j2 context-data bridge reads the current span to
 * stamp {@code trace_id}/{@code span_id} onto those log lines.
 *
 * <p>The transparent proxy dispatches the upstream forward asynchronously; when it does, completion is recorded from an
 * {@link AsyncListener} so the duration covers the real forward and the span ends only when the response is done.
 * Server mode (the JGit {@code GitServlet}) is synchronous and is finalized inline. Either way finalization runs
 * exactly once.
 *
 * <p>This covers both HTTP transports of the two proxy modes; SSH server-mode pushes do not traverse the servlet
 * container and so carry no span or duration/active metric. Their decision and forward outcomes are still recorded, via
 * {@link MeteringPushStore}.
 */
public class ObservabilityFilter implements Filter {

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
    private final String mode;

    public ObservabilityFilter(FogwallTelemetry telemetry, String mode) {
        this.telemetry = telemetry;
        this.mode = mode;
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
                .spanBuilder("git.push " + mode)
                .setSpanKind(SpanKind.SERVER)
                .setParent(parent)
                .setAttribute(FogwallTelemetry.MODE.getKey(), mode)
                .startSpan();

        // Publish the span context so FogwallServlet's async proxy-forward callbacks (which run on Jetty threads after
        // this scope closes) can re-activate it and keep their log lines correlated to the trace.
        Context spanContext = Context.current().with(span);
        httpRequest.setAttribute(FogwallServlet.OTEL_CONTEXT_ATTR, spanContext);

        long startNanos = System.nanoTime();
        telemetry.recordActive(1, mode);
        AtomicBoolean finalized = new AtomicBoolean(false);

        boolean async = false;
        try (Scope ignored = spanContext.makeCurrent()) {
            chain.doFilter(request, response);
            if (httpRequest.isAsyncStarted()) {
                async = true;
                httpRequest.getAsyncContext().addListener(new AsyncListener() {
                    @Override
                    public void onComplete(AsyncEvent event) {
                        complete(span, startNanos, httpRequest, httpResponse, finalized);
                    }

                    @Override
                    public void onError(AsyncEvent event) {
                        if (event.getThrowable() != null) span.recordException(event.getThrowable());
                    }

                    @Override
                    public void onTimeout(AsyncEvent event) {
                        span.setStatus(StatusCode.ERROR, "async timeout");
                    }

                    @Override
                    public void onStartAsync(AsyncEvent event) {}
                });
            }
        } catch (IOException | ServletException | RuntimeException e) {
            span.recordException(e);
            throw e;
        } finally {
            if (!async) {
                complete(span, startNanos, httpRequest, httpResponse, finalized);
            }
        }
    }

    private void complete(
            Span span,
            long startNanos,
            HttpServletRequest request,
            HttpServletResponse response,
            AtomicBoolean finalized) {
        if (!finalized.compareAndSet(false, true)) {
            return;
        }
        double seconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
        String provider = providerOf(request);
        telemetry.recordDuration(seconds, mode, provider);
        telemetry.recordActive(-1, mode);

        span.setAttribute(FogwallTelemetry.PROVIDER.getKey(), provider);
        String repo = repoOf(request);
        if (repo != null) {
            span.setAttribute(FogwallTelemetry.REPO.getKey(), repo);
        }
        int status = response.getStatus();
        span.setAttribute("http.response.status_code", status);
        if (status >= 500) {
            span.setStatus(StatusCode.ERROR);
        }
        span.end();
    }

    private static String providerOf(HttpServletRequest request) {
        GitRequestDetails details = (GitRequestDetails) request.getAttribute(FogwallServlet.GIT_REQUEST_ATTR);
        if (details != null && details.getProvider() != null) {
            return details.getProvider().getName();
        }
        return "unknown";
    }

    private static String repoOf(HttpServletRequest request) {
        GitRequestDetails details = (GitRequestDetails) request.getAttribute(FogwallServlet.GIT_REQUEST_ATTR);
        if (details != null && details.getRepoRef() != null) {
            return details.getRepoRef().getSlug();
        }
        return null;
    }
}
