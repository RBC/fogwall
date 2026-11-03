package com.rbc.fogwall.observability;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** A live {@link FogwallTelemetry} backed by in-memory exporters, for asserting on the spans and metrics it records. */
public final class InMemoryTelemetry {

    private final InMemorySpanExporter spans = InMemorySpanExporter.create();
    private final InMemoryMetricReader metrics = InMemoryMetricReader.create();
    private final OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
            .setTracerProvider(SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(spans))
                    .build())
            .setMeterProvider(
                    SdkMeterProvider.builder().registerMetricReader(metrics).build())
            .build();
    private final FogwallTelemetry telemetry = FogwallTelemetry.enabled(sdk);

    public FogwallTelemetry telemetry() {
        return telemetry;
    }

    /** Finished spans, in the order they ended. */
    public List<SpanData> spans() {
        return spans.getFinishedSpanItems();
    }

    /** The finished span with this name; fails if there is not exactly one. */
    public SpanData span(String name) {
        List<SpanData> named =
                spans().stream().filter(s -> s.getName().equals(name)).toList();
        if (named.size() != 1) {
            throw new AssertionError("expected one span named " + name + ", got " + spanNames());
        }
        return named.get(0);
    }

    public List<String> spanNames() {
        return spans().stream().map(SpanData::getName).toList();
    }

    /** The metrics collected so far. Collecting resets nothing: counters are cumulative. */
    public Collection<MetricData> collect() {
        return metrics.collectAllMetrics();
    }

    /** The cumulative value of a long counter or gauge point with exactly these attributes. */
    public Optional<Long> longValue(String metric, Attributes attributes) {
        return collect().stream()
                .filter(m -> m.getName().equals(metric))
                .flatMap(m -> m.getData().getPoints().stream())
                .filter(p -> p.getAttributes().equals(attributes))
                .map(p -> ((LongPointData) p).getValue())
                .findFirst();
    }

    /** Whether the named metric has any point at all. */
    public boolean hasPoints(String metric) {
        return collect().stream()
                .filter(m -> m.getName().equals(metric))
                .anyMatch(m -> !m.getData().getPoints().isEmpty());
    }

    /** The {@code fogwall.stage.outcomes} count for one stage. */
    public long stageCount(String stage, String mode, String provider, StageOutcome outcome) {
        return longValue(
                        "fogwall.stage.outcomes",
                        Attributes.of(
                                FogwallTelemetry.STAGE,
                                stage,
                                FogwallTelemetry.MODE,
                                mode,
                                FogwallTelemetry.PROVIDER,
                                provider,
                                FogwallTelemetry.STAGE_OUTCOME,
                                outcome.label()))
                .orElse(0L);
    }
}
