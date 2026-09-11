# Logging

## Default log locations

| Environment         | Log output                                          |
| ------------------- | --------------------------------------------------- |
| `./gradlew run`     | `fogwall-server/logs/application.log` + console     |
| Docker / production | console only (stdout); redirect or use a log driver |

The default Log4j2 config logs `com.rbc.fogwall` at `DEBUG` and everything else at `INFO`.

## Enabling debug logging for specific subsystems

Override the bundled `log4j2.xml` at runtime — no rebuild required:

```bash
# Local run
JAVA_TOOL_OPTIONS=-Dlog4j2.configurationFile=/path/to/my-log4j2.xml \
  ./gradlew :fogwall-dashboard:run

# Docker
volumes:
  - ./my-log4j2.xml:/app/conf/log4j2.xml:ro
environment:
  JAVA_TOOL_OPTIONS: -Dlog4j2.configurationFile=/app/conf/log4j2.xml
```

## Debug profiles by problem area

### OIDC / Spring Security authentication failures

`docker/log4j2-debug.xml` is included for this. Activate it in Docker Compose:

```yaml
volumes:
  - ./docker/log4j2-debug.xml:/app/conf/log4j2-debug.xml:ro
environment:
  JAVA_TOOL_OPTIONS: -Dlog4j2.configurationFile=/app/conf/log4j2-debug.xml
```

This enables `DEBUG` on `org.springframework.security` and `org.springframework.web.client`. Remove it when done — it is
very chatty.

### JGit HTTP transport (upstream push/fetch failures)

Add to your `log4j2.xml`:

```xml
<Logger name="org.eclipse.jgit" level="DEBUG"/>
<Logger name="org.eclipse.jgit.http.server" level="DEBUG"/>
<Logger name="org.eclipse.jgit.transport" level="DEBUG"/>
```

Produces detailed output for each step of the JGit credential negotiation and pack transfer. Useful when a push reaches
the proxy but fails forwarding to upstream.

### Jetty request handling (incoming connections, servlet dispatch)

```xml
<Logger name="org.eclipse.jetty" level="DEBUG"/>
<Logger name="org.eclipse.jetty.server" level="DEBUG"/>
<Logger name="org.eclipse.jetty.http" level="DEBUG"/>
```

### Upstream HTTP client (transparent proxy mode)

```xml
<Logger name="org.eclipse.jetty.client" level="DEBUG"/>
```

Logs each HTTP request and response made by Jetty's `ProxyServlet` to the upstream. Useful when the transparent proxy
path (`/proxy/`) fails to reach the upstream.

## Administrative action logging

Every mutating dashboard REST endpoint — user create/delete/password reset/email and SCM identity changes, group
create/delete/membership/rule changes, permission grants and revocations, access rule changes, cache invalidation —
emits one `INFO`-level line to the application log:

```
admin_action actor=<login> action=<user.create|group.delete|permission.grant|...> target=<resource> outcome=<SUCCESS|DENIED> [detail=<...>]
```

`outcome=DENIED` covers refusals such as an admin trying to delete the last remaining admin account or modify a
config-defined group. Password resets log that a reset happened, never the new value. Read endpoints are not logged.
This is the operational history of who changed what through the dashboard; push and SCM API proposal records remain
separately in the database as evidence about proxy traffic. Grep the application log for `admin_action` to filter this
stream from everything else.

## Reading logs for a failed push

Each push gets a `requestId` in the MDC (visible in the `[%X{requestId}]` field in the log pattern). To follow a single
push through the log:

```bash
grep "your-request-id" logs/application.log
```

The `requestId` is also printed in the sideband output to the git client, so you can match terminal output to log lines.

When OpenTelemetry is enabled, each log line emitted inside a request span also carries `trace_id` and `span_id`, so a
log entry can be pivoted straight to its trace in the collector. Those fields are omitted (and the log format is
unchanged) when observability is off. See [Observability](../configuration/observability.md) for enabling it and the
full list of exported traces and metrics.

## Git client output formatting

Two environment variables control the `remote:` sideband messages sent to git clients during a push:

| Variable           | Effect                                                                                                          |
| ------------------ | --------------------------------------------------------------------------------------------------------------- |
| `NO_COLOR`         | Disables ANSI colour. Follows the [no-color.org](https://no-color.org) convention — set to any non-empty value. |
| `FOGWALL_NO_EMOJI` | Replaces emoji (✅ ❌ ⛔ 🔑) with plain ASCII. Useful for CI systems or terminals that do not render Unicode.   |

Set on the server process, not on the client. See
[Git client output](../configuration/running-and-logging.md#git-client-output) for Docker Compose examples.
