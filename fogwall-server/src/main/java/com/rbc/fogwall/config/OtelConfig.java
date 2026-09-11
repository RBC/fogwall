package com.rbc.fogwall.config;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;

/**
 * Binds the {@code otel:} block in fogwall.yml — OpenTelemetry observability. Off by default so the standard push path
 * carries no instrumentation cost; an operator opts in by pointing fogwall at an OTLP collector.
 *
 * <p>When enabled, fogwall builds an OpenTelemetry SDK that exports over OTLP:
 *
 * <ul>
 *   <li>a per-request parent span for pushes arriving over HTTP (server mode and transparent proxy), continuing any
 *       inbound W3C trace;
 *   <li>request-level metrics — in-flight gauge, push duration, decision counts, and forward outcomes;
 *   <li>{@code trace_id}/{@code span_id} stamped onto the Log4j2 layout for log-to-trace correlation.
 * </ul>
 *
 * <p>Per-stage child spans and per-check validation metrics are a later increment and are not produced here. SSH
 * server-mode pushes do not traverse the servlet container, so they carry no parent span (decision and forward metrics
 * still cover them).
 */
@Data
public class OtelConfig {

    /**
     * Master switch. Defaults to {@code false}: with no OTLP collector configured, building exporters and starting a
     * span per request would be pure overhead. Set {@code true} (and an {@link #endpoint}) to turn observability on.
     *
     * <p>Env var: {@code fogwall_OTEL_ENABLED}.
     */
    private boolean enabled = false;

    /**
     * OTLP collector endpoint, e.g. {@code http://otel-collector:4317} for gRPC or {@code http://otel-collector:4318}
     * for HTTP. Empty (default) falls back to the OTLP exporter's own default host/port for the selected
     * {@link #protocol}. Set via YAML ({@code otel.endpoint:}) or env var ({@code fogwall_OTEL_ENDPOINT}).
     */
    private String endpoint = "";

    /**
     * OTLP transport: {@code grpc} (default) or {@code http} (OTLP/HTTP + protobuf). Env var:
     * {@code fogwall_OTEL_PROTOCOL}.
     */
    private String protocol = "grpc";

    /**
     * {@code service.name} resource attribute reported to the collector. Defaults to {@code fogwall}. The
     * {@code service.version} attribute is filled from {@code BuildInfo} and is not configurable here. Env var:
     * {@code fogwall_OTEL_SERVICENAME}.
     */
    private String serviceName = "fogwall";

    /** Tracing toggle — spans are produced only when both {@link #enabled} and this are true. */
    private SignalConfig tracing = new SignalConfig();

    /** Metrics toggle — instruments export only when both {@link #enabled} and this are true. */
    private SignalConfig metrics = new SignalConfig();

    /**
     * Extra resource attributes merged onto every span and metric (e.g. {@code deployment.environment: prod}). Keys and
     * values are strings. Env vars override individual entries as {@code fogwall_OTEL_RESOURCEATTRIBUTES_<key>}.
     */
    private Map<String, String> resourceAttributes = new LinkedHashMap<>();

    /** A per-signal enable flag ({@code otel.tracing.enabled}, {@code otel.metrics.enabled}); both default on. */
    @Data
    public static class SignalConfig {
        private boolean enabled = true;
    }
}
