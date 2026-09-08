# Server settings

```yaml
server:
  port: 8080

  # Approval mode for server mode pushes:
  #   auto       — approves every clean push immediately (default; no dashboard required)
  #   ui         — waits for a human reviewer via the REST API
  #   servicenow — delegates to a ServiceNow approval workflow
  # Note: FogwallDashboardApplication always uses 'ui' regardless of this setting.
  approval-mode: auto

  # How long a server mode push waits for a review decision while the client
  # connection is held open (approval-mode: ui or servicenow). On expiry the push is
  # marked CANCELED and rejected — the developer must re-push and re-review. This is a
  # live-session bound, not a durable queue; hold it short. Default 1800 (30 minutes).
  approval-timeout-seconds: 1800

  # Sideband keepalive interval in seconds for server mode operations.
  # Sends periodic progress packets to prevent idle-timeout disconnects during
  # long steps (secret scanning, approval polling). Set to 0 to disable.
  heartbeat-interval-seconds: 10

  # Whether server mode serves clone/fetch from its local mirror. Default true —
  # a developer whose remote is the fogwall URL expects `git pull` to work against it.
  # Applies to both server mode transports (HTTP and SSH). Set to false to make fogwall
  # a push-only gateway that never serves a local mirror; fetches are then refused with
  # a clear git-side error ("fetches are not served through this gateway"), not a 404
  # that reads as a missing repository. Push (receive-pack) is unaffected either way.
  # Transparent proxy mode is unaffected — it forwards to upstream, serving no local
  # mirror. Override per provider with providers.<name>.serve-fetch.
  serve-fetch: true

  # Maximum number of requests handled concurrently on virtual threads. Requests over
  # the limit wait for a slot. Each in-flight push holds its buffered pack data in
  # memory until the request completes, so size this to heap capacity and typical pack
  # size — prefer adding instances over raising this limit when scaling out.
  # Set to 0 to disable virtual-thread dispatch (requests then run directly on the
  # platform thread pool, capped by its own size).
  max-concurrent-requests: 512

  # Jetty platform thread-pool sizing. With virtual-thread dispatch on (the default,
  # above) this pool runs only Jetty's acceptors and selectors — not blocking
  # application work — so the defaults suit most deployments; these knobs are for
  # tuning a large or constrained instance. Defaults match Jetty's own QueuedThreadPool.
  # A config with min > max is rejected at startup.
  threads:
    min: 8 # minimum (core) platform threads kept alive
    max: 200 # maximum platform threads
    idle-timeout-ms: 60000 # ms an idle thread above `min` is kept before being reaped

  # Largest request body fogwall will accept, in bytes. Applies to both proxy modes.
  # An over-size push is rejected with a git error before the body is read, so it
  # costs no memory. Set to 0 to disable the check — note that "unlimited" is still
  # bounded by heap in practice, and absolutely by ~2GiB, since the body is buffered
  # as a single array.
  # Raising this needs a matching increase in container memory: the real bound is heap
  # divided by concurrent pushes. See "Sizing memory for pushes" in the Admin Guide.
  max-push-bytes: 67108864 # 64MiB

  # Largest decompressed size of any single object in a pushed pack, in bytes.
  # Applies to both proxy modes, and to both server mode transports (HTTP and
  # SSH). max-push-bytes above caps the compressed wire size, but a crafted pack can
  # inflate to roughly 1000x its compressed size — this caps the inflated side, per
  # object. A push containing a violating object is rejected during pack parsing.
  #
  # The default sits deliberately far above the binary-blob filter's 50MiB policy
  # ceiling: binary-blob is the tunable policy layer for "how big may a file be",
  # while this limit exists only to stop decompression bombs, and should be kept
  # high enough that no legitimate push ever hits it. Set to 0 to disable.
  max-object-size-bytes: 134217728 # 128MiB

  # Base URL fogwall is externally reachable at — the bare host, WITHOUT a /dashboard suffix (fogwall appends
  # /dashboard, /api, etc. itself where needed). Used in links sent to clients via sideband messages, and required
  # for SCM OAuth account linking (#40) to build a correct redirect_uri — OAuth linking is disabled with a clear
  # error if this is unset. No default — must be set explicitly.
  #
  # BREAKING CHANGE in v1.4.0: prior releases expected this to already include any path prefix your reverse proxy
  # adds (e.g. https://fogwall.internal.example.com/dashboard), with fogwall concatenating routes directly onto it.
  # As of v1.4.0 it must be the bare origin instead — drop a trailing /dashboard (or other prefix) from your existing
  # value when upgrading, or push-record/profile links in sideband messages will point at the wrong path.
  # service-url: https://fogwall.internal.example.com

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

## Session persistence for multi-instance deployments

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
