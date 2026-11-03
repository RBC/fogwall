package com.rbc.fogwall.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One running pipeline stage: started by a {@code FogwallTelemetry.start*Stage} method, ended exactly once with its
 * {@link StageOutcome}. Ending records the stage's duration and outcome metrics and ends its span, if it has one.
 *
 * <p>When telemetry is disabled every start method returns {@link #NOOP}, a shared instance that does nothing, so the
 * push path allocates nothing for an instrumented stage.
 */
public sealed interface StageTimer permits StageTimer.Noop, StageTimer.Live {

    /** The stage timer handed out when telemetry is disabled. */
    StageTimer NOOP = new Noop();

    /** Sets the provider label for a stage that only learns its provider after it starts. */
    void setProvider(String provider);

    /** Makes this stage's span current, so log lines written during the stage carry its trace. */
    Scope makeCurrent();

    /** Ends the stage. Later calls do nothing. */
    void end(StageOutcome outcome);

    /** Ends the stage as {@link StageOutcome#ERROR}, recording the failure on its span. Later calls do nothing. */
    default void fail(Throwable failure) {
        recordException(failure);
        end(StageOutcome.ERROR);
    }

    /** Records a failure on the span without ending the stage. */
    void recordException(Throwable failure);

    /** The disabled-telemetry timer. */
    final class Noop implements StageTimer {
        private Noop() {}

        @Override
        public void setProvider(String provider) {}

        @Override
        public Scope makeCurrent() {
            return Scope.noop();
        }

        @Override
        public void end(StageOutcome outcome) {}

        @Override
        public void recordException(Throwable failure) {}
    }

    /** A timed stage with live instruments; its span is invalid (never exported) for an untraced stage. */
    final class Live implements StageTimer {
        private final FogwallTelemetry telemetry;
        private final String stage;
        private final String mode;
        private final Span span;
        private final long startNanos = System.nanoTime();
        private final AtomicBoolean ended = new AtomicBoolean();
        private volatile String provider;

        Live(FogwallTelemetry telemetry, String stage, String mode, String provider, Span span) {
            this.telemetry = telemetry;
            this.stage = stage;
            this.mode = mode;
            this.provider = provider;
            this.span = span;
        }

        @Override
        public void setProvider(String provider) {
            this.provider = provider;
        }

        @Override
        public Scope makeCurrent() {
            return span.makeCurrent();
        }

        @Override
        public void recordException(Throwable failure) {
            span.recordException(failure);
        }

        @Override
        public void end(StageOutcome outcome) {
            if (!ended.compareAndSet(false, true)) {
                return;
            }
            double seconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
            String providerLabel = FogwallTelemetry.providerLabel(provider);
            telemetry.recordStage(seconds, stage, mode, providerLabel, outcome);
            span.setAttribute(FogwallTelemetry.PROVIDER, providerLabel);
            span.setAttribute(FogwallTelemetry.STAGE_OUTCOME, outcome.label());
            if (outcome == StageOutcome.ERROR) {
                span.setStatus(StatusCode.ERROR);
            }
            span.end();
        }
    }
}
