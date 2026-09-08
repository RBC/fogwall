# Running and logging

## Running

```bash
# Proxy only (no dashboard):
./gradlew :fogwall-server:run

# Proxy + dashboard + REST API:
./gradlew :fogwall-dashboard:run

# Override port via environment variable:
FOGWALL_SERVER_PORT=9090 ./gradlew :fogwall-server:run
```

Logs: `fogwall-server/logs/application.log`

---

## Logging

fogwall uses Log4j2 for logging. To override the bundled config without rebuilding the image, mount a custom
`log4j2.xml` and point the JVM at it:

```bash
# Local run
JAVA_TOOL_OPTIONS=-Dlog4j2.configurationFile=/path/to/log4j2.xml ./gradlew :fogwall-dashboard:run

# Docker — mount your config and set the env var
volumes:
  - ./my-log4j2.xml:/app/conf/log4j2.xml:ro
environment:
  JAVA_TOOL_OPTIONS: -Dlog4j2.configurationFile=/app/conf/log4j2.xml
```

`JAVA_TOOL_OPTIONS` is read directly by the JVM, so it works regardless of how the application is launched.

A ready-made debug config (`docker/log4j2-debug.xml`) is included for diagnosing OIDC and Spring Security issues — it
enables `DEBUG` on `org.springframework.security` and `org.springframework.web.client`. See the comments in that file
for how to activate it.

---

## Git client output

fogwall sends validation results and status messages to the git client via sideband (the `remote:` lines visible during
a push). Two environment variables control the formatting of these messages:

| Variable           | Effect                                                                                                                                                            |
| ------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `NO_COLOR`         | Disables ANSI colour in sideband output. Follows the [no-color.org](https://no-color.org) convention — set to any value to disable.                               |
| `FOGWALL_NO_EMOJI` | Replaces emoji symbols (✅ ❌ ⛔ 🔑 etc.) with plain ASCII equivalents. Useful when pushing through terminals or CI systems that do not render Unicode correctly. |

Both are read at runtime from the server's environment — no restart is required if set before the process starts, but
they cannot be changed while the server is running.

```bash
# Docker Compose — add to the fogwall service environment block
environment:
  NO_COLOR: "1"
  FOGWALL_NO_EMOJI: "1"
```
