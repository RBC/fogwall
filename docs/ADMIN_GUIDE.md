# Administrator and Operator Guide

This guide covers deploying, configuring, and operating fogwall. It is written for the person responsible for running
the proxy — setting up user accounts, configuring providers and rules, diagnosing problems, and keeping the service
healthy.

For the YAML configuration reference, see [CONFIGURATION.md](CONFIGURATION.md). For developers pushing through the
proxy, see [USER_GUIDE.md](USER_GUIDE.md).

---

## Conceptual model: three independent layers

Before diving into configuration details, it helps to understand that access control in fogwall is three orthogonal
layers that all must pass before a push is forwarded:

```text
1. Access rules       rules.allow / rules.deny
   "Is this repo even on the proxy's allowed list?"
         ↓
2. User permissions   permissions:
   "Is this user allowed to push to this specific repo?"
         ↓
3. Commit validation  commit:
   "Does the content of this push comply with policy?"
```

A push fails at the first layer that rejects it. A common misconfiguration is to add a repo to `rules.allow` but forget
to add a `permissions` entry for the user — or vice versa. Both are required.

**Access rules** are site-wide policy: they determine what the proxy will route at all, independently of who is pushing.
Think of them as a firewall rule list.

**User permissions** are per-user grants scoped to a provider and path. They determine whether a particular
authenticated user is permitted to push to (or review) a particular repository.

**Commit validation** runs against the push content: author email domains, commit messages, diff scanning, secret
scanning. These apply to everyone regardless of permissions.

---

## User accounts

fogwall supports four authentication backends. **LDAP, AD, and OIDC are the expected production choices.** Local auth
manages users in the database (add/remove users, reset passwords via the dashboard) with passwords defined in YAML
config. It is self-contained and requires no external directory, but every user must be provisioned manually. It is
suitable for small teams or single-operator deployments; LDAP, AD, or OIDC are preferable when the org already has a
directory.

### Authentication backends

| Backend          | `auth.provider` | When to use                                                    |
| ---------------- | --------------- | -------------------------------------------------------------- |
| Local (static)   | `local`         | Dev / demo only. Passwords in YAML config.                     |
| LDAP             | `ldap`          | Generic LDAP directory (OpenLDAP, 389 DS, etc.)                |
| Active Directory | `ad`            | On-premises AD domain. UPN bind, no `user-dn-patterns` needed. |
| OIDC             | `oidc`          | Keycloak, Okta, Entra ID, Dex, etc.                            |

