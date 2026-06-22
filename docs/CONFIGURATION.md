# Configuration Reference

fogwall uses layered YAML configuration merged at startup. A base file ships with the jar; additional profile files and
environment variable overrides are applied on top in a defined order.

## Configuration files and profiles

### Load order (lowest → highest priority)

| Layer | Source                              | When loaded                                          |
| ----- | ----------------------------------- | ---------------------------------------------------- |
| 1     | `fogwall.yml`                       | Always — base defaults bundled in the jar            |
| 2     | `fogwall-{profile}.yml`             | For each profile listed in `FOGWALL_CONFIG_PROFILES` |
| 3     | Environment variables (`FOGWALL_*`) | Always — highest priority                            |

### `FOGWALL_CONFIG_PROFILES`

Set this environment variable to a comma-separated list of profile names. For each name, fogwall looks for
`fogwall-{name}.yml` on the classpath (including any files mounted into `/app/conf/` in Docker). Unknown or missing
profile files are silently skipped.

```bash
# Local development — loads fogwall-local.yml
FOGWALL_CONFIG_PROFILES=local

# Docker with LDAP auth — loads fogwall-docker-default.yml then fogwall-ldap.yml
FOGWALL_CONFIG_PROFILES=docker-default,ldap

# Docker with OIDC auth and PostgreSQL
FOGWALL_CONFIG_PROFILES=docker-default,oidc
# (postgres settings come from FOGWALL_DATABASE_* env vars, no profile file needed)
```

Later profiles take priority over earlier ones. All profiles take priority over `fogwall.yml`. Environment variables
override everything.

### Bundled profiles

| Profile name     | File                         | Purpose                                                   |
| ---------------- | ---------------------------- | --------------------------------------------------------- |
| `local`          | `fogwall-local.yml`          | Local development: dev users, Vite CORS, test allow rules |
| `docker-default` | `fogwall-docker-default.yml` | Docker base: admin user, Gitea provider, validation rules |
| `ldap`           | `fogwall-ldap.yml`           | LDAP authentication config (used with `docker-default`)   |
| `oidc`           | `fogwall-oidc.yml`           | OIDC authentication config (used with `docker-default`)   |

> When running via `./gradlew run`, `FOGWALL_CONFIG_PROFILES=local` is set automatically. In Docker, set it explicitly
> via the Compose file or your deployment config.

### Docker Compose

The Docker Compose setup uses overlay files to compose the stack. See
[docker-compose.ldap.yml](../docker-compose.ldap.yml) and [docker-compose.oidc.yml](../docker-compose.oidc.yml) for
examples of how profiles are combined.

```bash
# Default (local auth, h2 database)
docker compose up -d

# LDAP auth
docker compose -f docker-compose.yml -f docker-compose.ldap.yml up -d

# OIDC auth + PostgreSQL
docker compose --profile postgres \
  -f docker-compose.yml -f docker-compose.oidc.yml -f docker-compose.postgres.yml up -d
```

## Environment variable overrides

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

## Server settings

```yaml
server:
  port: 8080

  # Approval mode for store-and-forward pushes:
  #   auto       — approves every clean push immediately (default; no dashboard required)
  #   ui         — waits for a human reviewer via the REST API
  #   servicenow — delegates to a ServiceNow approval workflow
  # Note: FogwallDashboardApplication always uses 'ui' regardless of this setting.
  approval-mode: auto

  # Sideband keepalive interval in seconds for store-and-forward operations.
  # Sends periodic progress packets to prevent idle-timeout disconnects during
  # long steps (secret scanning, approval polling). Set to 0 to disable.
  heartbeat-interval-seconds: 10

  # Base URL of the dashboard, used in links sent to clients via sideband messages.
  # Should include the /dashboard path prefix.
  # Defaults to http://localhost:<port>/dashboard if not set.
  # service-url: https://fogwall.internal.example.com/dashboard

  # When false (default), any authenticated user may review any push they did not push
  # themselves. Set to true to require an explicit REVIEW permission entry for the repo.
  # Use true for deployments that need restricted approvers with formal sign-off.
  require-review-permission: false

  # Origins allowed to make cross-origin requests to the dashboard REST API.
  # Required when the frontend is served from a different hostname than the backend
  # (e.g. Vite dev server on port 5173, or a load-balanced dashboard behind a different host).
  # Default (empty): same-origin only.
  # allowed-origins:
  #   - http://localhost:5173
  #   - https://dashboard.example.com

  # HTTP session persistence backend. Controls where authenticated sessions are stored.
  # Options:
  #   none   — in-memory (default); sessions lost on restart, not shared across pods
  #   jdbc   — persisted to the configured JDBC database; zero new infrastructure required
  #   mongo   — persisted to the configured MongoDB database; zero new infrastructure required
  #   redis  — persisted to a Redis or Valkey instance; configure via server.redis.*
  # Use jdbc or redis for multi-instance deployments so sessions survive pod restarts
  # and remain valid across all replicas.
  # session-store: none

  # Redis connection — only required when session-store: redis
  # redis:
  #   host: redis.cluster.local
  #   port: 6379
  #   password: ""
  #   ssl: false
```

### Session persistence for multi-instance deployments

By default, authenticated sessions are stored in memory. This works for single-instance deployments but means:

- Sessions are lost when a pod restarts
- A user hitting a different pod after a load balancer switch will be logged out

Set `server.session-store` to persist sessions across restarts and share them between replicas.

**JDBC (recommended — zero new infrastructure):**

```yaml
server:
  session-store: jdbc

database:
  type: postgres
  url: jdbc:postgresql://db.internal:5432/fogwall
  pool:
    maximum-pool-size: 3
    minimum-idle: 1
```

The session tables (`SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES`) are created automatically by the database migrator on
first startup. No manual DDL required.

**Redis / Valkey:**

