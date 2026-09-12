# SCM API

_Available since v1.4.0, opt-in per provider — see [docs/internals/scm-api-proxy.md](../internals/scm-api-proxy.md)._

Extends fogwall past `git push` into the rest of the contribution lifecycle: proxying `gh`'s issue/PR
create-edit-comment-review traffic and `glab`'s issue/MR equivalent, reusing fogwall's existing identity resolution,
permission engine, and audit trail. See [the administrator guide](../admin/scm-api.md) for the operational model
(BYO-token, egress assumption) and [the user guide](../user/contributions.md) for how a developer points `gh`/`glab` at
it.

The providers use different wire formats under the hood — GitHub's is GraphQL with an opaque node ID that must be
resolved to `owner/repo`; GitLab's and Forgejo's are REST with the repository addressed directly in the URL, so no
resolution step exists for them (`node-id-cache-ttl` below is a GitHub-only setting, inert for the others). See
[docs/internals/scm-api-proxy.md](../internals/scm-api-proxy.md) for the per-provider wire-format detail. Every dialect
shares the same opt-in config shape — a global settings block, and a per-provider `enabled` flag that actually mounts
the path:

```yaml
scm-api:
  # TTL for GitHub's GraphQL node-ID -> owner/repo resolution cache (ISO-8601 duration). A SECURITY parameter, not
  # just a perf knob: a GraphQL node ID can outlive a repo rename/transfer while what it resolves to changes
  # underneath it. Conservative default; shorten further for a deployment where repo renames/transfers are unusually
  # frequent. Has no effect on GitLab, which addresses its target directly in the URL.
  node-id-cache-ttl: PT5M

providers:
  github:
    enabled: true
    scm-api:
      enabled: true # per-provider opt-in (default false)
      port: 9443 # required when enabled — a dedicated listener, see below
  gitlab:
    enabled: true
    scm-api:
      enabled: true
      port: 9444
  gitea:
    enabled: true
    scm-api:
      enabled: true
      port: 9445
      require-validated-head: false # relax the head-provenance check; default true
      merge-enabled: true # allow merging PR/MRs through this provider; default false
```

**Each enabled provider needs its own `port`**, and fogwall fails to start if one is enabled without it. The dialect is
mounted at the root of that listener (`/api/graphql`, `/api/v4/*`, `/api/v1/*`) because that is the only place the CLIs
can reach it: `gh` and `fj` address the API from the host root and silently discard any path prefix. A single shared
listener isn't an option either — every GitLab claims `/api/v4` and every Gitea/Forgejo `/api/v1`, so two instances of
the same platform would collide. Clients are then pointed at a plain host and port (`GH_HOST`, `GITLAB_HOST`,
`tea login add --url`, `fj -H`). See [docs/internals/scm-api-proxy.md](../internals/scm-api-proxy.md) for the per-CLI
evidence.

**TLS is inherited from [`server.tls`](tls.md); there is no per-provider TLS block.** When `server.tls` is set, every
SCM API listener serves HTTPS with the same certificate, on its own port. The CLIs only ever address a custom host over
HTTPS, so TLS has to terminate either at fogwall this way or at an ingress in front of it — with `server.tls` unset,
fogwall logs a warning naming each plaintext listener. See
[TLS on the SCM API listeners](../admin/scm-api.md#tls-on-the-scm-api-listeners).

The caller's `User-Agent` and the CLI version it advertises are recorded on every audit record — the anchor for noticing
a CLI upgrade has changed its wire format, which otherwise surfaces only as an unexplained denial. It is not used to
gate: `User-Agent` is caller-controlled, so nothing branches on it.

`require-validated-head` refuses a pull/merge request create whose head commit fogwall has no push record for — closing
the gap where a contributor pushes straight to their fork, never touching fogwall, then opens the pull request through
it. The lookup is keyed on the commit SHA alone, not the repository, since a fork push and the upstream pull/merge
request are two different repositories.

It defaults on, so a pull/merge request's head must trace to a push fogwall saw. Relax it (set `false`) where these
workflows are common: a rebase, amend, or force-push after pushing through fogwall changes the SHA a validated push
recorded, and a commit authored in the SCM's own web UI never went through fogwall at all — each leaves the head with no
matching push record. A denial names the remedy — push the branch through fogwall, then reopen — rather than just
refusing.

`merge-enabled` allows merging a pull/merge request through this provider's SCM API proxy (`gh pr merge`,
`glab mr merge`, `tea`/`fj pr merge`). It defaults off because merge is the highest-consequence operation on this path,
so exposing it is an explicit operator decision rather than a side effect of enabling the SCM API proxy. It is
**independent of the `MERGE` grant, and both are required**: the capability must be enabled here, and the caller must
hold the grant (see [Permissions](permissions.md)). With it off, a merge request is refused even for a caller who holds
`MERGE`.

Per-repo authorization for **mutations** (issue/PR create, edit, comment, review) goes through the existing
`RepoPermission` grants — see [Permissions](permissions.md) below — with a dedicated `PROPOSE` grant kept independent
from `PUSH`/`REVIEW`, so an operator can permission git-push and SCM API mutations separately. Merging a pull/merge
request is its own grant, `MERGE`, independent again from `PROPOSE` — see
[the administrator guide's merging section](../admin/scm-api.md#merging):

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

## SCM API properties

| Property                                          | Type    | Default | Description                                                                                                                              |
| ------------------------------------------------- | ------- | ------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| `scm-api.node-id-cache-ttl`                       | string  | `PT5M`  | ISO-8601 duration. See the security note above.                                                                                          |
| `providers.<name>.scm-api.enabled`                | boolean | `false` | Whether the SCM API proxy is mounted for this provider.                                                                                  |
| `providers.<name>.scm-api.port`                   | int     | —       | Dedicated listener port. **Required** when `enabled`; startup fails without it.                                                          |
| `providers.<name>.scm-api.require-validated-head` | boolean | `true`  | Refuse a pull/merge request whose head commit has no fogwall push record. Relax where rebase/amend/force-push/web-UI commits are common. |
| `providers.<name>.scm-api.merge-enabled`          | boolean | `false` | Allow merging PR/MRs through this provider. Independent of the `MERGE` grant; both are required.                                         |

## Token model

The CLI carries a personal access token the user supplies. fogwall forwards it upstream unchanged after inspecting the
request, and never mints or supplies a credential for this path.

[SCM OAuth](scm-oauth.md) is a separate mechanism, for fogwall-managed operations rather than external tooling. It does
not provide a token for a CLI. See [the administrator guide](../admin/scm-api.md) for the egress assumption this relies
on.
