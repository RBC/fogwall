# Proposals

_Available since v1.4.0, opt-in per provider — see [docs/internals/scm-api-proxy.md](../internals/scm-api-proxy.md)._

Extends fogwall past `git push` into the rest of the contribution lifecycle: proxying `gh`'s issue/PR
create-edit-comment-review traffic and `glab`'s issue/MR equivalent, reusing fogwall's existing identity resolution,
permission engine, and audit trail. See [the administrator guide](../admin/proposals.md) for the operational model
(BYO-token, egress assumption) and [the user guide](../user/proposals.md) for how a developer points `gh`/`glab` at it.

The providers use different wire formats under the hood — GitHub's is GraphQL with an opaque node ID that must be
resolved to `owner/repo`; GitLab's and Forgejo's are REST with the repository addressed directly in the URL, so no
resolution step exists for them (`node-id-cache-ttl` below is a GitHub-only setting, inert for the others). See
[docs/internals/scm-api-proxy.md](../internals/scm-api-proxy.md) for the per-provider wire-format detail. Every dialect
shares the same opt-in config shape — a global settings block, and a per-provider `enabled` flag that actually mounts
the path:

```yaml
proposals:
  # TTL for GitHub's GraphQL node-ID -> owner/repo resolution cache (ISO-8601 duration). A SECURITY parameter, not
  # just a perf knob: a GraphQL node ID can outlive a repo rename/transfer while what it resolves to changes
  # underneath it. Conservative default; shorten further for a deployment where repo renames/transfers are unusually
  # frequent. Has no effect on GitLab, which addresses its target directly in the URL.
  node-id-cache-ttl: PT5M

providers:
  github:
    enabled: true
    proposals:
      enabled: true # per-provider opt-in (default false)
      port: 9443 # required when enabled — a dedicated listener, see below
  gitlab:
    enabled: true
    proposals:
      enabled: true
      port: 9444
  gitea:
    enabled: true
    proposals:
      enabled: true
      port: 9445
      require-known-cli: true # optional hardening; default false
```

**Each enabled provider needs its own `port`**, and fogwall fails to start if one is enabled without it. The dialect is
mounted at the root of that listener (`/api/graphql`, `/api/v4/*`, `/api/v1/*`) because that is the only place the CLIs
can reach it: `gh` and `fj` address the API from the host root and silently discard any path prefix. A single shared
listener isn't an option either — every GitLab claims `/api/v4` and every Gitea/Forgejo `/api/v1`, so two instances of
the same platform would collide. Clients are then pointed at a plain host and port (`GH_HOST`, `GITLAB_HOST`,
`tea login add --url`, `fj -H`). See [docs/internals/scm-api-proxy.md](../internals/scm-api-proxy.md) for the per-CLI
evidence.

**TLS is inherited from [`server.tls`](tls.md); there is no per-provider TLS block.** When `server.tls` is set, every
proposals listener serves HTTPS with the same certificate, on its own port. The CLIs only ever address a custom host
over HTTPS, so TLS has to terminate either at fogwall this way or at an ingress in front of it — with `server.tls`
unset, fogwall logs a warning naming each plaintext listener. See
[TLS on the proposals listeners](../admin/proposals.md#tls-on-the-proposals-listeners).

`require-known-cli` refuses callers whose `User-Agent` isn't one of the four recognised SCM CLIs — browsers, bare
`curl`, unrecognised automation. It is **hardening, not a security boundary**: `User-Agent` is caller-controlled, so the
setting can only deny a request that would otherwise be allowed, never permit one the allowlist and permission engine
would refuse. It defaults off because a CLI release that changes its `User-Agent` format would otherwise start failing
for reasons unrelated to policy. The raw header is recorded on every audit record either way, since each CLI advertises
its version there.

Per-repo authorization for **mutations** (issue/PR create, edit, comment, review) goes through the existing
`RepoPermission` grants — see [Permissions](permissions.md) below — with a dedicated `PROPOSE` grant kept independent
from `PUSH`/`REVIEW`, so an operator can permission git-push and SCM API mutations separately:

```yaml
permissions:
  - username: alice
    provider: github
    match:
      value: /acme/widgets
    grant: PROPOSE
```

**Reads** (GraphQL `query` traffic — an ordinary `gh issue list`, for example) are not gated by `PROPOSE`. They are
forwarded for any authenticated caller, which keeps the default read cost near pass-through — no allowlist, no node-ID
resolution, no extra round-trip.

## Proposal properties

| Property                                       | Type    | Default | Description                                                                               |
| ---------------------------------------------- | ------- | ------- | ----------------------------------------------------------------------------------------- |
| `proposals.node-id-cache-ttl`                  | string  | `PT5M`  | ISO-8601 duration. See the security note above.                                           |
| `providers.<name>.proposals.enabled`           | boolean | `false` | Whether the SCM API proxy is mounted for this provider.                                   |
| `providers.<name>.proposals.port`              | int     | —       | Dedicated listener port. **Required** when `enabled`; startup fails without it.           |
| `providers.<name>.proposals.require-known-cli` | boolean | `false` | Refuse callers whose `User-Agent` isn't a recognised SCM CLI. Subtractive hardening only. |

## Token model

The CLI carries a personal access token the user supplies. fogwall forwards it upstream unchanged after inspecting the
request, and never mints or supplies a credential for this path.

[SCM OAuth](scm-oauth.md) is a separate mechanism, for fogwall-managed operations rather than external tooling. It does
not provide a token for a CLI. See [the administrator guide](../admin/proposals.md) for the egress assumption this
relies on.
