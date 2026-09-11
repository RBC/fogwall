package com.rbc.fogwall.observability;

import com.rbc.fogwall.config.OtelConfig;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import lombok.extern.slf4j.Slf4j;

/**
 * Builds fogwall's {@link FogwallTelemetry} from {@code otel.*} config. This is the only place the OpenTelemetry SDK
 * (exporters, providers, resource) is assembled; the rest of fogwall records through the vendor-neutral API in
 * fogwall-core. Both runnable modules — the standalone server and the dashboard — call {@link #build} at startup.
 *
 * <p>When {@code otel.enabled} is false this returns a disabled {@link FogwallTelemetry} that records nothing, so the
 * default deployment pays no observability cost. When enabled it wires an OTLP exporter (gRPC or HTTP per
 * {@code otel.protocol}), a batch span processor and a periodic metric reader, W3C trace-context propagation for
 * continuing inbound traces, and a JVM shutdown hook that flushes and closes the SDK on exit.
 */
@Slf4j
public final class OpenTelemetryBootstrap {

    private OpenTelemetryBootstrap() {}

    /**
     * @param cfg the {@code otel:} config block
     * @param serviceVersion the {@code service.version} resource value (from {@code BuildInfo})
     */
    public static FogwallTelemetry build(OtelConfig cfg, String serviceVersion) {
        if (!cfg.isEnabled()) {
            log.info("OpenTelemetry disabled (otel.enabled=false); no traces or metrics are exported.");
            return FogwallTelemetry.disabled();
        }

        Resource resource = Resource.getDefault().merge(Resource.create(resourceAttributes(cfg, serviceVersion)));
        OpenTelemetrySdkBuilder builder = OpenTelemetrySdk.builder()
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()));

        if (cfg.getTracing().isEnabled()) {
            builder.setTracerProvider(SdkTracerProvider.builder()
                    .setResource(resource)
                    .addSpanProcessor(
                            BatchSpanProcessor.builder(spanExporter(cfg)).build())
                    .build());
        }
        if (cfg.getMetrics().isEnabled()) {
            builder.setMeterProvider(SdkMeterProvider.builder()
                    .setResource(resource)
                    .registerMetricReader(
                            PeriodicMetricReader.builder(metricExporter(cfg)).build())
                    .build());
        }

        OpenTelemetrySdk sdk = builder.build();
        Runtime.getRuntime().addShutdownHook(new Thread(sdk::close, "otel-shutdown"));
        // Register globally so the Log4j2 context-data bridge (a ServiceLoader ContextDataProvider) resolves this SDK
        // when stamping trace_id/span_id onto log lines. Guarded because the global can be set only once per JVM.
        try {
            GlobalOpenTelemetry.set(sdk);
        } catch (IllegalStateException e) {
            log.warn("GlobalOpenTelemetry already set; keeping the existing global instance", e);
        }

        log.info(
                "OpenTelemetry enabled: protocol={} endpoint={} tracing={} metrics={}",
                cfg.getProtocol(),
                cfg.getEndpoint().isBlank() ? "<exporter default>" : cfg.getEndpoint(),
                cfg.getTracing().isEnabled(),
                cfg.getMetrics().isEnabled());
        return FogwallTelemetry.enabled(sdk);
    }

    private static Attributes resourceAttributes(OtelConfig cfg, String serviceVersion) {
        var attrs = Attributes.builder().put("service.name", cfg.getServiceName());
        if (serviceVersion != null && !serviceVersion.isBlank()) {
            attrs.put("service.version", serviceVersion);
        }
        cfg.getResourceAttributes().forEach(attrs::put);
        return attrs.build();
    }

    private static boolean isHttp(OtelConfig cfg) {
        return "http".equalsIgnoreCase(cfg.getProtocol()) || "http/protobuf".equalsIgnoreCase(cfg.getProtocol());
    }

    private static SpanExporter spanExporter(OtelConfig cfg) {
        if (isHttp(cfg)) {
            var b = OtlpHttpSpanExporter.builder();
            if (!cfg.getEndpoint().isBlank()) b.setEndpoint(cfg.getEndpoint());
            return b.build();
        }
        var b = OtlpGrpcSpanExporter.builder();
        if (!cfg.getEndpoint().isBlank()) b.setEndpoint(cfg.getEndpoint());
        return b.build();
    }

    private static MetricExporter metricExporter(OtelConfig cfg) {
        if (isHttp(cfg)) {
            var b = OtlpHttpMetricExporter.builder();
            if (!cfg.getEndpoint().isBlank()) b.setEndpoint(cfg.getEndpoint());
            return b.build();
        }
        var b = OtlpGrpcMetricExporter.builder();
        if (!cfg.getEndpoint().isBlank()) b.setEndpoint(cfg.getEndpoint());
        return b.build();
    }
}
