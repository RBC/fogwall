package com.rbc.fogwall.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;

/**
 * Central holder for fogwall's OpenTelemetry instruments. Both the request-level {@code ObservabilityFilter} and the
 * per-mode terminal seams (proxy persistence filter, server/SSH post-receive hooks) record through this one object so
 * the instrument names, units, and attribute keys are defined in a single place.
 *
 * <p>The {@link Tracer} and {@link Meter} come from an injected {@link OpenTelemetry}. When observability is disabled
 * the instance is built from {@link OpenTelemetry#noop()} and {@link #isEnabled()} returns {@code false}, so callers
 * that add real per-request overhead (starting a span, registering an async listener) can skip it entirely and leave
 * the default push path free of instrumentation cost.
 *
 * <p><b>Cardinality.</b> Metric attributes are deliberately limited to low-cardinality dimensions — provider, proxy
 * mode, push status, forward outcome, and for the SCM API proxy the mutation/operation name (bounded by the dialect
 * allowlists) and its outcome. The repository slug is high-cardinality and is attached to spans only, never to a
 * metric, to avoid a time-series explosion at enterprise scale.
 */
public final class FogwallTelemetry {

    /** Instrumentation scope name shared by the tracer and meter. */
    public static final String SCOPE = "com.rbc.fogwall";

    /** SCM provider name, e.g. {@code github}. Low cardinality. */
    public static final AttributeKey<String> PROVIDER = AttributeKey.stringKey("fogwall.provider");

    /** Proxy mode / transport: {@code server}, {@code proxy}, or {@code ssh}. Low cardinality. */
    public static final AttributeKey<String> MODE = AttributeKey.stringKey("fogwall.mode");

    /** Final push decision: {@code ALLOWED}, {@code REJECTED}, {@code REVIEW}, {@code ERROR}. Low cardinality. */
    public static final AttributeKey<String> STATUS = AttributeKey.stringKey("fogwall.push.status");

    /** Upstream-forward outcome: {@code success} or {@code failure}. Low cardinality. */
    public static final AttributeKey<String> OUTCOME = AttributeKey.stringKey("fogwall.forward.outcome");

    /** Repository slug — SPAN attribute only; never a metric attribute (high cardinality). */
    public static final AttributeKey<String> REPO = AttributeKey.stringKey("fogwall.repo");

    /**
     * SCM API operation, normalized to a provider-agnostic name where it flattens ({@code pull_request.create},
     * {@code issue.update}, …) and the raw per-dialect op otherwise — see {@link ScmApiOperations}; a pure read is
     * {@code read}. Low cardinality (bounded by the dialect allowlists).
     */
    public static final AttributeKey<String> SCM_API_OPERATION = AttributeKey.stringKey("fogwall.scmapi.operation");

    /** SCM API action outcome: {@code FORWARDED}, {@code DENIED}, {@code REJECTED}, {@code ERROR}. Low cardinality. */
    public static final AttributeKey<String> SCM_API_OUTCOME = AttributeKey.stringKey("fogwall.scmapi.outcome");

    private final boolean enabled;
    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;
    private final LongUpDownCounter activePushes;
    private final DoubleHistogram pushDuration;
    private final LongCounter pushDecisions;
    private final LongCounter forwards;
    private final DoubleHistogram scmApiDuration;
    private final LongCounter scmApiActions;

    private FogwallTelemetry(OpenTelemetry openTelemetry, boolean enabled) {
        this.enabled = enabled;
        this.openTelemetry = openTelemetry;
        this.tracer = openTelemetry.getTracer(SCOPE);
        Meter meter = openTelemetry.getMeter(SCOPE);
        this.activePushes = meter.upDownCounterBuilder("fogwall.push.active")
                .setUnit("{push}")
                .setDescription("Pushes currently in flight through the HTTP proxy")
                .build();
        this.pushDuration = meter.histogramBuilder("fogwall.push.duration")
                .setUnit("s")
                .setDescription("Wall-clock duration of a push request through the HTTP proxy")
                .build();
        this.pushDecisions = meter.counterBuilder("fogwall.push.decisions")
                .setUnit("{push}")
                .setDescription("Pushes by final validation decision")
                .build();
        this.forwards = meter.counterBuilder("fogwall.push.forward")
                .setUnit("{push}")
                .setDescription("Upstream forward attempts by outcome")
                .build();
        this.scmApiDuration = meter.histogramBuilder("fogwall.scmapi.duration")
                .setUnit("s")
                .setDescription("Wall-clock duration of an SCM API proxy request")
                .build();
        this.scmApiActions = meter.counterBuilder("fogwall.scmapi.actions")
                .setUnit("{action}")
                .setDescription("SCM API proxy mutations and refusals by operation and outcome")
                .build();
    }