See [CONFIGURATION.md — Authentication](CONFIGURATION.md#authentication) for the full config reference and worked
examples.

### How users are provisioned per backend

**Local:** users are defined entirely in the `users:` YAML block. Each entry needs a username, BCrypt password hash, and
at least one email. Roles and SCM identities are set here too. Changes require a config reload.

```yaml
users:
  - username: alice
    password-hash: "{bcrypt}$2a$12$..."
    roles: [ADMIN]
    emails:
      - alice@corp.example.com
    scm-identities:
      - provider: github/github.com
        username: alice-github
```

**LDAP / AD:** users are provisioned automatically on first login. The proxy creates a user record from the directory
attributes returned at bind time. The `mail` attribute (if present) is stored as a locked email — locked means it cannot
be edited from the profile UI, since the directory is the source of truth. Roles are assigned via `auth.role-mappings`
(LDAP group CNs → role names). When `role-mappings` is configured, a user who does not match any mapped group is
**denied access entirely** — they authenticate successfully against the directory but are refused by the proxy. This is
intentional: the proxy is not open to all directory users by default. To grant baseline access, map a broad group (e.g.
all-staff) to `USER`.

SCM identities and permissions still need to be set up after first login — either by the user themselves from their
profile page, by an admin via the dashboard, or via a supplemental `users:` YAML entry (which can carry `scm-identities`
without a `password-hash` for IdP-authed users).

**OIDC:** same auto-provisioning and deny-by-default behaviour as LDAP. Groups from the configured `groups-claim`
(default: `groups`) are mapped to roles via `auth.role-mappings`. Email comes from the `email` claim in the ID token.
Users whose token carries no matching group claim are denied access.

### Dashboard roles

Roles control what a user can do in the dashboard and REST API:

| Role             | What it grants                                                                                                                                                   |
| ---------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `USER` (default) | View push records; approve or reject pushes they have `REVIEW` permission on; manage their own profile (emails, SCM identities)                                  |
| `ADMIN`          | Everything USER can do, plus: create/delete users, reset passwords, manage any user's profile, view all push records                                             |
| `SELF_CERTIFY`   | Grants the **capability** to self-approve pushes. This is the prerequisite gate — it must be present before any per-repo `SELF_CERTIFY` permission takes effect. |

`ROLE_USER` is granted to every authenticated user automatically when no `role-mappings` are configured (open mode).
When `role-mappings` are configured, access is deny-by-default — a user must belong to at least one mapped group or they
are refused login entirely. Map a broad group to `USER` to grant baseline access to all directory members.

`ROLE_SELF_CERTIFY` is the prerequisite gate for self-approval. It represents the capability, attested by your org's IdP
or IAM process. Self-approval requires **both** this role and a per-repo `SELF_CERTIFY` permission entry — neither alone
is sufficient. This separation lets organisations externalise the capability grant (who is trusted to self-certify at
all) to their existing directory/IAM procedures, while the per-repo entitlement remains managed inside fogwall.

How to grant `ROLE_SELF_CERTIFY`:

- **LDAP / AD / OIDC:** add `SELF_CERTIFY` to `auth.role-mappings` and map it to the appropriate IdP group.
- **Local auth:** add `SELF_CERTIFY` to `roles:` in the user's `users:` YAML entry.

<!-- prettier-ignore-start -->
> [!NOTE]
> Organisations that require mandatory peer review (four-eyes) for all activity should simply not grant `SELF_CERTIFY` role or permissions. If no user holds `SELF_CERTIFY`, all pushes require a separate reviewer.
<!-- prettier-ignore-end -->

**Roles are dashboard-level access only.** They do not control which repos a user can push to — that is what permissions
(below) are for.

### Emails and SCM identities

Every user record carries two independent data sets that the proxy uses to verify identity on each push:

**Emails** — the set of email addresses the user commits with (i.e. the value in `git config user.email`). On every
push, every author and committer email in the incoming commits is checked against this list. If an email is not
registered to the authenticated user, the push fails in `strict` mode or warns in `warn` mode. This is what ties commit
attribution to a verified real person.

**SCM identities** — the upstream provider username(s) for this user (e.g. their GitHub login). On every push, the proxy
calls the upstream API using the PAT supplied in the git credentials and checks the returned username against this list.
This confirms that the token being used actually belongs to the person who authenticated with the proxy, not a shared or
borrowed token.

These are two independent checks and both must pass in `strict` mode. They catch different things: a commit email
mismatch means the developer's git client is misconfigured or the commit is attributed to someone else; an SCM identity
mismatch means the token does not belong to the authenticated user.

**How emails are populated:**

- Local auth: set in the `users:` YAML block; editable from the profile UI.
- LDAP/AD: the directory `mail` attribute is imported on first login as a locked email (not editable from the UI — the
  directory is the source of truth). Additional emails can be added via the admin dashboard.
- OIDC: the `email` claim from the ID token is imported on first login as a locked email.

**How SCM identities are populated:**

There is no automatic source for SCM identities — they must be added manually regardless of auth backend. After first
login, either the user themselves or an admin can add SCM identities from the profile page in the dashboard. For
example: provider `github/github.com`, username `alice-gh`. Users manage their own profile; admins can manage any user's
profile.

For local auth, SCM identities can also be set in the `users:` YAML block:

```yaml
users:
  - username: alice
    # ...
    scm-identities:
      - provider: github/github.com
        username: alice-gh
      - provider: gitlab/gitlab.com
        username: alice
```

Until SCM identities are populated, pushes from that user will fail identity verification in `strict` mode. Use
`identity-verification: warn` during rollout to let pushes through while identities are being registered. See
[USER_GUIDE.md — Identity verification](USER_GUIDE.md#identity-verification) for the developer-facing view of what these
checks look like at the terminal.

### Disabling local admin when using an IdP

When LDAP, AD, or OIDC is configured, the static `users:` block still works and is evaluated alongside the IdP. In most
production setups you want to remove static local accounts (or at minimum remove any with `roles: [ADMIN]`) once
IdP-based login is confirmed working. See #103 for planned enforcement of this.

---

## Repo permissions

Permissions control which users can push to which repos, and who can review pushes.

```yaml
permissions:
  - username: alice
    provider: github/github.com
    path: /myorg/myrepo
    grant: PUSH
```

### Operations

| Value             | What it grants                                                                                                                                                                                                                                              |
| ----------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `PUSH`            | User can submit pushes to this repo for validation and review                                                                                                                                                                                               |
| `REVIEW`          | User can approve or reject pushes to this repo submitted by others                                                                                                                                                                                          |
| `PUSH_AND_REVIEW` | Shorthand for both PUSH and REVIEW                                                                                                                                                                                                                          |
| `SELF_CERTIFY`    | Per-repo entitlement: this user may self-approve pushes to this repo. Requires `ROLE_SELF_CERTIFY` (the capability role) to also be present — see [Dashboard roles](#dashboard-roles). Does **not** imply PUSH or REVIEW; grant those separately if needed. |

### SELF_CERTIFY — for solo contributors

`SELF_CERTIFY` is the right choice for a developer who works independently and does not have a team reviewer. Without
it, pushes in `ui` approval mode wait indefinitely for someone else to approve them.

Self-approval requires **two** things — both must be in place:

1. The `SELF_CERTIFY` role (capability gate) — granted via `auth.role-mappings` or `roles: [SELF_CERTIFY]` in local
   config. This is the org-level attestation that the user is trusted to self-certify at all.
2. A `SELF_CERTIFY` permission entry for the specific repo — the per-repo entitlement.

To set up a trusted solo contributor who approves their own work:

```yaml
# Step 1: grant the SELF_CERTIFY capability role (local auth example)
users:
  - username: bob
    password-hash: "{bcrypt}$2a$12$..."
    roles: [SELF_CERTIFY] # or via auth.role-mappings for LDAP/AD/OIDC

# Step 2: grant the per-repo entitlement
permissions:
  - username: bob
    provider: github/github.com
    path: /myorg/myrepo
    grant: PUSH
  - username: bob
    provider: github/github.com
    path: /myorg/myrepo
    grant: SELF_CERTIFY
```

Bob's pushes are validated as normal (commit rules, secret scanning, identity checks). Once validation passes, the proxy
records a self-certification in the audit log and forwards without waiting for a reviewer.

If Bob also needs to review others' pushes to that repo, add a third entry with `grant: REVIEW`.

### Path matching

Paths default to exact (`LITERAL`) matching. Use `path-type` for wildcards:

```yaml
# GLOB — all repos under an owner
- username: alice
  provider: gitlab/gitlab.com
  path: /myorg/*
  path-type: GLOB
  grant: PUSH

# REGEX — Java regex matched against /owner/repo
- username: alice
  provider: github/github.com
  path: \/myorg\/service\-.*
  path-type: REGEX
  grant: PUSH
```

### Permissions vs access rules

A user with `PUSH` permission on `/myorg/myrepo` can still be blocked if `/myorg/myrepo` is not in `rules.allow`. Both
must be satisfied. The distinction:

- **Access rules** → "does the proxy route this repo at all?" — operator policy
- **Permissions** → "can this user push to it?" — per-user grant

A wildcard allow rule (`slugs: ["*/*"]`) effectively means "route everything" and shifts all control to the permissions
layer. A tightly scoped allow rule means you do not need to worry about accidentally granting a user permission to a
repo the proxy does not handle.

---

## Access rules

```yaml
rules:
  allow:
    - enabled: true
      order: 110
      operation: [FETCH, PUSH]
      providers: [github/github.com]
      slugs:
        - /myorg/repo-one
        - /myorg/repo-two

  deny:
    - enabled: true
      order: 100 # deny rules with lower order numbers take precedence
      operation: [PUSH]
      slugs:
        - /myorg/archived-repo
```

Rules are evaluated in `order` number order (lower = earlier). Deny rules override allow rules at the same order number.
The proxy is **default-deny**: if no allow rule matches, the request is rejected.

`operation` scopes a rule to `PUSH`, `FETCH`, or both. A repo can be open for fetch but restricted for push.

---

## Approval mode

```yaml
server:
  approval-mode: auto # auto | ui
```

| Mode   | Behaviour                                                                                                                                                                                            |
| ------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `auto` | Clean pushes are immediately approved and forwarded after validation. No reviewer needed. Good for teams that use validation as a guardrail without a manual review step, and for solo contributors. |
| `ui`   | Every push enters `PENDING` state and waits for a reviewer to approve or reject in the dashboard. The `git push` command stays open until a decision is made.                                        |

`SELF_CERTIFY` permission interacts with `ui` mode: users with the capability and per-repo entitlement can self-review
their own push in the dashboard. The review step still happens — they attest to and record their own approval. This
signals to operators and the audit log that the pusher has reviewed and accepted responsibility for the changes. Other
users' pushes still require a peer reviewer.

The dashboard module (`fogwall-dashboard`) always uses `ui` mode. The standalone server module defaults to `auto`.

---

## Logging

### Default log locations

| Environment         | Log output                                          |
| ------------------- | --------------------------------------------------- |
| `./gradlew run`     | `fogwall-server/logs/application.log` + console     |
| Docker / production | console only (stdout); redirect or use a log driver |

The default Log4j2 config logs `com.rbc.fogwall` at `DEBUG` and everything else at `INFO`.

### Enabling debug logging for specific subsystems

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

### Debug profiles by problem area

#### OIDC / Spring Security authentication failures

`docker/log4j2-debug.xml` is included for this. Activate it in Docker Compose:

```yaml
volumes:
  - ./docker/log4j2-debug.xml:/app/conf/log4j2-debug.xml:ro
environment:
  JAVA_TOOL_OPTIONS: -Dlog4j2.configurationFile=/app/conf/log4j2-debug.xml
```

This enables `DEBUG` on `org.springframework.security` and `org.springframework.web.client`. Remove it when done — it is
very chatty.

#### JGit HTTP transport (upstream push/fetch failures)

Add to your `log4j2.xml`:

```xml
<Logger name="org.eclipse.jgit" level="DEBUG"/>
<Logger name="org.eclipse.jgit.http.server" level="DEBUG"/>
<Logger name="org.eclipse.jgit.transport" level="DEBUG"/>
```

Produces detailed output for each step of the JGit credential negotiation and pack transfer. Useful when a push reaches
the proxy but fails forwarding to upstream.

#### Jetty request handling (incoming connections, servlet dispatch)

```xml
<Logger name="org.eclipse.jetty" level="DEBUG"/>
<Logger name="org.eclipse.jetty.server" level="DEBUG"/>
<Logger name="org.eclipse.jetty.http" level="DEBUG"/>
```

#### Upstream HTTP client (transparent proxy mode)

```xml
<Logger name="org.eclipse.jetty.client" level="DEBUG"/>
```

Logs each HTTP request and response made by Jetty's `ProxyServlet` to the upstream. Useful when the transparent proxy
path (`/proxy/`) fails to reach the upstream.

### Reading logs for a failed push

Each push gets a `requestId` in the MDC (visible in the `[%X{requestId}]` field in the log pattern). To follow a single
push through the log:

```bash
grep "your-request-id" logs/application.log
```

The `requestId` is also printed in the sideband output to the git client, so you can match terminal output to log lines.

<!-- prettier-ignore-start -->
> [!NOTE]
> **Roadmap:** OpenTelemetry tracing support (propagating trace/span IDs into the log MDC and exporting spans to a collector) is tracked in [#106](https://github.com/RBC/fogwall/issues/106). Once implemented, the `requestId` will be correlatable across distributed systems without manual log grepping.
<!-- prettier-ignore-end -->

### Git client output formatting

Two environment variables control the `remote:` sideband messages sent to git clients during a push:

| Variable           | Effect                                                                                                          |
| ------------------ | --------------------------------------------------------------------------------------------------------------- |
| `NO_COLOR`         | Disables ANSI colour. Follows the [no-color.org](https://no-color.org) convention — set to any non-empty value. |
| `FOGWALL_NO_EMOJI` | Replaces emoji (✅ ❌ ⛔ 🔑) with plain ASCII. Useful for CI systems or terminals that do not render Unicode.   |

Set on the server process, not on the client. See
[CONFIGURATION.md — Git client output](CONFIGURATION.md#git-client-output) for Docker Compose examples.

---

## JGit filesystem requirements

JGit requires write access to two locations at runtime. Failures here produce cryptic errors that look like git
transport problems but are actually filesystem permission issues.

### Home directory

JGit reads `~/.gitconfig` and writes lock files in `$HOME`. In a container, `HOME` must point to a writable directory.

The Docker image sets `ENV HOME=/app/home` and creates `/app/home` with correct permissions. If you override the image's
entrypoint or run under a different UID, verify that `$HOME` is writable:

```bash
# Inside the container:
ls -la $HOME
touch $HOME/.test && rm $HOME/.test   # must succeed
```

**OpenShift / arbitrary UID:** OpenShift runs containers as a random UID by default. The image is built with GID 0
group-write on `/app/home`, `/app/.data`, and `/app/logs` (`chmod g+rwX`) so that any UID in group 0 can write to them.
If you see `Permission denied` errors on startup, check whether your security context is overriding the GID.

### `/tmp` for scratch repos and gitleaks

JGit creates temporary bare repositories in `java.io.tmpdir` (defaults to `/tmp`) for store-and-forward pushes and for
transparent proxy diff inspection. Gitleaks also writes temporary files there.

If `/tmp` is not writable (e.g. `noexec` mount, read-only root filesystem), override the JVM temp dir:

```yaml
environment:
  JAVA_TOOL_OPTIONS: -Djava.io.tmpdir=/app/.data/tmp
```

And create the directory in your deployment:

```bash
mkdir -p /app/.data/tmp
chmod 700 /app/.data/tmp
```

For Kubernetes with a `readOnlyRootFilesystem: true` security context, mount an `emptyDir` at `/tmp`:

```yaml
volumes:
  - name: tmp
    emptyDir: {}
volumeMounts:
  - name: tmp
    mountPath: /tmp
```

### Gitleaks binary permissions

When `secret-scan.enabled: true`, the proxy needs to execute the gitleaks binary. The bundled binary (inside the JAR) is
extracted to `java.io.tmpdir` at startup — that directory must allow executable files (`noexec` prevents this).

If the temp dir is `noexec`, point gitleaks at a writable, exec-allowed path:

```yaml
commit:
  secret-scan:
    enabled: true
    scanner-path: /app/.data/gitleaks # explicit path bypasses auto-extraction
```

Or pre-install gitleaks and put it on `PATH` — the proxy will find it via system path lookup before falling back to the
bundled binary.

---

## Externalized configuration

fogwall follows 12-factor config principles: the application ships with safe defaults baked into the JAR, and operators
layer environment-specific values on top without modifying the image.

### How config is loaded

Sources are merged in priority order (lowest → highest):

| Priority    | Source                                                  | Mechanism                                |
| ----------- | ------------------------------------------------------- | ---------------------------------------- |
| 1 (lowest)  | `fogwall.yml`                                           | Bundled in the JAR — base defaults       |
| 2           | Profile YAMLs named in `FOGWALL_CONFIG_PROFILES`        | Classpath lookup (see below)             |
| 3           | `FOGWALL_*` environment variables                       | Strip prefix, lowercase, `_` → `.`       |
| 4 (highest) | Hot-reload overlay (`reload.file.path` or `reload.git`) | Filesystem path; applied on every reload |

A higher-priority source only overrides the specific keys it defines — other base values are preserved.

### Profile-based config files — the `/app/conf/` pattern

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

### Environment variable overrides

Any `FOGWALL_` prefixed env var overrides the equivalent config key at the highest priority (above profiles, below
hot-reload overlays). The mapping is: strip `FOGWALL_`, lowercase, replace `_` with `.`:

```
FOGWALL_SERVER_PORT=9090              → server.port
FOGWALL_DATABASE_TYPE=postgres        → database.type
FOGWALL_SECRET__SCAN_ENABLED=false   → secret-scan.enabled
```

Use env vars for values that differ per-environment (secrets, hostnames, ports) and profile YAML files for structural
config (users, permissions, rules) that is too complex to express as a flat key-value pair.

### Hot-reload overlay

The `reload:` block configures a separate high-priority overlay that is re-read at runtime without restarting the
server. See [CONFIGURATION.md — Hot reload](CONFIGURATION.md#hot-reload) for the full reference.

The overlay file path can be a ConfigMap mount too:

```yaml
reload:
  file:
    enabled: true
    path: /app/conf/fogwall-runtime.yml
```

This lets operations teams push rule or permission changes by updating a ConfigMap and triggering
`POST /api/config/reload` — no pod restart needed.

---

## Network requirements

fogwall opens outbound connections to upstream SCM providers (GitHub, GitLab, Bitbucket, Gitea) from the **server**, not
from the developer's workstation. Your network team needs to allow egress from the proxy host, not from individual
developer machines.

### Outbound connections the proxy makes

| Path                                       | Library                  | Destination               |
| ------------------------------------------ | ------------------------ | ------------------------- |
| Store-and-forward upstream push (HTTPS)    | JGit Transport (HTTPS)   | SCM provider git endpoint |
| Store-and-forward upstream push (SSH)      | JGit Transport (SSH)     | SCM provider SSH endpoint |
| Transparent proxy forwarding               | Jetty HttpClient (HTTPS) | SCM provider git endpoint |
| SCM identity resolution (PAT verification) | Apache HttpClient 5      | SCM provider REST API     |
| SSH fingerprint lookup                     | Apache HttpClient 5      | SCM provider REST API     |

All paths must be able to reach the upstream SCM provider. A common operational mistake is opening the firewall for one
path but not the others — pushes appear to succeed locally but fail when the proxy tries to verify the committer's
identity via the API.

### Corporate HTTP proxy

If outbound internet access requires routing through a corporate HTTP proxy, set the standard environment variables
before starting fogwall:

```bash
export HTTPS_PROXY=http://proxy.corp.example.com:8080
export HTTP_PROXY=http://proxy.corp.example.com:8080
export NO_PROXY=localhost,127.0.0.1,*.internal.example.com
```

fogwall reads these at startup and configures all three outbound paths accordingly. No YAML config is needed.

Many organisations run a local proxy relay (cntlm, Alpaca) that handles NTLM/Kerberos authentication to the corporate
proxy transparently. Point `HTTPS_PROXY` at the relay (e.g. `http://localhost:3128`) and fogwall will work without any
auth configuration.

<!-- prettier-ignore-start -->
> [!NOTE]
> Full proxy authentication (Basic, NTLM) in YAML config is tracked in [#158](https://github.com/RBC/fogwall/issues/158).
<!-- prettier-ignore-end -->

### Connectivity diagnostics (dashboard)

The dashboard admin panel includes a **Provider Connectivity** section (`Admin → Provider Connectivity`) that runs
layered outbound checks against each configured provider. Use this to generate a sharable diagnostic report for your
network team without requiring them to access server logs.

**Baseline check** (all providers): for each provider runs in sequence and stops at the first failure:

1. **TCP** — opens a socket to `host:port` (5 s timeout). Classifies the outcome as REFUSED, TIMEOUT, or RESET so a
   firewall DROP vs REJECT is immediately distinguishable.
2. **TLS** — completes the TLS handshake and reports the negotiated protocol, cipher suite, and peer certificate CN.
   Detects MITM/SSL-inspection appliances that swap the upstream certificate.
3. **HTTP** — sends `GET /` and records the HTTP status code and response time.

**Targeted check** (single provider + optional repo path): runs the same three steps, then adds:

4. **Git probe** — sends `GET /info/refs?service=git-upload-pack` and `GET /info/refs?service=git-receive-pack` with a
   `User-Agent: git/2.x.x` header. Any HTTP response (200, 401, 403 …) means the request reached the upstream — git URL
   patterns and the git user-agent are not being filtered. A TIMEOUT or RESET after TCP/TLS passed indicates a DLP
   appliance blocking git-specific traffic specifically.

The targeted check returns a structured `steps` log in the API response (`GET /api/admin/connectivity?provider=<name>`)
that can be copied directly into a ticket for the network team.

### DLP appliances and non-GET blocking

Some enterprises deploy DLP (Data Loss Prevention) appliances that inspect or selectively block outbound HTTPS traffic.
A common policy blocks anything other than GET requests to `github.com` or similar SCM hosts — this will prevent fogwall
from forwarding pushes upstream even if the proxy can reach the host.

Symptoms: clones through the proxy succeed, but pushes fail at the upstream forwarding step with a 403 or a TCP reset.
The git probe in the targeted connectivity check will show this as a TIMEOUT or RESET on the `git-receive-pack` step
after TCP and TLS both pass.

**Resolution:** work with your network team to allowlist the proxy server's egress IP for POST/PUT traffic to the SCM
provider's git endpoint. A transparent HTTPS inspection proxy (MITM) will also break JGit's certificate pinning — the
proxy host's egress IP should bypass SSL inspection, not just be allowlisted at the IP layer.

---

## Production checklist

### Database

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
```

Schema is applied automatically via Flyway on startup.

### TLS

Put fogwall behind a reverse proxy (nginx, Caddy, Envoy) for TLS termination in production. The application can also
terminate TLS directly if preferred — see [CONFIGURATION.md — TLS](CONFIGURATION.md#tls).

For upstream connections to internal GitLab/Bitbucket/Forgejo instances with a corporate CA:

```yaml
server:
  tls:
    trust-ca-bundle: /etc/fogwall/tls/internal-ca.pem
```

This merges the corporate CA with the JVM's built-in trust anchors so public providers (GitHub, GitLab SaaS) continue to
work without changes.

### Health check

The dashboard module exposes an unauthenticated health endpoint:

```text
GET /api/health   → 200 OK with status payload when the server is up
```

The standalone server module (`fogwall-server`) does not expose a health endpoint — use a TCP check against the proxy
port instead.

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

### Session timeout

Default session lifetime is 24 hours. Tighten for compliance environments:

```yaml
auth:
  session-timeout-seconds: 28800 # 8 hours
```

### API key

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

---

## SSH transport

fogwall can accept pushes over SSH on port 2222 (default). This is an alternative to the HTTP push path — not a
replacement. SSH transport and HTTP transport run side-by-side; a provider can be reached via either or both.

### How SSH identity verification works

The SSH push path enforces the **same compliance guarantee** as the HTTP path — every push is tied to a verified SCM
user — but the mechanism is different because there is no token available:

1. **Inbound MINA auth (connection gate):** the client's public key must be registered in the pusher's fogwall profile
   (`ssh-keys`). This is equivalent to HTTP Basic auth — it authenticates the proxy user.
2. **SCM identity verification (compliance gate):** fogwall calls the provider REST API to fetch the SSH public keys
   registered by each SCM identity linked to the proxy user, then checks whether the connecting key's SHA-256
   fingerprint is among them. If it is, the push record's `scmUsername` is set to the matching SCM login. If it is not,
   **the push is blocked** — the same outcome as a failed token verification on the HTTP path.

Both steps are required. Step 1 alone is not sufficient — a key registered only in fogwall (but not on the SCM) will
clear MINA auth but fail step 2.

**Provider support:** fingerprint lookup is implemented for GitHub, GitLab, Forgejo, and Gitea. Providers that do not
implement this lookup (Bitbucket, generic proxy) will block all SSH pushes fail-closed. SSH is intentionally not
supported for those providers until a compliant identity verification path exists.

### Configuring an SSH provider

An SSH provider is a standard provider entry with an SSH-scheme URI. For a self-hosted Gitea instance:

```yaml
providers:
  gitea-ssh:
    type: forgejo
    uri: ssh://git@gitea.corp.example.com # SSH transport URI — routes inbound git-receive-pack
    api-uri: http://gitea.corp.example.com:3000 # only needed when HTTP API port differs from standard
    api-token: <service-account-PAT> # see below
```

The `uri` field controls SSH push routing — the path clients use is
`ssh://fogwall-host:2222/<provider-host>/<org>/<repo>.git`. The `api-uri` is only required when the HTTP API port cannot
be derived from the `uri` (see below).

Permissions and access rules for SSH providers work identically to HTTP providers:

```yaml
permissions:
  - username: alice
    provider: gitea-ssh
    match:
      target: SLUG
      value: /myorg/.*
      type: REGEX
    grant: PUSH
```

### SCM identity link for SSH

Users must have an `scm-identities` entry for the SSH provider (in addition to any HTTP provider entry). The provider ID
is the provider's name in the `providers:` block:

```yaml
users:
  - username: alice
    scm-identities:
      - provider: gitea-ssh # must match the provider name exactly
        username: alice-gitea
```

This is separate from any HTTP provider identity because the provider IDs are different. A user pushing via both HTTP
and SSH will have two identity entries — one for the HTTP provider, one for the SSH provider.

### The `api-token` requirement

The provider REST API is called to fetch SSH public keys for registered SCM identities. GitHub's endpoint
(`GET /users/{login}/keys`) is public — no token is needed. Forgejo and GitLab require authentication when the instance
is configured with `REQUIRE_SIGNIN_VIEW=true` (common in corporate deployments where the git server is not publicly
accessible).

Create a service account on the upstream SCM and generate a PAT with `read:user` scope (Forgejo) or `read_user` scope
(GitLab). This account does not need repository access — it only needs to list user SSH public keys. Set the token in
the provider config:

```yaml
providers:
  gitea-ssh:
    type: forgejo
    uri: ssh://git@gitea.corp.example.com
    api-token: <service-account-PAT>
```

There is no environment variable override for `api-token` (the env var mechanism does not support hyphenated config
keys). Use a profile config file to supply the token outside of the checked-in base config:

```yaml
# /app/conf/fogwall-local.yml  (mounted into the container, not committed)
providers:
  gitea-ssh:
    api-token: gta_xxxxx
```

### `api-uri` — when it is needed

For production deployments where SSH and HTTPS share the same hostname (the normal case), the HTTP API URL is derived
automatically from the SSH URI: `ssh://git@host` → `https://host/api/v1`. No `api-uri` is needed.

`api-uri` is only required when the HTTP API runs on a non-standard port that cannot be inferred from the SSH URI — for
example, a local development Gitea where SSH is on 3022 and HTTP is on 3000:

```yaml
gitea-ssh:
  type: forgejo
  uri: ssh://git@localhost:3022
  api-uri: http://localhost:3000
```

### Requiring agent forwarding

Fogwall uses the client's forwarded SSH agent to authenticate outbound SSH connections to the upstream SCM. The client
**must** connect with `ssh -A` (or `ForwardAgent yes` in `~/.ssh/config`). If agent forwarding is absent, the push is
blocked with a clear error:

```text
error: SSH agent forwarding required — connect with 'ssh -A' or set 'ForwardAgent yes' in ~/.ssh/config
```

There is no configuration to disable this requirement — fogwall never reads local identity files for upstream auth.

---

## Common operational problems

### Push is rejected with "repository not permitted"

Check both layers:

1. Is the repo in `rules.allow`? Verify the slug matches exactly (including leading `/`).
2. Does the user have a `permissions` entry for this provider + path with `grant: PUSH`?

### Push hangs waiting for approval indefinitely

The server is in `ui` mode and no reviewer has approved the push. Either:

- Any authenticated user (other than the pusher) can open the push record and approve it in the dashboard.
- Or grant the pusher `SELF_CERTIFY` permission so they can approve their own clean pushes.

If `require-review-permission: true` is set, only users with an explicit `REVIEW` permission entry for the repository
can approve.

### Push blocked: identity not linked

The proxy cannot match the token to a registered proxy user. This check is always enforced — `identity-verification`
mode does not affect it. Check:

1. Does the user's profile have an `scm-identities` entry for the correct provider?
2. Does the token have the required API scope to call `GET /user`?

### SSH push rejected: SSH key not linked to any SCM identity

The fingerprint of the connecting SSH key was not found among the keys registered on the upstream SCM for the linked SCM
identity. Possible causes:

1. The key is in fogwall (`ssh-keys`) but not on the upstream SCM account. Have the user add it in their SCM account
   settings.
2. The linked `scm-identities` entry refers to the wrong provider or username. The provider must be the SSH provider
   name (e.g. `gitea-ssh`), not the HTTP provider name (`gitea`).
3. The `api-token` is missing or has expired, causing the key lookup to return an empty list. Check the server log for
   `Failed to fetch SSH keys for ... user '...'` and renew the token.

### SSH push rejected: SSH identity verification not supported by provider

The provider used for this push does not implement SSH fingerprint lookup (e.g. `type: generic`). The SSH path is
fail-closed — pushes are blocked unless the provider can verify the connecting key against the SCM user's registered
keys. Switch to a supported provider type (`forgejo`, `gitlab`, or `github`). Opt-in fail-open behaviour for unsupported
providers is planned as a follow-up feature.

### Push blocked or warned: commit email mismatch

One or more commit author/committer emails are not registered to the authenticated user. This is controlled by
`identity-verification`:

- In `strict` mode the push is blocked. Check that the user's email list includes the address they commit with
  (`git config user.email`), and that the commits were not authored by someone else.
- In `warn` mode the push goes through but the mismatch is logged and visible in the push record. Switch to `strict`
  once you are confident emails are populated for all users.

### OIDC login fails / redirect loop

1. Enable the Spring Security debug profile (`docker/log4j2-debug.xml`) — see
   [Debug profiles](#debug-profiles-by-problem-area).
2. Check the redirect URI registered in the IdP matches `https://<your-host>/login/oauth2/code/fogwall` exactly.
3. For Entra ID: verify `jwk-set-uri` is set — without it, token issuer validation fails silently.

### Upgrading from a pre-1.2.0 deployment: OIDC redirect URI mismatch (AADSTS50011)

The project was renamed in 1.2.0, which changed the Spring Security OAuth2 registration ID from `gitproxy` to `fogwall`.
This shifts the callback URL that fogwall sends to the IdP in the authorization request:

| Version | Redirect URI sent to IdP                    |
| ------- | ------------------------------------------- |
| < 1.2.0 | `https://<host>/login/oauth2/code/gitproxy` |
| ≥ 1.2.0 | `https://<host>/login/oauth2/code/fogwall`  |

**Fix:** add the new URI to your IdP app registration alongside the existing one. In Entra ID: App registrations → your
app → Authentication → add `https://<host>/login/oauth2/code/fogwall` as a redirect URI. Both URIs can coexist — remove
the old one once all deployments are on 1.2.0+.

### Gitleaks produces no output / scan appears to be skipped

Check `logs/application.log` for lines containing `gitleaks`. The log will show which binary path was resolved and
whether the scan ran. If the binary cannot be executed (permission denied, noexec mount), the proxy falls back to
skipping the scan rather than failing the push — add `gitleaks` to PATH or set `scanner-path` explicitly.

### Push fails after approval with an upstream error (404, 403, etc.)

Once a push passes validation and is approved, the proxy forwards it to the upstream SCM transparently — no further
processing occurs. Any error from the upstream is passed straight back to the git client exactly as if the developer
were pushing directly.

Common upstream errors and their causes:

| Error                                    | Likely cause                                                                                                                                                                                                                |
| ---------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Repository not found` / 404             | The token does not have access to the repository. GitHub returns 404 (not 403) for both missing repos and insufficient permissions on private repos — this is intentional on GitHub's part to avoid leaking repo existence. |
| `403 Forbidden`                          | The token has repo access but lacks the required write scope (e.g. a fine-grained PAT missing `Contents: write`).                                                                                                           |
| `pre-receive hook declined`              | The upstream has its own server-side hooks that rejected the push. Nothing the proxy can do — the developer needs to resolve it upstream.                                                                                   |
| `remote: error: GH006: Protected branch` | The target branch has branch protection rules on the upstream. Again, upstream-side — not a proxy issue.                                                                                                                    |

These errors appear in the developer's terminal and in the push record in the dashboard. They are not logged as proxy
errors — from the proxy's perspective the forwarding succeeded.

**Diagnosing token scope issues:** if a push consistently fails with 404 or 403 immediately after approval, ask the
developer to test the same push directly (bypassing the proxy) with the same token. If it also fails direct, the problem
is the token — not the proxy.

### `Permission denied` on startup in a container

JGit failed to write to `$HOME` or `/tmp`. Verify:

```bash
docker exec <container> sh -c 'ls -la $HOME && touch $HOME/.probe && rm $HOME/.probe'
docker exec <container> sh -c 'touch /tmp/.probe && rm /tmp/.probe'
```

If either fails, see [JGit filesystem requirements](#jgit-filesystem-requirements) above.
