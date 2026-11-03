package com.rbc.fogwall.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import java.util.concurrent.atomic.AtomicLong;

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

    /** Fetch decision: {@code ALLOWED} or {@code BLOCKED}. Low cardinality. */
    public static final AttributeKey<String> FETCH_RESULT = AttributeKey.stringKey("fogwall.fetch.result");

    /** Why a fetch was refused, from a fixed set; {@code none} when it was allowed. Low cardinality. */
    public static final AttributeKey<String> FETCH_REFUSAL = AttributeKey.stringKey("fogwall.fetch.refusal");

    /**
     * Pipeline stage: a {@code PushStepKind} key, shared by a server-mode hook and its transparent-proxy filter, or one
     * of the lifecycle stages {@link #APPROVAL_STAGE} and {@link #FORWARD_STAGE}. Low cardinality.
     */
    public static final AttributeKey<String> STAGE = AttributeKey.stringKey("fogwall.stage");

    /** How a stage ended: {@code pass}, {@code block} or {@code error} (see {@link StageOutcome}). Low cardinality. */
    public static final AttributeKey<String> STAGE_OUTCOME = AttributeKey.stringKey("fogwall.stage.outcome");

    /** Push record id — SPAN attribute only; it joins a parked push's forward to the request that received it. */
    public static final AttributeKey<String> PUSH_ID = AttributeKey.stringKey("fogwall.push.id");

    /** {@link #MODE} value for server mode over HTTP. */
    public static final String MODE_SERVER = "server";

    /** {@link #MODE} value for the transparent proxy. */
    public static final String MODE_PROXY = "proxy";

    /** {@link #MODE} value for server mode over SSH. */
    public static final String MODE_SSH = "ssh";

    /** Stage name for the wait on a push's review, between validation and forwarding. */
    public static final String APPROVAL_STAGE = "approval";

    /** Stage name for the push to the upstream provider. */
    public static final String FORWARD_STAGE = "forward";

    /**
     * Request attribute holding this {@link FogwallTelemetry} on a transparent-proxy request, set by
     * {@code ObservabilityFilter} only when telemetry is enabled. The filter chain times each filter as a stage when it
     * is present; when it is absent nothing is timed.
     */
    public static final String REQUEST_ATTR = "fogwall.telemetry";

    private final boolean enabled;
    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;
    private final LongUpDownCounter activePushes;
    private final DoubleHistogram pushDuration;
    private final LongCounter pushDecisions;
    private final LongCounter forwards;
    private final DoubleHistogram scmApiDuration;
    private final LongCounter scmApiActions;
    private final LongCounter fetchDecisions;
    private final DoubleHistogram stageDuration;
    private final LongCounter stageOutcomes;
    private final LongCounter forwardRestarts;
    private final DoubleHistogram approvalToForward;
    private final AtomicLong parkedPushes = new AtomicLong(-1);
    private final AtomicLong parkedBytes = new AtomicLong(-1);

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
        this.fetchDecisions = meter.counterBuilder("fogwall.fetch.decisions")
                .setUnit("{fetch}")
                .setDescription("Clones and fetches by decision and refusal reason")
                .build();
        this.stageDuration = meter.histogramBuilder("fogwall.stage.duration")
                .setUnit("s")
                .setDescription("Wall-clock duration of one pipeline stage of a push")
                .build();
        this.stageOutcomes = meter.counterBuilder("fogwall.stage.outcomes")
                .setUnit("{stage}")
                .setDescription("Pipeline stages run, by stage and outcome")
                .build();
        this.forwardRestarts = meter.counterBuilder("fogwall.deferred.forward.restarts")
                .setUnit("{push}")
                .setDescription("Parked-push forwards restarted because the instance forwarding them stopped")
                .build();
        this.approvalToForward = meter.histogramBuilder("fogwall.deferred.forward.delay")
                .setUnit("s")
                .setDescription("Time from a parked push's approval to its forward completing")
                .build();
        // Observed values are -1 until the first deferred-forwarding sweep, and not reported until then.
        meter.gaugeBuilder("fogwall.deferred.parked")
                .ofLongs()
                .setUnit("{push}")
                .setDescription("Parked pushes whose pack is stored, as of the last sweep")
                .buildWithCallback(m -> {
                    long value = parkedPushes.get();
                    if (value >= 0) m.record(value);
                });
        meter.gaugeBuilder("fogwall.deferred.parked.size")
                .ofLongs()
                .setUnit("By")
                .setDescription("Bytes held by stored packs of parked pushes, as of the last sweep")
                .buildWithCallback(m -> {
                    long value = parkedBytes.get();
                    if (value >= 0) m.record(value);
                });
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

    /** Record fogwall's decision on one clone or fetch. {@code mode} uses the {@link #MODE} values. */
    public void recordFetchDecision(String provider, String mode, String result, String refusal) {
        fetchDecisions.add(
                1, Attributes.of(PROVIDER, provider, MODE, mode, FETCH_RESULT, result, FETCH_REFUSAL, refusal));
    }

    /**
     * Starts a stage whose span is a child of the current context: a stage running on the request thread, under the
     * per-request span {@code ObservabilityFilter} made current.
     */
    public StageTimer startStage(String stage, String mode, String provider) {
        if (!enabled) return StageTimer.NOOP;
        return live(
                stage, mode, provider, stageSpan(stage, mode, SpanKind.INTERNAL).startSpan());
    }

    /**
     * Starts a stage whose span is a child of {@code parent}, for a stage that ends on another thread than the request
     * thread that started it.
     */
    public StageTimer startStage(String stage, String mode, String provider, Context parent, SpanKind kind) {
        if (!enabled) return StageTimer.NOOP;
        return live(
                stage,
                mode,
                provider,
                stageSpan(stage, mode, kind).setParent(parent).startSpan());
    }

    /**
     * Starts a stage that records metrics only, for a transport with no per-request span to parent it to (SSH): a span
     * there would start a new trace per stage.
     */
    public StageTimer startUntracedStage(String stage, String mode, String provider) {
        if (!enabled) return StageTimer.NOOP;
        return live(stage, mode, provider, Span.getInvalid());
    }

    /**
     * Starts a stage that runs after the request that received the push has ended, such as the forward of a parked
     * push. Its span starts a new trace and carries the push id, which joins it to the receiving request's trace.
     */
    public StageTimer startDetachedStage(String stage, String mode, String pushId) {
        if (!enabled) return StageTimer.NOOP;
        Span span = stageSpan(stage, mode, SpanKind.INTERNAL)
                .setNoParent()
                .setAttribute(PUSH_ID, pushId)
                .startSpan();
        return live(stage, mode, null, span);
    }

    private SpanBuilder stageSpan(String stage, String mode, SpanKind kind) {
        return tracer.spanBuilder(stage)
                .setSpanKind(kind)
                .setAttribute(STAGE, stage)
                .setAttribute(MODE, mode);
    }

    private StageTimer live(String stage, String mode, String provider, Span span) {
        return new StageTimer.Live(this, stage, mode, provider, span);
    }

    /** Records one completed stage; called by {@link StageTimer} when the stage ends. */
    void recordStage(double seconds, String stage, String mode, String provider, StageOutcome outcome) {
        stageDuration.record(seconds, Attributes.of(STAGE, stage, MODE, mode, PROVIDER, provider));
        stageOutcomes.add(
                1, Attributes.of(STAGE, stage, MODE, mode, PROVIDER, provider, STAGE_OUTCOME, outcome.label()));
    }

    /**
     * Records the parked-push backlog seen by a deferred-forwarding sweep. Every instance sweeps, so every instance
     * reports the same shared backlog; aggregate it across instances with max, not sum.
     */
    public void observeParkedBacklog(long pushes, long bytes) {
        parkedPushes.set(pushes);
        parkedBytes.set(bytes);
    }

    /** Records a parked-push forward the sweep restarted because the claim of the instance forwarding it lapsed. */
    public void recordForwardRestart() {
        forwardRestarts.add(1);
    }

    /** Records the time from a parked push's approval to its forward completing. */
    public void recordApprovalToForward(double seconds, String provider) {
        approvalToForward.record(seconds, Attributes.of(PROVIDER, provider));
    }

    /** The metric label for a provider name: {@code unknown} when there is none. */
    static String providerLabel(String provider) {
        return provider == null || provider.isBlank() ? "unknown" : provider;
    }
}
