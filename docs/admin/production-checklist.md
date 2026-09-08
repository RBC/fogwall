# Production checklist

## Database

Default `h2-mem` loses all push records on restart. For production:

```yaml
# PostgreSQL — recommended
database:
  type: postgres
  url: jdbc:postgresql://db.internal:5432/fogwall?sslmode=verify-full&sslrootcert=/certs/ca.crt
  username: fogwall
  password: secret

# H2 file — zero external dependencies, persistent
database:
  type: h2-file
  path: /app/.data/fogwall

# MySQL / MariaDB — same config shape, different type
database:
  type: mysql # or mariadb
  url: jdbc:mysql://db.internal:3306/fogwall
  username: fogwall
  password: secret
```

`database.type` accepts `h2-mem` (default), `h2-file`, `postgres`, `mysql`, `mariadb`, or `mongo`. Schema is applied
automatically via Flyway on startup for the JDBC backends.

## TLS

Put fogwall behind a reverse proxy (nginx, Caddy, Envoy) for TLS termination in production. The application can also
terminate TLS directly if preferred — see [TLS](../configuration/tls.md).

For upstream connections to internal GitLab/Bitbucket/Forgejo instances with a corporate CA:

```yaml
server:
  tls:
    trust-ca-bundle: /etc/fogwall/tls/internal-ca.pem
```

This merges the corporate CA with the JVM's built-in trust anchors so public providers (GitHub, GitLab SaaS) continue to
work without changes.

## Standalone server image (no dashboard)

The default `docker build .` produces the dashboard image (`FogwallDashboardApplication`) — proxy, REST API, approval
UI. For enforcement-only deployments that don't need the dashboard or approval UI (CI pipelines, automated
environments), build the lighter standalone server target instead:

```bash
docker build --target server -t fogwall-server .
```

This runs `FogwallJettyApplication` — the git validation and forwarding pipeline with YAML-driven configuration, no
Spring, no React/Node build step, no REST API. It uses the same config override mechanism as the dashboard image (mount
a `fogwall-{profile}.yml` at `/app/conf/`, set `FOGWALL_CONFIG_PROFILES`) and exposes the same port 8080.

A push's lifecycle here is automated checks and nothing else: decisions are recorded to the database, but there is no
review step, because there is nothing to review with. Two settings therefore **fail startup** on this image rather than
being accepted and then never satisfied:

| Setting                           | Why it cannot work here                                                              |
| --------------------------------- | ------------------------------------------------------------------------------------ |
| `server.approval-mode: ui`        | A held push waits for a decision over the REST API, which this image does not serve. |
| `scm-oauth.identity-mode: strict` | Only OAuth-verified identities count, and account linking is a dashboard flow.       |

Pick the dashboard image if you want either. The two are alternatives, not halves of one deployment.

```bash
docker run -e FOGWALL_CONFIG_PROFILES=docker-default \
  -v ./docker/fogwall-docker-default.yml:/app/conf/fogwall-docker-default.yml:ro \
  -p 8080:8080 fogwall-server
```

## Health check

The dashboard module exposes an unauthenticated health endpoint:

```text
GET /api/health   → 200 OK with status payload when the server is up
```

The standalone server module (`fogwall-server`) does not expose a health endpoint — use a TCP check against the proxy
port instead.

## Imported SSH keys and revocation

Linking an account imports the SSH keys registered on it, marked as coming from that provider. In
`scm-oauth.identity-mode: strict` those imported keys are the only ones that resolve an SCM identity for an SSH push.

fogwall does not poll providers to notice a key removed upstream, and does not need to: a push is forwarded with the
client's own SSH agent, so a key revoked on the SCM fails there whatever fogwall still holds. Fetches re-sync from
upstream with that same agent, and fogwall grants no fetch permission of its own. A user who wants their imported keys
re-read unlinks and re-links the account, which imports them again.

## Identifying which build is running

The `edge` image is rebuilt from `main` on every commit, so a version alone does not identify a deployment. Both modules
log their version and short commit on the first line at startup:

```text
Starting fogwall with dashboard 1.3.2 (f04b5ff)...
```

The dashboard also reports both over the API, which is the easier one to check against a running deployment:

```text
GET /api    → {"version":"1.3.2","commit":"f04b5ffc…","apiDocs":"/api/openapi.json"}
```

`commit` reads `unknown` when the build could not establish one — a `docker build` run without the `BUILD_COMMIT` build
argument, for instance. The published images always carry it. fogwall also names its version in the `User-Agent` it
sends on requests it originates against a provider's API (`fogwall/1.3.2`), so a provider-side rate-limit or deprecation
notice can be traced back to a build; requests it merely brokers for a CLI keep that CLI's own `User-Agent` untouched.

For Kubernetes (dashboard module):

```yaml
livenessProbe:
  httpGet:
    path: /api/health
    port: 8080
  initialDelaySeconds: 15
  periodSeconds: 10
readinessProbe:
  httpGet:
    path: /api/health
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 5
```

## Session timeout

Default session lifetime is 24 hours. Tighten for compliance environments:

```yaml
auth:
  session-timeout-seconds: 28800 # 8 hours
```

## API key

The REST API accepts a single shared API key for machine-to-machine calls (e.g. approval scripts). Change the default
before going to production:

```yaml
# In config or via env var:
FOGWALL_API_KEY: "your-secret-key"
```

The shared key is a stopgap for automation until proper machine auth is available. It carries no user identity — all
calls made with it are unattributed. Prefer session-based access (log in as a named service account) for any automation
that needs an audit trail.

<!-- prettier-ignore-start -->
> [!NOTE]
> **Roadmap:** Per-user and per-service API keys, and an OAuth2 resource server mode for machine-to-machine auth, are tracked in [#57](https://github.com/RBC/fogwall/issues/57). Until then, treat the shared key as a temporary measure and rotate it regularly.
<!-- prettier-ignore-end -->
