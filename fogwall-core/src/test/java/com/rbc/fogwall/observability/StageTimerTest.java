package com.rbc.fogwall.observability;

import static org.junit.jupiter.api.Assertions.*;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;

class StageTimerTest {

    private final InMemoryTelemetry otel = new InMemoryTelemetry();
    private final FogwallTelemetry telemetry = otel.telemetry();

    @Test
    void disabledTelemetry_handsOutTheSharedNoop() {
        FogwallTelemetry disabled = FogwallTelemetry.disabled();

        assertSame(StageTimer.NOOP, disabled.startStage("url-rule", "server", "github"));
        assertSame(StageTimer.NOOP, disabled.startUntracedStage("url-rule", "ssh", "github"));
        assertSame(StageTimer.NOOP, disabled.startStage("forward", "proxy", "github", Context.root(), SpanKind.CLIENT));
        assertSame(StageTimer.NOOP, disabled.startDetachedStage("forward", "server", "push-1"));
    }

    @Test
    void stage_isAChildOfTheCurrentSpan_andRecordsItsOutcome() {
        Span request = telemetry.tracer().spanBuilder("git.push server").startSpan();
        try (Scope ignored = request.makeCurrent()) {
            telemetry.startStage("secret-scan", "server", "github").end(StageOutcome.BLOCK);
        }
        request.end();

        SpanData stage = otel.span("secret-scan");
        assertEquals(request.getSpanContext().getSpanId(), stage.getParentSpanId());
        assertEquals("secret-scan", stage.getAttributes().get(FogwallTelemetry.STAGE));
        assertEquals("server", stage.getAttributes().get(FogwallTelemetry.MODE));
        assertEquals("github", stage.getAttributes().get(FogwallTelemetry.PROVIDER));
        assertEquals("block", stage.getAttributes().get(FogwallTelemetry.STAGE_OUTCOME));
        assertEquals(1, otel.stageCount("secret-scan", "server", "github", StageOutcome.BLOCK));
        assertTrue(otel.hasPoints("fogwall.stage.duration"));
    }

    @Test
    void endingTwice_recordsOnce() {
        StageTimer stage = telemetry.startStage("url-rule", "proxy", "github");
        stage.end(StageOutcome.PASS);
        stage.end(StageOutcome.ERROR);

        assertEquals(1, otel.stageCount("url-rule", "proxy", "github", StageOutcome.PASS));
        assertEquals(0, otel.stageCount("url-rule", "proxy", "github", StageOutcome.ERROR));
        assertEquals(1, otel.spans().size());
    }

    @Test
    void failedStage_isAnErrorSpanCarryingTheException() {
        telemetry.startStage("diff-scan", "server", "gitlab").fail(new IllegalStateException("boom"));

        SpanData stage = otel.span("diff-scan");
        assertEquals(StatusCode.ERROR, stage.getStatus().getStatusCode());
        assertEquals("error", stage.getAttributes().get(FogwallTelemetry.STAGE_OUTCOME));
        assertEquals("exception", stage.getEvents().get(0).getName());
        assertEquals(1, otel.stageCount("diff-scan", "server", "gitlab", StageOutcome.ERROR));
    }

    @Test
    void untracedStage_recordsMetricsButNoSpan() {
        telemetry.startUntracedStage("push-permission", "ssh", "github").end(StageOutcome.PASS);

        assertTrue(otel.spans().isEmpty());
        assertEquals(1, otel.stageCount("push-permission", "ssh", "github", StageOutcome.PASS));
    }

    @Test
    void detachedStage_startsANewTraceCarryingThePushId_andTakesItsProviderLate() {
        Span unrelated = telemetry.tracer().spanBuilder("sweep").startSpan();
        try (Scope ignored = unrelated.makeCurrent()) {
            StageTimer stage = telemetry.startDetachedStage("forward", "server", "push-1");
            stage.setProvider("gitea");
            stage.end(StageOutcome.PASS);
        }
        unrelated.end();

        SpanData forward = otel.span("forward");
        assertFalse(forward.getParentSpanContext().isValid());
        assertEquals("push-1", forward.getAttributes().get(FogwallTelemetry.PUSH_ID));
        assertEquals(1, otel.stageCount("forward", "server", "gitea", StageOutcome.PASS));
    }

    @Test
    void missingProvider_isLabelledUnknown() {
        telemetry.startStage("url-rule", "proxy", null).end(StageOutcome.PASS);

        assertEquals(1, otel.stageCount("url-rule", "proxy", "unknown", StageOutcome.PASS));
    }

    @Test
    void parkedBacklog_isReportedOnlyOnceASweepHasObservedIt() {
        assertFalse(otel.hasPoints("fogwall.deferred.parked"));

        telemetry.observeParkedBacklog(3, 4096);

        assertEquals(
                3L,
                otel.longValue("fogwall.deferred.parked", Attributes.empty()).orElseThrow());
        assertEquals(
                4096L,
                otel.longValue("fogwall.deferred.parked.size", Attributes.empty())
                        .orElseThrow());
    }
}
