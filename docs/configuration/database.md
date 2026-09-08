# Database

```yaml
database:
  type: h2-mem # h2-mem | h2-file | postgres | mysql | mariadb | mongo
```

## Database backends

| Type       | Description            | Extra keys                                                                 |
| ---------- | ---------------------- | -------------------------------------------------------------------------- |
| `h2-mem`   | H2 in-memory (default) | `name` (default: `fogwall`)                                                |
| `h2-file`  | H2 persisted to disk   | `path` (default: `./.data/fogwall`)                                        |
| `postgres` | PostgreSQL             | `url` **or** `host`, `port`, `name`, `username`, `password`                |
| `mysql`    | MySQL 8.0+             | `url` **or** `host`, `port` (default 3306), `name`, `username`, `password` |
| `mariadb`  | MariaDB 10.5+          | `url` **or** `host`, `port` (default 3306), `name`, `username`, `password` |
| `mongo`    | MongoDB                | `url` (required); `name` optional if the database is in the URI path       |

SQLite is intentionally not supported — its single-writer file locking is unsuitable for a proxy that may run more than
one instance against the same database.

For `postgres`, `mysql`, `mariadb`, and `mongo`, setting `url` to a full connection string is the recommended approach
when you need driver-specific options (TLS, SSL certificates, connection parameters) that are not exposed as individual
config fields.

MySQL and MariaDB use **separate JDBC drivers** (`mysql-connector-j` and `mariadb-java-client`, not one shared driver) —
pick the `type` matching the actual engine you're running, even though the two are largely wire-compatible.
`database.port` defaults to PostgreSQL's port (5432); for `mysql`/`mariadb` set it explicitly (typically `3306`) unless
the server actually listens on 5432 — fogwall logs a startup warning if it detects the untouched default being used with
either type.

## Connection pool tuning

Applies to all JDBC backends (`h2-mem`, `h2-file`, `postgres`, `mysql`, `mariadb`). The default pool size is
deliberately small — git push workloads are sequential per user, so a large pool buys nothing and drives up aggregate
connection counts when multiple instances share a database. The
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

# MySQL — individual fields
database:
  type: mysql
  host: db.internal
  port: 3306
  name: fogwall
  username: fogwall
  password: secret

# MySQL — connection string
database:
  type: mysql
  url: jdbc:mysql://db.internal:3306/fogwall?useSSL=true&requireSSL=true
  username: fogwall
  password: secret

# MariaDB — individual fields
database:
  type: mariadb
  host: db.internal
  port: 3306
  name: fogwall
  username: fogwall
  password: secret

# MariaDB — connection string
database:
  type: mariadb
  url: jdbc:mariadb://db.internal:3306/fogwall?useSsl=true
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

## SCM token identity cache

When users are configured, fogwall caches successful token-to-username resolutions so that repeated pushes from the same
PAT do not incur a provider API call every time. The cache is backed by the configured database (JDBC or MongoDB) and
keyed on a SHA-512 digest of the token — the raw token is never stored.

| Variable                         | Default | Effect                                                                                                                               |
| -------------------------------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| `FOGWALL_SCM_CACHE_MAX_AGE_DAYS` | `7`     | Maximum age of a cache entry in days. Entries older than this are ignored on read and overwritten on the next successful resolution. |

Token rotation is handled automatically: a new PAT produces a new cache key, so the old entry simply ages out. There is
no need to flush the cache manually when tokens are rotated.

## MongoDB: coexisting with the upstream Node.js git-proxy

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