    /** Build instruments backed by a live SDK. */
    public static FogwallTelemetry enabled(OpenTelemetry openTelemetry) {
        return new FogwallTelemetry(openTelemetry, true);
    }

    /** Build no-op instruments; {@link #isEnabled()} is {@code false}. */
    public static FogwallTelemetry disabled() {
        return new FogwallTelemetry(OpenTelemetry.noop(), false);
    }

    /** Whether a live SDK is wired. Callers use this to skip per-request span/listener overhead when off. */
    public boolean isEnabled() {
        return enabled;
    }

    /** The tracer for the {@code com.rbc.fogwall} scope. Returns a no-op tracer when disabled. */
    public Tracer tracer() {
        return tracer;
    }

    /** The underlying instance — used by the filter to reach the context propagators for inbound trace extraction. */
    public OpenTelemetry openTelemetry() {
        return openTelemetry;
    }

    /** Increment ({@code +1}) or decrement ({@code -1}) the in-flight push gauge for a proxy mode. */
    public void recordActive(long delta, String mode) {
        activePushes.add(delta, Attributes.of(MODE, mode));
    }

    /** Record the wall-clock duration of a completed HTTP push request. */
    public void recordDuration(double seconds, String mode, String provider) {
        pushDuration.record(seconds, Attributes.of(MODE, mode, PROVIDER, provider));
    }

    /**
     * Record fogwall's final decision on a push. Transport-agnostic — a push's decision does not depend on how it
     * arrived, so this carries no {@link #MODE}; the transport view lives on
     * {@link #recordDuration}/{@link #recordActive}. Recorded from the store decorator at the terminal status
     * transition, covering both proxy modes and both server-mode transports.
     */
    public void recordDecision(String provider, String status) {
        pushDecisions.add(1, Attributes.of(PROVIDER, provider, STATUS, status));
    }

    /** Record the outcome of a single upstream-forward attempt. Transport-agnostic, like {@link #recordDecision}. */
    public void recordForward(String provider, boolean success) {
        forwards.add(1, Attributes.of(PROVIDER, provider, OUTCOME, success ? "success" : "failure"));
    }

    /**
     * Start a {@link SpanKind#CLIENT} span around fogwall's outbound forward of an SCM API call to the upstream
     * provider — a child of the current per-request server span. The upstream status and this leg's own timing hang off
     * it, isolating upstream latency from fogwall's own overhead. fogwall cannot propagate the trace into the provider,
     * so this measures the call from fogwall's side. Returns a no-op span when telemetry is disabled.
     */
    public Span startScmApiForwardSpan(String provider) {
        return tracer.spanBuilder("scmapi.forward")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(PROVIDER.getKey(), provider)
                .startSpan();
    }

    /** Record the wall-clock duration of a completed SCM API proxy request, by provider and operation. */
    public void recordScmApiDuration(double seconds, String provider, String operation) {
        scmApiDuration.record(seconds, Attributes.of(PROVIDER, provider, SCM_API_OPERATION, operation));
    }

    /**
     * Record one audited SCM API action — a proxied mutation or a refusal — by operation and outcome. Recorded from the
     * store decorator at the single write seam, so a read that produces no audit record produces no count either.
     */
    public void recordScmApiAction(String provider, String operation, String outcome) {
        scmApiActions.add(1, Attributes.of(PROVIDER, provider, SCM_API_OPERATION, operation, SCM_API_OUTCOME, outcome));
    }
}
