# Environment variable overrides

Strip the `FOGWALL_` prefix, lowercase, and replace `_` with `.` to get the config path.

| Environment Variable                      | Config path                        | Example                                 |
| ----------------------------------------- | ---------------------------------- | --------------------------------------- |
| `FOGWALL_CONFIG_PROFILES`                 | _(meta — not a config key)_        | `docker-default,ldap`                   |
| `FOGWALL_SERVER_PORT`                     | `server.port`                      | `9090`                                  |
| `FOGWALL_SERVER_APPROVAL_MODE`            | `server.approvalMode`              | `ui`                                    |
| `FOGWALL_SERVER_SERVICE_URL`              | `server.serviceUrl`                | `https://fogwall.example.com/dashboard` |
| `FOGWALL_DATABASE_TYPE`                   | `database.type`                    | `postgres`                              |
| `FOGWALL_DATABASE_URL`                    | `database.url`                     | `jdbc:postgresql://...`                 |
| `FOGWALL_DATABASE_HOST`                   | `database.host`                    | `db.internal`                           |
| `FOGWALL_DATABASE_POOL_MAXIMUMPOOLSIZE`   | `database.pool.maximum-pool-size`  | `3`                                     |
| `FOGWALL_DATABASE_POOL_MINIMUMIDLE`       | `database.pool.minimum-idle`       | `1`                                     |
| `FOGWALL_DATABASE_POOL_CONNECTIONTIMEOUT` | `database.pool.connection-timeout` | `30000`                                 |
| `FOGWALL_SERVER_SESSIONSTORE`             | `server.session-store`             | `jdbc`                                  |
| `FOGWALL_SERVER_REDIS_HOST`               | `server.redis.host`                | `redis.cluster.local`                   |
| `FOGWALL_SERVER_REDIS_PORT`               | `server.redis.port`                | `6379`                                  |
| `FOGWALL_SERVER_ALLOWEDORIGINS`           | `server.allowed-origins`           | `https://dashboard.example.com`         |
| `FOGWALL_PROVIDERS_GITHUB_ENABLED`        | `providers.github.enabled`         | `false`                                 |
| `FOGWALL_PROVIDERS_<NAME>_URI`            | `providers.<name>.uri`             | `https://gitlab.corp.com`               |

> Complex nested structures (URL rules, full commit validation blocks) are not overridable via env vars. Use YAML
> profile files instead.

## Hyphenated keys and provider names

_Available since v1.3.0._

The single-underscore rule above can't produce a hyphen — there's no way to write `providers.gitea-ssh.api-token` as an
env var name if every `_` is a path separator. For a config key or a provider name that itself contains a hyphen, use
the double-underscore convention instead (same idea as systemd, Kubernetes, and Docker Compose): `__` is the path
separator, and a lone `_` becomes a hyphen.

| Environment Variable                      | Config path                     |
| ----------------------------------------- | ------------------------------- |
| `FOGWALL_PROVIDERS__GITEA_SSH__API_TOKEN` | `providers.gitea-ssh.api-token` |
| `FOGWALL_SERVER__SESSION_STORE`           | `server.session-store`          |

This only activates when the env var name contains `__` — every existing single-underscore example above still works
unchanged, since none of them contain `__`.