```yaml
server:
  session-store: redis
  redis:
    host: redis.cluster.local # or valkey.cluster.local
    port: 6379
    password: "" # omit if no auth configured
    ssl: false # set true for TLS-secured Redis
```

A minimal single-replica Redis or Valkey pod is sufficient — sessions are small and low-throughput. No persistence or
clustering required for this use case.

**MongoDB:**

```yaml
server:
  session-store: mongo

database:
  type: mongo
  url: mongodb://fogwall:secret@mongo.internal:27017/fogwall
```

Sessions are stored in the `proxy_sessions` collection alongside the other `proxy_*` collections. A TTL index on
`expireAt` lets MongoDB expire idle sessions server-side — no background cleanup task runs in the proxy. The session
store reuses the same connection pool as the rest of the MongoDB-backed stores, so no extra configuration is needed.
Requires `database.type: mongo`.

## TLS

### Server HTTPS listener

By default fogwall listens on plain HTTP. To enable HTTPS, add a `server.tls` block.

**PEM-based (preferred — no keytool required):**

```yaml
server:
  tls:
    port: 8443
    certificate: /etc/fogwall/tls/server.pem # X.509 certificate or chain, PEM
    key: /etc/fogwall/tls/server-key.pem # PKCS8 private key, unencrypted PEM
```

The private key must be in PKCS8 format. Convert a PKCS1 key with:

```bash
openssl pkcs8 -topk8 -nocrypt -in server.pem -out server-key.pem
```

**Keystore-based (for shops with existing managed keystores):**

```yaml
server:
  tls:
    port: 8443
    keystore:
      path: /etc/fogwall/tls/keystore.p12
      password: changeit
      type: PKCS12 # or JKS
```

Plain HTTP on `server.port` remains active when HTTPS is configured — both listeners run concurrently.

### Custom upstream CA trust

Enterprise PKIs typically issue certificates that Java's built-in truststore doesn't include, causing
`SSLHandshakeException` on upstream connections to internal GitLab/Bitbucket/Forgejo instances. fogwall supports
trusting a custom CA bundle without touching the JVM truststore or running `keytool`.

```yaml
server:
  tls:
    trust-ca-bundle: /etc/fogwall/tls/internal-ca.pem
```

The PEM file may contain one or more `-----BEGIN CERTIFICATE-----` blocks (a full CA chain is fine). Custom CAs are
merged with the JVM's built-in trust anchors — public hosts (GitHub, GitLab SaaS, Bitbucket Cloud) continue to work
without any changes.

This applies to both proxy modes:

- **Transparent proxy** — Jetty's `HttpClient` used for upstream forwarding
- **Store-and-forward** — JGit's HTTP transport used for forwarding after local receipt

## Database

```yaml
database:
  type: h2-mem # h2-mem | h2-file | postgres | mongo
```

### Database backends

| Type       | Description            | Extra keys                                                           |
| ---------- | ---------------------- | -------------------------------------------------------------------- |
| `h2-mem`   | H2 in-memory (default) | `name` (default: `fogwall`)                                          |
| `h2-file`  | H2 persisted to disk   | `path` (default: `./.data/fogwall`)                                  |
| `sqlite`   | SQLite file            | `path` (default: `./.data/fogwall.db`)                               |
| `postgres` | PostgreSQL             | `url` **or** `host`, `port`, `name`, `username`, `password`          |
| `mongo`    | MongoDB                | `url` (required); `name` optional if the database is in the URI path |

For both `postgres` and `mongo`, setting `url` to a full connection string is the recommended approach when you need
driver-specific options (TLS, SSL certificates, connection parameters) that are not exposed as individual config fields.

### Connection pool tuning

Applies to all JDBC backends (`h2-mem`, `h2-file`, `postgres`). The default pool size is deliberately small — git push
workloads are sequential per user, so a large pool buys nothing and drives up aggregate connection counts when multiple
instances share a database. The
[HikariCP pool sizing guide](https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing) covers this in depth.

```yaml
database:
  type: postgres
  url: jdbc:postgresql://db.internal:5432/fogwall
  pool:
    maximum-pool-size: 3 # per-instance connections; multiply by instance count for total DB load
    minimum-idle: 1 # release idle connections; omit to keep the full pool warm
    connection-timeout: 30000 # ms; fail fast if the pool is exhausted
    idle-timeout: 600000 # ms; retire connections after 10 min idle
    max-lifetime: 1800000 # ms; rotate connections every 30 min
```

For deployments sharing a database across multiple instances (multiple environments, blue/green, canary), set
`maximum-pool-size` to the lowest value that keeps p99 push latency acceptable. For most proxy workloads, 2–5
connections per instance is sufficient.

```yaml
# Postgres — individual fields
database:
  type: postgres
  host: db.internal
  port: 5432
  name: fogwall
  username: fogwall
  password: secret

# Postgres — connection string (use this for sslmode, certificates, etc.)
database:
  type: postgres
  url: jdbc:postgresql://db.internal:5432/fogwall?sslmode=verify-full&sslrootcert=/certs/ca.crt
  username: fogwall
  password: secret

# Mongo — connection string (name extracted from URI path)
database:
  type: mongo
  url: mongodb://fogwall:secret@mongo.internal:27017/fogwall?tls=true&tlsCAFile=/certs/ca.crt

# Mongo — connection string with separate name field
database:
  type: mongo
  url: mongodb://fogwall:secret@mongo.internal:27017
  name: fogwall
```

### SCM token identity cache

When users are configured, fogwall caches successful token-to-username resolutions so that repeated pushes from the same
PAT do not incur a provider API call every time. The cache is backed by the configured database (JDBC or MongoDB) and
keyed on a SHA-512 digest of the token — the raw token is never stored.

| Variable                         | Default | Effect                                                                                                                               |
| -------------------------------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| `FOGWALL_SCM_CACHE_MAX_AGE_DAYS` | `7`     | Maximum age of a cache entry in days. Entries older than this are ignored on read and overwritten on the next successful resolution. |

