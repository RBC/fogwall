# Observability (OpenTelemetry)

_Available since v1.4.0, opt-in._

fogwall can export traces and metrics over OTLP to an OpenTelemetry collector (Grafana Tempo/Mimir, Jaeger, Datadog, and
the like) and stamp trace and span IDs onto its own logs for correlation. It is **off by default** — with no collector
configured, starting a span per request and building exporters would be pure overhead — so an operator turns it on by
setting `otel.enabled: true` and pointing fogwall at a collector.

```yaml
otel:
  enabled: false
  # OTLP collector endpoint. Left blank, the exporter's own default is used (gRPC :4317, HTTP :4318).
  endpoint: http://otel-collector:4317
  protocol: grpc # grpc | http
  service-name: fogwall
  tracing:
    enabled: true
  metrics:
    enabled: true
  resource-attributes:
    deployment.environment: prod
```

## Settings

| Key                   | Default   | Meaning                                                                                    |
| --------------------- | --------- | ------------------------------------------------------------------------------------------ |
| `enabled`             | `false`   | Master switch. When off, nothing is exported and the push path carries no instrumentation. |
| `endpoint`            | _(blank)_ | OTLP collector endpoint. Blank uses the exporter default for the chosen protocol.          |
| `protocol`            | `grpc`    | OTLP transport: `grpc` or `http` (OTLP/HTTP + protobuf).                                   |
| `service-name`        | `fogwall` | The `service.name` resource attribute. `service.version` is filled from the build.         |
| `tracing.enabled`     | `true`    | Emit spans (only when `enabled` is also true).                                             |
| `metrics.enabled`     | `true`    | Emit metrics (only when `enabled` is also true).                                           |
| `resource-attributes` | _(none)_  | Extra key/value resource attributes merged onto every span and metric.                     |

The endpoint can also be set with the `FOGWALL_OTEL_ENDPOINT` environment variable; every key follows the standard
[environment-variable mapping](environment-variables.md).

## What is exported

**Trace** — a per-request parent span for each push that arrives over HTTP (server mode and transparent proxy). If the
client sends a W3C `traceparent` header, the span continues that trace. SSH server-mode pushes do not pass through the
servlet container and so have no parent span.

**Metrics**

| Instrument               | Type          | Attributes            | Notes                                                    |
| ------------------------ | ------------- | --------------------- | -------------------------------------------------------- |
| `fogwall.push.active`    | up-down gauge | `mode`                | Pushes in flight through the HTTP proxy.                 |
| `fogwall.push.duration`  | histogram (s) | `mode`, `provider`    | Wall-clock duration of an HTTP push request.             |
| `fogwall.push.decisions` | counter       | `provider`, `status`  | Pushes by final decision (FORWARDED, REJECTED, ERROR …). |
| `fogwall.push.forward`   | counter       | `provider`, `outcome` | Upstream forward attempts, success or failure.           |

`mode` is `server` or `proxy`. Decision and forward counts are transport-agnostic and so cover SSH pushes too; the
duration and in-flight gauge are HTTP-only. Repository slug is recorded on spans only, never on a metric, to keep metric
cardinality bounded.

## Logs

When observability is on and a request is in a span, the log layout prints `trace_id` and `span_id` after the thread
field, so a log line can be pivoted straight to its trace. When it is off, those fields are omitted and the log format
is unchanged.
