# Externalized configuration

fogwall follows 12-factor config principles: the application ships with safe defaults baked into the JAR, and operators
layer environment-specific values on top without modifying the image.

Both `fogwall-server` and `fogwall-dashboard` load config through [Gestalt](https://gestalt-config.github.io/gestalt/),
a lightweight Java config library rather than Spring's `@ConfigurationProperties`/`Environment` stack — same reasoning
as [not using Spring Boot](../architecture/deployment-modes.md#proxy--dashboard-fogwall-dashboard) — fogwall needs
config loading to work identically in `fogwall-server`, which has no Spring on its classpath at all. Gestalt is a much
smaller dependency that covers the same core need (typed config binding, layered sources, environment variable
overrides) without pulling in a DI container. The profile mechanism below is directly modeled on Spring profiles — the
concept of named, composable config overlays activated by name is worth keeping even without the rest of Spring's config
machinery.

## How config is loaded

Sources are merged in priority order (lowest → highest):

| Priority    | Source                                                  | Mechanism                                |
| ----------- | ------------------------------------------------------- | ---------------------------------------- |
| 1 (lowest)  | `fogwall.yml`                                           | Bundled in the JAR — base defaults       |
| 2           | Profile YAMLs named in `FOGWALL_CONFIG_PROFILES`        | Classpath lookup (see below)             |
| 3           | `FOGWALL_*` environment variables                       | Strip prefix, lowercase, `_` → `.`       |
| 4 (highest) | Hot-reload overlay (`reload.file.path` or `reload.git`) | Filesystem path; applied on every reload |

A higher-priority source only overrides the specific keys it defines — other base values are preserved.

## Profile-based config files — the `/app/conf/` pattern

The Docker image prepends `/app/conf/` to the JVM classpath. Any YAML file mounted there is treated as a classpath
resource and loaded automatically when its profile is activated.

**Step 1 — Mount the file:**

```yaml
# docker-compose.yml or Kubernetes pod spec
volumes:
  - ./my-config.yml:/app/conf/fogwall-my-config.yml:ro
# Or in Kubernetes, mount a ConfigMap:
# - name: fogwall-config
#   mountPath: /app/conf
```

**Step 2 — Activate the profile:**

```yaml
environment:
  FOGWALL_CONFIG_PROFILES: my-config
```

The loader looks for `fogwall-{profile}.yml` on the classpath. With `/app/conf/` prepended, your mounted file is found
first.

<!-- prettier-ignore-start -->
> [!IMPORTANT]
> A file mounted at `/app/conf/` is silently ignored unless the matching profile name is set in `FOGWALL_CONFIG_PROFILES`. There is no auto-discovery — the profile name is the activation key.
<!-- prettier-ignore-end -->

**Multiple profiles** are comma-separated; later profiles take priority over earlier ones:

```
FOGWALL_CONFIG_PROFILES=docker-default,ldap
```

This loads `fogwall-docker-default.yml` then `fogwall-ldap.yml`; `ldap` wins on any key both files define.

<!-- prettier-ignore-start -->
> [!WARNING]
> **List merge caveat:** Gestalt replaces lists at the key level — it does not append. If two profile files both define `permissions:`, the later file's list replaces the earlier one entirely. Keep all entries for a given list key in a single profile file. A common split that avoids this: one profile for organizational config (users, permissions, rules) and a second for environment-specific connectivity (auth provider URL, database, TLS) which never defines list keys.
<!-- prettier-ignore-end -->

## Environment variable overrides

Any `FOGWALL_` prefixed env var overrides the equivalent config key at the highest priority (above profiles, below
hot-reload overlays). The mapping is: strip `FOGWALL_`, lowercase, replace `_` with `.`:

```
FOGWALL_SERVER_PORT=9090              → server.port
FOGWALL_DATABASE_TYPE=postgres        → database.type
FOGWALL_SECRET__SCAN_ENABLED=false   → secret-scan.enabled
```

Use env vars for values that differ per-environment (secrets, hostnames, ports) and profile YAML files for structural
config (users, permissions, rules) that is too complex to express as a flat key-value pair.

## Hot-reload overlay

The `reload:` block configures a separate high-priority overlay that is re-read at runtime without restarting the
server. See [Hot reload](../configuration/hot-reload.md) for the full reference.

The overlay file path can be a ConfigMap mount too:

```yaml
reload:
  file:
    enabled: true
    path: /app/conf/fogwall-runtime.yml
```

This lets operations teams push rule or permission changes by updating a ConfigMap and triggering
`POST /api/config/reload` — no pod restart needed.
