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
| `enabled`             | `false`   | Master switch. When off, nothing is exported and the proxy paths carry no instrumentation. |
| `endpoint`            | _(blank)_ | OTLP collector endpoint. Blank uses the exporter default for the chosen protocol.          |
| `protocol`            | `grpc`    | OTLP transport: `grpc` or `http` (OTLP/HTTP + protobuf).                                   |
| `service-name`        | `fogwall` | The `service.name` resource attribute. `service.version` is filled from the build.         |
| `tracing.enabled`     | `true`    | Emit spans (only when `enabled` is also true).                                             |
| `metrics.enabled`     | `true`    | Emit metrics (only when `enabled` is also true).                                           |
| `resource-attributes` | _(none)_  | Extra key/value resource attributes merged onto every span and metric.                     |

The endpoint can also be set with the `FOGWALL_OTEL_ENDPOINT` environment variable; every key follows the standard
[environment-variable mapping](environment-variables.md).

## What is exported

**Trace** — a per-request parent span for each push that arrives over HTTP (server mode and transparent proxy) and for
each SCM API proxy request (the `gh`/`glab`/`fj`/`tea` proposal flows). If the client sends a W3C `traceparent` header,
the span continues that trace. SSH server-mode pushes do not pass through the servlet container and so have no parent
span. An SCM API request also gets a child `CLIENT` span for fogwall's outbound forward to the provider, carrying the
upstream `http.response.status_code` and that leg's own timing — fogwall cannot propagate the trace into the provider,
but it measures the call from its own side.

**Metrics**

| Instrument                | Type          | Attributes                         | Notes                                                    |
| ------------------------- | ------------- | ---------------------------------- | -------------------------------------------------------- |
| `fogwall.push.active`     | up-down gauge | `mode`                             | Pushes in flight through the HTTP proxy.                 |
| `fogwall.push.duration`   | histogram (s) | `mode`, `provider`                 | Wall-clock duration of an HTTP push request.             |
| `fogwall.push.decisions`  | counter       | `provider`, `status`               | Pushes by final decision (FORWARDED, REJECTED, ERROR …). |
| `fogwall.push.forward`    | counter       | `provider`, `outcome`              | Upstream forward attempts, success or failure.           |
| `fogwall.scmapi.duration` | histogram (s) | `provider`, `operation`            | Wall-clock duration of an SCM API proxy request.         |
| `fogwall.scmapi.actions`  | counter       | `provider`, `operation`, `outcome` | SCM API mutations and refusals by operation and outcome. |

`mode` is `server` or `proxy`. Decision and forward counts are transport-agnostic and so cover SSH pushes too; the push
duration and in-flight gauge are HTTP-only. `operation` is a provider-agnostic name where the operation flattens cleanly
— `proposal.create`, `proposal.update`, `proposal.merge`, `issue.create`, `comment.create`, … (`proposal` covers a
GitHub/Forgejo pull request and a GitLab merge request) — and the dialect's own operation name where it does not, such
as a label or assignee edit whose subject the raw op does not name. A read is classified by resource too — `issue.read`,
`proposal.read`, `comment.read`, or the GraphQL query's root field (`repository.read`), with `other.read` for anything
unrecognized — so a read names what was read, not a bare `read`; an action with no operation is `unknown`. `outcome` is
the action's result (`FORWARDED`, `DENIED`, `REJECTED`, `ERROR`). Reads are traced and timed but not counted in
`fogwall.scmapi.actions`, which tracks mutations and refusals to match the audit trail. Repository slug is recorded on
spans only, never on a metric, to keep metric cardinality bounded.

## Logs

When observability is on and a request is in a span, the log layout prints `trace_id` and `span_id` after the thread
field, so a log line can be pivoted straight to its trace. When it is off, those fields are omitted and the log format
is unchanged.
