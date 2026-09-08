# Core abstractions

## Build stamp (`BuildInfo`)

`BuildInfo` names the version and commit the running process was built from. It lives in `fogwall-core` so both
applications read the same two values from one file, `fogwall-build.properties`, which Gradle expands at build time. The
dashboard's `version.properties` could not serve this purpose: it is absent from the standalone server's distribution,
and a second resource of that name would shadow it rather than supplement it.

The commit is supplied to the build rather than discovered by it — the container builder image has no `git` binary, so
`-PbuildCommit` (or `BUILD_COMMIT`) carries it in, falling back to a local `git rev-parse` for a developer build. Both
values degrade to `unknown` rather than failing a build, and an unexpanded `${...}` placeholder is treated as `unknown`
too, since that is what a classpath assembled without `processResources` yields. The expanded values are declared as
`processResources` inputs, without which the task goes `UP-TO-DATE` and the jar keeps shipping the previous build's
stamp.

## Provider (`FogwallProvider`)

A provider represents one upstream Git hosting service. It carries the upstream HTTP base URI, the URL path prefix the
proxy listens on, and optional API calls for identity resolution.

Built-in providers: `github`, `gitlab`, `bitbucket`, `forgejo`/`gitea`, `codeberg`. Custom generic providers can be
declared in config with an arbitrary name and URI.

**Transport is a property of the provider, not a separate entry.** A single provider entry can serve HTTP (its `uri`),
SSH (an `ssh:` sub-block exposing `getSshUri()`), or both — the `FogwallServletRegistrar` registers the HTTP servlets
for any provider with an HTTP URI, and the `SshServerRegistrar` registers an SSH route for any provider whose
`getSshUri()` is present, both keyed by the same `servletPath()`. Because both transports resolve to one provider name,
identity resolution, permissions, and OAuth links apply uniformly across them — there is no `github` / `github-ssh`
duplication.

Providers that implement `TokenIdentityProvider` can resolve an SCM username from a push token by calling the hosting
service's API (e.g. `GET /user` for GitHub). This is how the proxy maps a credential to a known identity without
requiring the developer to use their SCM username as the HTTP Basic username. These mappings are cached in the database
for performance & to avoid excess API calls to respect rate limits. The cache expires entries on the order of 7 days by
default - PAT tokens have a configurable lifespan, so this strikes a balance between keeping up with token changes and
minimizing API calls.

## Push store (`PushStore`)

Every push attempt produces a `PushRecord`. The record tracks the full lifecycle:
`RECEIVED → PENDING → APPROVED → FORWARDED`, or `RECEIVED → BLOCKED`, or `RECEIVED → PENDING → REJECTED`. It embeds an
ordered list of `PushStep` entries (one per validation step) and a list of commits.

The push store is the integration point for the approval workflow: the dashboard reads push records from it, writes
approvals/rejections to it, and the proxy polls it.

Backends: H2 (dev), PostgreSQL, MySQL, MariaDB, MongoDB, in-memory (testing).

## Approval gateway (`ApprovalGateway`)

Decouples the proxy from the approval mechanism. Two implementations today, with the interface designed for external
integrations:

- **`AutoApprovalGateway`** — clean pushes are approved immediately (no human review)
- **`UiApprovalGateway`** — proxy writes the push record and polls the store; a reviewer approves or rejects via the
  dashboard REST API

The `ApprovalGateway` interface is the extension point for external approval workflows — for example, a
`ServiceNowApprovalGateway` (planned) that would create a request ticket and wait for external approval before
forwarding the push.

## User store and identity

The proxy maintains its own user registry, separate from any upstream SCM accounts.

```
UserEntry (proxy user)
  ├── username + password hash (BCrypt / {noop} in dev/local auth modes)
  ├── emails[]          claimed email addresses (used for author attribution)
  ├── scmIdentities[]   links to upstream SCM accounts
  │     ├── provider    e.g. "github", "gitlab"
  │     └── username    the developer's SCM login
  └── roles[]           USER, ADMIN
```

When a developer pushes with `Authorization: Basic <token>`, the proxy:

1. Calls the provider API with the token to get the developer's SCM username.
2. Looks up a proxy user whose `scmIdentities` has a matching `(provider, scmUsername)` entry.
3. Uses the resolved `UserEntry` for permission checks and author attribution.
4. Records that SCM login on the push record, so the audit trail names the account the token belongs to. A user may hold
   several identities on one provider, and the match may have been made on email, in which case no identity on file
   carries the login at all.

Resolution results are cached in the database (7-day TTL by default).

Backends: static YAML list, JDBC (H2/Postgres), MongoDB, or a composite that checks both.