Token rotation is handled automatically: a new PAT produces a new cache key, so the old entry simply ages out. There is
no need to flush the cache manually when tokens are rotated.

### MongoDB: coexisting with the upstream Node.js git-proxy

If you are migrating from [finos/git-proxy](https://github.com/finos/git-proxy) (the Node.js implementation) and
pointing this proxy at a database that previously held its data, the two applications use incompatible document schemas.
The safest path is to **provision a new MongoDB database** (e.g. `fogwall`) and point `database.url` at it. This avoids
all collision risk and keeps indexes, backups, and ops tooling cleanly separated.

If provisioning a separate database is not feasible, this proxy now uses collection names that do not collide with the
upstream Node.js implementation:

| Collection         | Written by                 | Notes                                                              |
| ------------------ | -------------------------- | ------------------------------------------------------------------ |
| `proxy_users`      | `MongoUserStore`           | Renamed from `users` to avoid collision with upstream's `users`.   |
| `proxy_pushes`     | `MongoPushStore`           | Renamed from `pushes` to avoid collision with upstream's `pushes`. |
| `repo_permissions` | `MongoRepoPermissionStore` | No upstream equivalent.                                            |
| `access_rules`     | `MongoUrlRuleRegistry`     | No upstream equivalent.                                            |
| `fetch_records`    | `MongoFetchStore`          | No upstream equivalent.                                            |

This means you _can_ point both apps at the same MongoDB database without corrupting each other's data. We still
recommend separate databases for operational clarity — shared databases make backups, restores, and index tuning harder
to reason about — but it is no longer a correctness hazard. Starting with 1.0.0, these collection names are part of the
project's stability contract and will not be renamed without an in-place migration path.

## Authentication

The dashboard supports four authentication providers, selected via `auth.provider`.

```yaml
auth:
  provider: local # local | ldap | ad | oidc (default: local)

  # Maximum idle time before a session expires and the user must re-authenticate.
  # Default: 86400 (24 hours). Tighten to 28800 (8 hours) or less for compliance environments.
  session-timeout-seconds: 86400
```

### Local (default)

Usernames and BCrypt password hashes are defined directly in the `users:` block. See the [Users](#users) section.

### LDAP

Authenticates users against a generic LDAP directory using a bind operation.

```yaml
auth:
  provider: ldap
  ldap:
    # LDAP server URL including base DN.
    url: ldap://ldap.example.com:389/dc=example,dc=com

    # User DN pattern — {0} is substituted with the login username.
    user-dn-patterns: cn={0},ou=users

    # Optional bind credentials for group search / attribute lookup.
    bind-dn: cn=admin,dc=example,dc=com
    bind-password: secret

    # Base DN (relative to url base) to search for group membership.
    # When set, group names are mapped to roles via auth.role-mappings below.
    group-search-base: ou=groups

    # LDAP filter for group membership. {0} = user full DN, {1} = username.
    group-search-filter: "(member={0})"

  # Map fogwall role names to lists of LDAP group CNs.
  # When a user is a member of any listed group, the role is granted.
  role-mappings:
    ADMIN:
      - git-admins
      - security-team
```

### Active Directory

Authenticates users against an on-premises Active Directory domain using UPN bind (`user@domain.com`). Unlike the
generic LDAP provider, no `user-dn-patterns` is required — Spring Security constructs the UPN automatically from the
`domain` and the submitted username.

```yaml
auth:
  provider: ad
  ad:
    # AD domain name — used to form user@domain UPN for bind.
    domain: corp.example.com

    # Domain controller URL. When omitted, Spring Security resolves the DC via DNS SRV records.
    url: ldap://dc.corp.example.com:389

    # Optional: base DN for group search. When set, group membership is used for role mapping.
    group-search-base: DC=corp,DC=example,DC=com

    # LDAP filter for group membership. {0} = user full DN.
    group-search-filter: "(member={0})"

  role-mappings:
    ADMIN:
      - CN=git-admins,OU=Groups,DC=corp,DC=example,DC=com
```

<!-- prettier-ignore-start -->
> [!TIP]
> The AD provider understands Active Directory error sub-codes on bind failure 49 (expired passwords, locked accounts, etc.) and maps them to specific Spring Security exceptions.
<!-- prettier-ignore-end -->

### OIDC

Authenticates users via OpenID Connect authorization code flow (Keycloak, Okta, Entra ID, Dex, etc.).

```yaml
auth:
  provider: oidc
  oidc:
    # OIDC issuer URI — Spring Security fetches {issuerUri}/.well-known/openid-configuration at startup.
    issuer-uri: https://accounts.example.com

    client-id: fogwall-client
    client-secret: fogwall-secret

    # Optional: path to a PKCS#8 PEM RSA private key for private_key_jwt client auth.
    # When set, client-secret is not required.
    # private-key-path: /run/secrets/fogwall-oidc-private-key.pem

    # Optional: path to the X.509 certificate (PEM) matching private-key-path.
    # Required for Entra ID — Entra matches registered certificates by x5t thumbprint, not kid.
    # Without this, every token exchange fails with AADSTS700027.
    # Generate: openssl req -new -x509 -key private.pem -out cert.pem -days 365
    # cert-path: /run/secrets/fogwall-oidc-cert.pem

    # Optional: explicit kid to embed in the private_key_jwt assertion header.
    # Use this for providers that match the assertion against a registered JWKS by kid
    # (Keycloak, Okta, Auth0, Dex). Without it, a random UUID kid is generated on each
    # restart, which breaks authentication with those providers.
    # Find the kid by inspecting your provider's JWKS endpoint and matching it to the
    # public key you registered.
    # Not needed when cert-path is set (Entra ID uses x5t instead of kid).
    # key-id: my-registered-kid

  # OIDC claim containing the user's group memberships. Defaults to "groups",
  # which is standard for Keycloak, Okta, and most Entra ID configurations.
  groups-claim: groups

  # Map fogwall role names to lists of OIDC group values from the claim above.
  role-mappings:
    ADMIN:
      - git-admins
```

#### Entra ID (Azure AD)

Entra ID requires two extra settings. The `jwk-set-uri` field is the key signal — when it is set, fogwall skips OIDC
discovery and issuer validation. This is necessary because Entra issues tokens with
`iss=https://sts.windows.net/{tenant}/` rather than the discovery base URL, which would cause Spring Security to reject
them otherwise.

```yaml
auth:
  provider: oidc
  oidc:
    issuer-uri: https://login.microsoftonline.com/{tenant-id}/v2.0
    client-id: <app-registration-client-id>
    client-secret: <client-secret>

    # Required for Entra ID — triggers issuer-validation bypass.
    jwk-set-uri: https://login.microsoftonline.com/{tenant-id}/discovery/v2.0/keys

  # Requires "Group claims" to be enabled in the app registration (Token configuration → Groups claim).
  # Group values will be object IDs (GUIDs) unless "Group names" is selected in the manifest.
  groups-claim: groups

  role-mappings:
    ADMIN:
      - <object-id-of-admin-group>
```

<!-- prettier-ignore-start -->
> [!IMPORTANT]
> **App registration checklist:**
> 1. Platform: Web — redirect URI `https://<your-host>/login/oauth2/code/fogwall`
> 2. API permissions: `openid`, `profile`, `email` (delegated)
> 3. Token configuration → add Groups claim → select "Security groups"
<!-- prettier-ignore-end -->

### Role mappings

`auth.role-mappings` applies to LDAP, AD, and OIDC. Keys are role names (without the `ROLE_` prefix); values are lists
of group names or claim values from the IdP. `ROLE_USER` is always granted to every authenticated user.

| Role    | Dashboard access                                                               |
| ------- | ------------------------------------------------------------------------------ |
| `USER`  | View and act on pushes awaiting approval                                       |
| `ADMIN` | All USER permissions + create/delete users, reset passwords, manage identities |

## Providers

Providers define the upstream Git hosting services the proxy routes to.

```yaml
providers:
  # Reserved names — provider type and default URI are built in
  github:
    enabled: true # → github.com
  gitlab:
    enabled: true # → gitlab.com
  bitbucket:
    enabled: true # → bitbucket.org
  codeberg:
    enabled: true # → codeberg.org
  gitea:
    enabled: true # → gitea.com

  # Custom-named providers — 'type' and 'uri' are both required
  my-internal-server:
    enabled: true
    type: github # uses GitHubProvider (identity resolution, GHES API path logic, etc.)
    uri: https://github.corp.example.com

  my-forgejo:
    enabled: true
    type: forgejo # ForgejoProvider; uri is required (forgejo has no canonical public host)
    uri: https://forge.internal.example.com

  acme-bitbucket:
    enabled: true
    type: bitbucket
    uri: https://bitbucket.acme.com
```

### Provider properties

| Property       | Type    | Default              | Description                                                                                                                                                 |
| -------------- | ------- | -------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `enabled`      | boolean | `true`               | Whether the provider is active                                                                                                                              |
| `servlet-path` | string  | `""`                 | Additional URL prefix for this provider                                                                                                                     |
| `uri`          | string  | _(built-in default)_ | Upstream base URI. Required for custom-named providers; omit for built-ins.                                                                                 |
| `type`         | string  | _(from name)_        | Provider implementation: `github`, `gitlab`, `bitbucket`, `codeberg`, `forgejo`, `gitea`. Required for any name that is not one of the five reserved names. |

The five reserved names (`github`, `gitlab`, `bitbucket`, `codeberg`, `gitea`) carry a built-in default URI and provider
type. Any other name is opaque — the name is never parsed for type hints — so `type` and `uri` must both be set. The
typed provider supplies API URL logic, identity resolution, and (for Bitbucket) credential rewriting; `uri` overrides
only the upstream address.

### Bitbucket identity resolution

Bitbucket does not enforce the git push username — only the token is validated. To enable identity resolution (required
for push permission checks and commit identity verification), the proxy adopts the convention that the **HTTP Basic-auth
username in the remote URL must be the user's Bitbucket account email address**.

Configure the remote URL like this:

```bash
https://<email>:<api-token>@bitbucket.org/<workspace>/<repo>.git
```

The proxy calls `GET /2.0/user` using those credentials to look up the user's Bitbucket `username` (the auto-generated
URL-safe identifier, e.g. `a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6`). It then rewrites the outbound credentials to
`username:token` before forwarding the push to Bitbucket — this is necessary because Bitbucket's git endpoint only
accepts the internal username, not an email address.

**Required API token scopes:** `read:user:bitbucket` and `write:repository:bitbucket`.

<!-- prettier-ignore-start -->
> [!TIP]
> **M&A / private server use case:** This same mechanism works for self-hosted Bitbucket Data Center instances. Set `uri` to your internal Bitbucket URL and the proxy will route and rewrite credentials accordingly, making it straightforward to gate pushes to acquired-company repositories during an integration period.
<!-- prettier-ignore-end -->

## Commit validation

Per-commit checks (identity, email policy, message content) apply to both store-and-forward and transparent proxy modes.

```yaml
commit:
  # Committer email policy — the committer is the employee who ran git commit or git rebase.
  # This is the primary corporate control: enforce that your staff use their work identity.
  # Rebased commits from external contributors still pass as long as the committer email is valid.
  committer:
    email:
      domain:
        # Regex the committer email domain must match. Omit to allow all domains.
        allow: "corp\\.example\\.com$"
      local:
        # Regex blocking specific local-parts (before @). Omit to allow all.
        block: "^(noreply|no-reply|bot|nobody)$"

  # Author email policy — the author is whoever originally wrote the commit.
  # Configure this only if you want to disallow rebasing external contributors' commits.
  # When set, any commit whose author email is outside the allowed domain is blocked —
  # developers must open PRs from the original fork rather than rebasing upstream changes.
  # Omit this block entirely to allow external author emails (the most common setup).
  author:
    email:
      domain:
        allow: "corp\\.example\\.com$"

  message:
    block:
      literals:
        - "WIP"
        - "DO NOT MERGE"
      patterns:
        - '(?i)(password|secret|token)\s*[=:]\s*\S+'
```

## Diff scan

Push-level check applied once per push against the aggregate diff (all commits combined). Only added lines (`+`) are
scanned — deletions and context lines are ignored.

```yaml
diff-scan:
  block:
    literals:
      - "internal.corp.example.com"
    patterns:
      - '(?i)https?://[a-z0-9.-]*\.corp\.example\.com\b'
```

## Secret scanning

Secret scanning via gitleaks (<https://github.com/gitleaks/gitleaks>). Applied once per push. The JAR ships with a
bundled gitleaks binary so scanning works out of the box.

Binary resolution order (first match wins):

1. `scanner-path` — explicit path, bypasses everything else
2. `version` + `auto-install: true` — downloads and caches that version on startup
3. Bundled JAR binary (default version, always present)
4. System `PATH`

```yaml
secret-scan:
  enabled: false
  # version: 8.22.0
  # auto-install: true
  # install-dir: ~/.cache/fogwall/gitleaks
  # scanner-path: /usr/local/bin/gitleaks

  # External TOML rules file. Ignored when inline-config is set.
  # config-file: /app/conf/.gitleaks.toml

  # Inline TOML config — takes precedence over config-file. Hot-reloadable via
  # POST /api/config/reload?section=secret-scan. Content must be valid gitleaks TOML:
  # inline-config: |
  #   title = "my-org"
  #   [extend]
  #   useDefault = true
  #   [[rules]]
  #   id = "my-org-api-key"
  #   regex = '''MY_ORG_[A-Z0-9]{32}'''

  # timeout-seconds: 30
```

## Hot reload

Selected config sections can be reloaded at runtime without restarting the server. Two reload sources are supported:

- **File watch** — monitors a local YAML file; triggers automatically on modification
- **Git source** — periodically pulls a git repository and reads a YAML overlay from it

```yaml
reload:
  file:
    enabled: false
    path: /app/conf/fogwall-local.yml # watched for modifications
  git:
    enabled: false
    url: https://github.com/myorg/config.git
    branch: main
    file-path: fogwall.yml
    interval-seconds: 300 # 0 = manual trigger only
```

#### Git source authentication

For private repositories, set these environment variables — no config file changes needed:

```
FOGWALL_RELOAD_GIT_AUTH_USERNAME=<username or token placeholder>
FOGWALL_RELOAD_GIT_AUTH_PASSWORD=<personal access token or password>
```

Both variables must be set together; if only one is present a warning is logged and the clone/pull proceeds without
credentials. For token-only auth (GitHub, GitLab, Gitea PATs) the username can be any non-empty string — `git` or
`x-token` are common placeholders.

### Reloadable sections

| Section        | YAML key        | What changes take effect                                            |
| -------------- | --------------- | ------------------------------------------------------------------- |
| `commit`       | `commit:`       | Author email rules, message block lists, identity-verification mode |
| `diff-scan`    | `diff-scan:`    | Diff content block literals and patterns                            |
| `secret-scan`  | `secret-scan:`  | All gitleaks settings including `inline-config`                     |
| `rules`        | `rules:`        | URL access control allow/deny rules                                 |
| `permissions`  | `permissions:`  | Config-sourced user→repo permission grants                          |
| `attestations` | `attestations:` | Dashboard approval form questions                                   |

Provider, server, and database sections always require a restart.

### Manual trigger

```
POST /api/config/reload                         # reload all sections
POST /api/config/reload?section=commit          # commit rules only
POST /api/config/reload?section=diff-scan       # diff scan only
POST /api/config/reload?section=secret-scan     # gitleaks config only
POST /api/config/reload?section=rules           # URL rules only
POST /api/config/reload?section=permissions     # permissions only
POST /api/config/reload?section=attestations    # attestation questions only
```

The dashboard admin panel also provides a section dropdown for manual triggers.

## Identity verification

```yaml
commit:
  identity-verification: warn # warn | strict | off
```

For every push, the proxy runs two checks:

1. **SCM login check** — calls the upstream provider's user API with the token supplied in the git credentials (the HTTP
   Basic-auth password). The returned login (e.g. GitHub `login`, GitLab `username`) is matched against the
   authenticated fogwall user's `scm-identities`. This check is **always enforced** regardless of the
   `identity-verification` mode — a push from a token that cannot be matched to a registered proxy user is always
   blocked.

2. **Commit email check** — every author and committer email in the pushed commits is checked against the authenticated
   fogwall user's `emails` list. These emails are populated independently of the SCM: they come from the IdP on
   LDAP/OIDC login, or from additional associations added via the dashboard. This is what ties commit attribution back
   to a verified real person. The `identity-verification` mode controls this check only.

<!-- prettier-ignore-start -->
> [!NOTE]
> The HTTP Basic-auth username in the remote URL is not used for identity resolution. It is ignored by all providers (except Bitbucket). Configure your remote URL with any username — `git`, `me`, your actual name — it makes no difference.
<!-- prettier-ignore-end -->

### Modes

`identity-verification` controls the **commit email check** only. The SCM login check is always enforced.

| Mode     | Behaviour                                                                                       | Use when                                                                       |
| -------- | ----------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------ |
| `strict` | Blocks the push if any commit email cannot be matched to the authenticated fogwall user         | Production — enforces that every commit is attributed to the person who pushed |
| `warn`   | Allows the push through but emits a sideband warning to the git client and records the mismatch | Rolling out to an existing team — lets you observe mismatches before enforcing |
| `off`    | Commit email check is disabled entirely                                                         | Migrations or environments where email data is not yet populated               |

<!-- prettier-ignore-start -->
> [!CAUTION]
> `warn` is not a security control. Pushes succeed regardless of the email check outcome. Only `strict` blocks mismatched commits. The default is `warn` to avoid breaking existing deployments on first install.
>
> **Known limitation:** `strict` currently checks **author** emails, not committer emails. This means rebased commits (where the original author is a different person) will be blocked even though the pusher is legitimate. Teams that rebase should use `warn` until this is fixed — see [#348](https://github.com/RBC/fogwall/issues/348).
<!-- prettier-ignore-end -->

### Token scope requirements

The SCM login check calls `GET /user` (or equivalent) on the upstream SCM using the pusher's token. The token must carry
at least the following scope:

| Provider | API endpoint                           | Additional scope                                                       |
| -------- | -------------------------------------- | ---------------------------------------------------------------------- |
| GitHub   | `GET https://api.github.com/user`      | No additional scopes required for either classic or fine-grained PATs. |
| GitLab   | `GET {uri}/api/v4/user`                | `read_user` or `api` (not recommended)                                 |
| Codeberg | `GET https://codeberg.org/api/v1/user` | `read:user`                                                            |
| Gitea    | `GET https://gitea.com/api/v1/user`    | `read:user`                                                            |

If the token is missing the required scope or cannot be resolved to a registered proxy user, the push is blocked
regardless of `identity-verification` mode.

### Prerequisites

Both checks require the user record to be populated before a push. A push from a token that cannot be matched to any
registered proxy user is always blocked. Use `identity-verification: warn` during rollout to allow pushes through while
users register their commit emails; the SCM identity must be registered before any push can proceed.

```yaml
users:
  - username: alice
    password-hash: "{bcrypt}$2a$12$..."
    roles:
      - ADMIN # optional; defaults to [USER] if omitted
    emails:
      - alice@example.com
    # push-usernames: HTTP Basic-auth usernames accepted for this user when pushing.
    # The proxy username is always implicitly valid; these are additional aliases.
    # Useful when git clients send a fixed username (e.g. "git") that differs from
    # the proxy username. Stored internally as SCM identities under the "proxy" provider.
    push-usernames:
      - git
      - alice-bot
    scm-identities:
      - provider: github
        username: alice-gh
      - provider: gitlab
        username: alice
```

## URL rules

URL rules control which repositories are accessible through the proxy. fogwall is **default-deny**: if no allow rules
are configured for a provider, all pushes and fetches to that provider are rejected. At least one allow rule must match
for a request to proceed.

Rules use a unified `match` block that specifies what to match against (`target`), the pattern string (`value`), and how
to interpret it (`type`). Evaluation is first-match-wins by `order` — identical to iptables/firewall rule semantics.

```yaml
rules:
  allow:
    # Specific repo by exact slug — both operations, scoped to one provider
    - enabled: true
      order: 110
      operation: BOTH
      provider: github
      match:
        target: SLUG
        value: /RBC/fogwall
        type: LITERAL

    # All repos under an owner — fetch only, any provider
    - enabled: true
      order: 120
      operation: FETCH
      match:
        target: OWNER
        value: finos
        type: GLOB

  deny:
    # Block a specific repo across all operations
    - enabled: true
      order: 100
      match:
        target: SLUG
        value: /myorg/forbidden-repo
        type: LITERAL
```

To allow all repositories on a provider (open mode):

```yaml
rules:
  allow:
    - enabled: true
      order: 110
      operation: BOTH
      provider: internal-github
      match:
        target: OWNER
        value: "*"
        type: GLOB
```

### URL rule properties

| Property       | Type    | Default | Description                                                         |
| -------------- | ------- | ------- | ------------------------------------------------------------------- |
| `enabled`      | boolean | `true`  | Whether this entry is active                                        |
| `order`        | int     | `1100`  | Evaluation order (lower = earlier; first match wins)                |
| `operation`    | string  | `BOTH`  | `FETCH`, `PUSH`, or `BOTH` — which git operation this entry matches |
| `provider`     | string  | _(all)_ | Provider name to scope this entry to; omit or leave blank for all   |
| `match`        | object  | —       | Repository match criteria — see below                               |
| `match.target` | enum    | `SLUG`  | What to match: `SLUG` (`/owner/repo`), `OWNER`, or `NAME`           |
| `match.value`  | string  | —       | The pattern to match against the chosen target                      |
| `match.type`   | enum    | `GLOB`  | How to interpret the pattern: `LITERAL`, `GLOB`, or `REGEX`         |

### Pattern matching

Each rule matches exactly one thing — the `match` block selects what URL part to test (`target`) and how to test it
(`type`). To express an AND condition (e.g. specific owner AND specific name prefix), use `target: SLUG` and write a
single glob or regex that covers both parts.

#### LITERAL

Exact string match after normalising a leading `/`. `/acme/repo` and `acme/repo` are equivalent.

#### GLOB

Wildcard matching using `*` (any characters) and `?` (single character).

| Target  | `*` behaviour                                               |
| ------- | ----------------------------------------------------------- |
| `SLUG`  | Does **not** cross `/` — use `acme/*` not `acme/**`         |
| `OWNER` | Owner names cannot contain `/` — `*` matches any valid name |
| `NAME`  | Repo names cannot contain `/` — `*` matches any valid name  |

| Pattern (GLOB, target=SLUG) | Matches                                     | Does NOT match  |
| --------------------------- | ------------------------------------------- | --------------- |
| `/acme/repo` _(LITERAL)_    | `/acme/repo`                                | `/acme/other`   |
| `/acme/*`                   | `/acme/repo`, `/acme/my-service`            | `/other/repo`   |
| `/acme/service-*`           | `/acme/service-api`, `/acme/service-worker` | `/acme/repo`    |
| `/acme/repo-?`              | `/acme/repo-1`, `/acme/repo-a`              | `/acme/repo-12` |

#### REGEX

Full Java regular expression. A few things to know before writing regex rules:

- **Full-string match**: the pattern must match the entire candidate string — there are no implicit anchors, but
  `matches()` semantics apply. A pattern of `acme` does **not** match `/acme/repo`; write `/acme/.*` or `.*acme.*`.
- **`/` does not need escaping**: Java regex uses strings, not a `/pattern/` literal syntax. Write `/acme/.*` not
  `\/acme\/.*`.
- **Case-insensitive**: use the `(?i)` inline flag — e.g. `(?i)/acme/.*`.
- **Anchoring**: explicit `^` and `$` are redundant with `matches()` but harmless if included.

| Pattern (REGEX, target=SLUG) | Matches                               | Does NOT match      |
| ---------------------------- | ------------------------------------- | ------------------- |
| `/acme/.*`                   | `/acme/repo`, `/acme/my-service`      | `/other/repo`       |
| `/(acme\|partner)/.*`        | `/acme/repo`, `/partner/repo`         | `/other/repo`       |
| `/acme/service-[0-9]+`       | `/acme/service-1`, `/acme/service-42` | `/acme/service-api` |
| `(?i)/acme/.*`               | `/acme/repo`, `/ACME/Repo`            | `/other/repo`       |

| Pattern (REGEX, target=NAME)       | Matches                      | Does NOT match |
| ---------------------------------- | ---------------------------- | -------------- |
| `(?i)(^&#124;-)secret(-&#124;$).*` | `secret-config`, `my-secret` | `secretariat`  |
| `migrate-.*`                       | `migrate-app`, `migrate-db`  | `old-migrate`  |

```yaml
rules:
  deny:
    # Block any repo whose name contains "secret" as a distinct word segment (case-insensitive)
    - enabled: true
      order: 50
      operation: PUSH
      match:
        target: NAME
        value: "(?i)(^|-)secret(-|$).*"
        type: REGEX

    # Block repos matching multiple owner orgs using alternation
    - enabled: true
      order: 51
      operation: BOTH
      match:
        target: OWNER
        value: "(blocked-org|suspended-org)"
        type: REGEX
```

### Real-world URL rule examples

**Gateway for a specific SCM — allow all push and fetch:**

```yaml
rules:
  allow:
    - enabled: true
      order: 110
      operation: BOTH
      provider: internal-github
      match:
        target: OWNER
        value: "*"
        type: GLOB
```

**Allow repos under a set of known owner orgs, identified by a name prefix:**

```yaml
rules:
  allow:
    - enabled: true
      order: 110
      operation: BOTH
      provider: internal-github
      match:
        target: OWNER
        value: "team-(alpha|beta|gamma)"
        type: REGEX
```

**Allow push only for repos whose name starts with a known prefix:**

When repos are identified by a project code followed by a hyphen, match on `NAME` so the rule applies regardless of
which org the repo lives under.

```yaml
rules:
  allow:
    - enabled: true
      order: 110
      operation: PUSH
      provider: internal-github
      match:
        target: NAME
        value: "proj0-*"
        type: GLOB

  # Multiple project prefixes — one rule per prefix, or combine with REGEX alternation:
  allow:
    - enabled: true
      order: 111
      operation: PUSH
      provider: internal-github
      match:
        target: NAME
        value: "(proj0|proj1|shared)-.*"
        type: REGEX
```

**Combine owner and name matching (AND condition):**

Use `target: SLUG` with a glob or regex — the slug is `/owner/repo` so a single pattern can constrain both parts.

```yaml
rules:
  allow:
    # Allow fetch from the source SCM for a specific org + name prefix (glob AND)
    - enabled: true
      order: 110
      operation: FETCH
      provider: source-github
      match:
        target: SLUG
        value: "acquired-org/migrate-*"
        type: GLOB

    # Allow push to the destination SCM — stricter control with regex
    - enabled: true
      order: 120
      operation: PUSH
      provider: dest-gitlab
      match:
        target: SLUG
        value: "/migrated-org/migrate-.*"
        type: REGEX
```

## Permissions

Permissions control which proxy users can push to or review pushes from specific repositories. They are checked
**after** URL rules: a push that is blocked by a deny rule never reaches the permission check.

Permissions are hot-reloadable (see [Reloadable sections](#reloadable-sections)).

```yaml
permissions:
  # LITERAL (default): exact /owner/repo match
  - username: alice
    provider: github
    match:
      target: SLUG
      value: /myorg/myrepo
      type: LITERAL
    grant: PUSH

  # GLOB: wildcard repo name under a specific owner
  - username: bob
    provider: gitlab
    match:
      target: SLUG
      value: /myorg/*
      type: GLOB
    grant: PUSH_AND_REVIEW

  # OWNER target: grant access to all repos under an org
  - username: carol
    provider: github
    match:
      target: OWNER
      value: myorg
      type: GLOB
    grant: REVIEW

  # REGEX on SLUG: match repos under multiple orgs
  - username: dave
    provider: github
    match:
      target: SLUG
      value: "/team-(alpha|beta)/.*"
      type: REGEX
    grant: PUSH_AND_REVIEW

  # SELF_CERTIFY: trusted contributor who can approve their own clean pushes.
  # Requires both this permission entry AND the SELF_CERTIFY role on the user.
  - username: trusted
    provider: github
    match:
      target: SLUG
      value: /myorg/myrepo
      type: LITERAL
    grant: SELF_CERTIFY
```

### Permission properties

| Property       | Type   | Default           | Description                                                               |
| -------------- | ------ | ----------------- | ------------------------------------------------------------------------- |
| `username`     | string | —                 | Proxy username (must match a `users:` entry or a DB user)                 |
| `provider`     | string | —                 | Provider name as defined in `providers:` config                           |
| `match`        | object | —                 | Repository match criteria — see below                                     |
| `match.target` | enum   | `SLUG`            | What to match: `SLUG` (`/owner/repo`), `OWNER`, or `NAME`                 |
| `match.value`  | string | —                 | The pattern to match against the chosen target                            |
| `match.type`   | enum   | `GLOB`            | How to interpret the pattern: `LITERAL`, `GLOB`, or `REGEX`               |
| `grant`        | enum   | `PUSH_AND_REVIEW` | What the user may do: `PUSH`, `REVIEW`, `PUSH_AND_REVIEW`, `SELF_CERTIFY` |

### Pattern matching

Permissions support the same three match types as URL rules (LITERAL, GLOB, REGEX) applied to the same three targets
(SLUG, OWNER, NAME). See [Pattern matching](#pattern-matching) above for full semantics including regex behaviour.

GLOB on `target: SLUG` follows slug path conventions:

| Pattern (GLOB, target=SLUG) | Matches                                     | Does NOT match |
| --------------------------- | ------------------------------------------- | -------------- |
| `/acme/repo`                | `/acme/repo`                                | `/acme/other`  |
| `/acme/*`                   | `/acme/repo`, `/acme/my-service`            | `/other/repo`  |
| `/acme/service-*`           | `/acme/service-api`, `/acme/service-worker` | `/acme/repo`   |
| `/*/proj0-*`                | `/acme/proj0-api`, `/other/proj0-db`        | `/acme/other`  |

<!-- prettier-ignore-start -->
> [!NOTE]
> **Conflict detection:** At config load time and when saving via the dashboard API, fogwall rejects any new permission entry whose pattern overlaps with an existing entry for the same user and provider. Two entries overlap when they are equal, or when one is a GLOB/REGEX pattern that would match the other's value. This prevents silent misconfiguration where the effective permission depends on evaluation order.
<!-- prettier-ignore-end -->

### Real-world permission examples

**Allow a user to push any repo on a specific provider:**

```yaml
permissions:
  - username: alice
    provider: internal-github
    match:
      target: OWNER
      value: "*"
      type: GLOB
    grant: PUSH_AND_REVIEW
```

**Allow push to repos whose name starts with a project code:**

```yaml
permissions:
  - username: alice
    provider: internal-github
    match:
      target: NAME
      value: "proj0-*"
      type: GLOB
    grant: PUSH

  # Or match the same prefix across any owner using SLUG:
  - username: alice
    provider: internal-github
    match:
      target: SLUG
      value: "/*/proj0-*"
      type: GLOB
    grant: PUSH
```

**Regex — match repos under multiple owner orgs:**

```yaml
permissions:
  - username: alice
    provider: internal-github
    match:
      target: SLUG
      value: "/team-(alpha|beta)/.*"
      type: REGEX
    grant: PUSH_AND_REVIEW
```

**Self-certify for a trusted committer scoped to a prefix:**

A trusted committer needs both entries: `PUSH_AND_REVIEW` to be able to push, and `SELF_CERTIFY` to bypass the peer
review requirement. These cover separate code paths and are not treated as conflicting.

```yaml
permissions:
  # Push and review access
  - username: trusted-dev
    provider: internal-github
    match:
      target: NAME
      value: "proj0-*"
      type: GLOB
    grant: PUSH_AND_REVIEW

  # Self-certify on the same scope (requires SELF_CERTIFY role on the user too)
  - username: trusted-dev
    provider: internal-github
    match:
      target: NAME
      value: "proj0-*"
      type: GLOB
    grant: SELF_CERTIFY
```

### Grant

| Value             | Effect                                                                                                                    |
| ----------------- | ------------------------------------------------------------------------------------------------------------------------- |
| `PUSH`            | User may push to matching repositories                                                                                    |
| `REVIEW`          | User may approve or reject pushes submitted by others                                                                     |
| `PUSH_AND_REVIEW` | Shorthand for both PUSH and REVIEW; does **not** include SELF_CERTIFY                                                     |
| `SELF_CERTIFY`    | Trusted contributor: may approve their own clean pushes without a peer reviewer. Requires the `SELF_CERTIFY` role as well |

<!-- prettier-ignore-start -->
> [!IMPORTANT]
> `SELF_CERTIFY` is a two-key lock: the user must have both a `SELF_CERTIFY` permission entry for the repository _and_ the `SELF_CERTIFY` role (set via `users[].roles` or `auth.role-mappings`). Either alone is not sufficient.
<!-- prettier-ignore-end -->

## Attestations

Attestation questions are presented to reviewers in the dashboard approval form. All configured questions must be
answered before the reviewer can submit an approval. Attestations are hot-reloadable.

```yaml
attestations:
  - id: reviewed-content
    type: checkbox
    label: "I have reviewed the diff and it contains no sensitive or proprietary information"
    required: true

  - id: policy-compliance
    type: checkbox
    label: "This push complies with our open source contribution policy"
    required: true

  - id: ticket-ref
    type: text
    label: "Internal ticket or justification reference"
    required: false

  - id: risk-level
    type: dropdown
    label: "Estimated risk level for this change"
    options:
      - Low
      - Medium
      - High
    required: true
    tooltip: "Select the risk level based on the scope and nature of the change"
```

### Attestation properties

| Property   | Type    | Default    | Description                                                          |
| ---------- | ------- | ---------- | -------------------------------------------------------------------- |
| `id`       | string  | —          | Unique key used to store the reviewer's answer in the push record    |
| `type`     | string  | `checkbox` | Input type: `checkbox`, `text`, or `dropdown`                        |
| `label`    | string  | —          | Question text shown in the review form                               |
| `required` | boolean | `false`    | Whether the question must be answered before the reviewer can submit |
| `options`  | list    | _(empty)_  | Choices for `dropdown` type; ignored for other types                 |
| `tooltip`  | string  | _(none)_   | Optional help text shown alongside the question                      |

Set `attestations: []` (or omit the key) to disable attestations entirely.

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
