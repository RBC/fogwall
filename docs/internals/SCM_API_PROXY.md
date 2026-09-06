# SCM API Proxy — design notes (#264)

This is a contributor-facing design note for the SCM API proxy — a policy-enforcing proxy for the SCM command-line tools
(`gh`, `glab`, `tea`, `fj`) that covers the contribution lifecycle _after_ `git push`: creating and iterating on issues
and pull/merge requests without leaving the CLI.

For what is actually shipped and how to configure/operate it, see
[docs/ARCHITECTURE.md](../ARCHITECTURE.md#proposals-a-dedicated-listener-per-provider),
[docs/ADMIN_GUIDE.md](../ADMIN_GUIDE.md#proposals), and [docs/CONFIGURATION.md](../CONFIGURATION.md#proposals) — those
are the living, current-state docs. This file is design rationale and the per-provider wire-format reference; it does
not track a point-in-time "status," since that goes stale the moment it's written. Two decisions worth noting against
the design below: the per-provider `ScmApiAccessRule` rule system (§ below assumed URL-rule reuse; built as its own
model instead — GraphQL traffic has no URL path to match, see the ARCHITECTURE.md note) is provider-level only for reads
(no per-repo read gating yet), and mutations are permissioned through a dedicated `RepoPermission.Grant.PROPOSE`,
independent of `PUSH`/`REVIEW`.

It extends fogwall from a git-push filter into a contribution-lifecycle gateway (CLAUDE.md's "SCM control plane" /
"inner-source enablement"), reusing fogwall's identity resolution, permission/rule engine, and audit trail rather than
standing up a separate service.

The document is split into a **provider-agnostic core** (the pipeline every CLI shares) and **per-provider sections**
(one dialect per SCM). GitHub/`gh` and GitLab/`glab` are filled in from verified live captures; Gitea/`tea` and
Forgejo/`fj` are stubs to be reverse-engineered with the [repeatable capture recipe](#appendix-capturing-a-new-provider)
at the end.

---

## Positioning & constraints

- **Opt-in module.** Per CLAUDE.md, this must not raise the baseline complexity or per-push cost for operators who don't
  use it. It is a distinct subsystem gated behind explicit config, not something folded into the default hook or filter
  chain.
- **Token model — the CLI brings its own token.** The credential a CLI carries is a personal access token the user
  supplies. fogwall forwards it upstream unchanged and never mints or supplies one.
- **SCM OAuth (#40) is for fogwall-managed operations.** Today that is the account-linking UI; later it may cover
  fogwall acting on a user's behalf, such as deferred forwarding of pushes or pushing on their behalf. It stays within
  fogwall's own interactions with an SCM and is not a credential source for external tooling — including a CLI proxied
  through this surface. Anything fogwall renders and submits itself does use it — the participant issue form in #563,
  and any future fogwall-hosted proposal UI. Those surfaces need no PAT from the user at all.
- **Enforcement is content interception, not the credential.** A developer with a valid PAT can already reach the
  upstream API directly; fogwall governs the _sanctioned_ API host and inspects what passes through it. That only
  constrains anything if direct access to the SCM API is blocked elsewhere — organization-wide egress control is the job
  of a web proxy or network security appliance, not fogwall. Same model as the git-push path.
- **Auditability.** One audit record per proxied _mutation_, same bar as the push path — who, which rule, what target,
  what evidence.
- **Designed for the provider CLIs.** Each dialect implements the request shapes its CLI actually emits — the GitHub
  allowlist covers the GraphQL mutations `gh` sends, not GitHub's REST API, which `gh` does not use for these
  operations. Other clients are possible but not intended: it would mean hand-rolling requests to match what a given CLI
  sends. This is not a general SCM API reverse proxy.
- **Future scope — client-type dispatch on `User-Agent`.** The header is already recorded per request as the version
  anchor (`GitHub CLI <version> …`, `glab/<version> …`) and can optionally refuse unrecognised clients. It could later
  select behaviour more finely — reject a browser outright, apply a different policy to a script. Not designed or
  implemented.

---

## Provider-agnostic pipeline

Every provider dialect runs the same ordered pipeline. Only the _parsing_ and _target-resolution_ steps are
provider-specific; the policy decisions reuse existing fogwall machinery.

```
CLI request (API call to fogwall host)
      │
      ▼
1. Authenticate      resolve the caller's identity from their forwarded token
      │              (reuse fogwall's existing identity resolution — token in, login out)
      ▼
2. Classify          parse the request → read op (allowed) or write op (mutation)?
      │              GraphQL: parse the AST; REST: method + path template
      ▼
3. Allowlist-check   is this a supported mutation? default-deny / fail closed
      │
      ▼
4. Resolve target    map the operation to a concrete owner/repo
      │              (may require an opaque-ID → repo resolution — see below)
      ▼
5. Authorize         run owner/repo + operation through the EXISTING permission/rule engine
      │
      ▼
6. Forward + audit   relay to upstream verbatim; write one audit record for the mutation
```

Three mechanics recur across providers and are the heart of the design:

### 3a. Fail-closed allowlisting on the _parsed operation_

The operation being performed is in the request **body** (a GraphQL mutation field, or a REST method+path), not reliably
in the URL alone. The allowlist must parse the operation out of the body and **default-deny** anything it does not
recognize.

> **Security note.** We parse GraphQL with a library and allowlist on the parsed mutation field, rather than string-
> matching the raw query text. String matching is a security bug waiting to happen: a field alias, a string literal, or
> a batched second mutation can carry the expected substring while the real operation differs.

### 3b. Multi-request fan-out

A single CLI command is several API calls: one or more **read** lookups followed by the **write** mutation. The
allowlist must permit the whole chain — reads flow freely (default-allow for `query`/GET-of-metadata), the mutation is
gated. Do not assume one command == one request.

### 3c. Opaque-ID → owner/repo resolution (with a cache)

Write operations frequently reference their target by an **opaque node/object ID**, not by `owner/repo`. So the
authorization target cannot be read from the URL — it must be resolved to a concrete repo before the permission engine
can run.

- **Resolve** the opaque ID to `owner/repo` (a provider-specific lookup — e.g. a GraphQL `node(id:)` query, or a REST
  object fetch), using the caller's own token.
- **Cache** the `id → owner/repo` mapping. IDs are stable enough that a developer's many calls against the same repo
  reuse one resolution.
- **The cache TTL is a security parameter, not just a perf knob.** An opaque ID can outlive a rename/transfer while the
  `owner/repo` it resolves to changes underneath it — a stale entry would authorize against the _old_ identity. Use a
  modest, configurable TTL with a conservative default. Renames/transfers are rare, so a short TTL keeps ~all the perf
  benefit while bounding the stale-authorization window.
- **Best path — seed the cache from the preceding lookup.** The read lookup that a CLI fires _before_ its mutation
  typically already carries `owner/repo` (in its request) and returns the opaque ID (in its response). fogwall passes
  both through, so it can populate the `id → owner/repo` cache from the caller's own traffic before the mutation arrives
  — often eliminating the extra resolution call entirely. The explicit resolve is the cold-cache fallback. Treat the
  upstream response as data (it comes from the SCM via the caller's token), and still bound it with the TTL.

Fits the existing fogwall cache pattern (`JdbcScmTokenCache`, `JdbcSshFingerprintCache`, `LocalRepositoryCache`).

### Cost at high volume

The only added upstream work is the resolution call in 3c, and only on a cold cache, and only for mutations (reads are
forwarded untouched). That keeps the default per-request cost near pass-through, satisfying CLAUDE.md's "default path
must stay cheap." State the egress assumption and the mutations-only cost explicitly in the implementation.

---

## Client redirection — why each provider gets its own port

Every one of the four CLIs is redirected by **host configuration**, and none of them accepts a path. Verified by
pointing each binary at a local listener configured as `http://127.0.0.1:8099/scm-api/<provider>` and recording what
actually arrived:

| CLI    | sub-path mount | request observed                                                          |
| ------ | -------------- | ------------------------------------------------------------------------- |
| `gh`   | **discarded**  | `POST /api/graphql` — `GH_HOST` is a hostname; a path cannot be expressed |
| `glab` | preserved      | `GET /scm-api/gitlab/api/v4/projects/foo%2Fbar/issues`                    |
| `tea`  | preserved      | `GET /scm-api/gitea/api/v1/user`                                          |
| `fj`   | **discarded**  | `GET /api/v1/user`                                                        |

`tea` concatenates (`c.url + "/api/v1" + path`), so a base path survives. `fj` resolves `base.join("/api/v1/...")`, and
RFC 3986 makes an absolute reference replace the entire base path — silently, with no error.

So a shared `/scm-api/<provider>` prefix is unreachable for half the CLIs, and the dialects cannot simply share one root
listener either: every GitLab claims `/api/v4` and every Gitea/Forgejo `/api/v1`, so two instances of the same platform
would collide.

**Each SCM-API-enabled provider therefore gets its own listener**, with its dialect mounted at the root of a context
bound to that connector via Jetty's `"@connectorName"` virtual-host form. Clients are then configured with nothing but a
host and port — the one form all four accept:

```yaml
providers:
  github:
    proposals: { enabled: true, port: 9443 }
  gitlab:
    proposals: { enabled: true, port: 9444 }
  gitea:
    proposals: { enabled: true, port: 9445 }
```

```shell
GH_HOST=fogwall:9443 gh issue create -R fogwall:9443/<owner>/<repo> --title ... --body ...
GITLAB_HOST=fogwall:9444 glab issue create -R <owner>/<repo> --title ...
tea login add --name fogwall --url http://fogwall:9445 --token "$PAT"
fj -H http://fogwall:9445 issue create "title" --body "body"
```

One deployment detail that is not optional: the SCM API listeners relax Jetty's URI compliance for exactly one
violation, `AMBIGUOUS_PATH_SEPARATOR`. GitLab addresses a project as a single `owner%2Frepo` segment, and Jetty's
default rejects that encoded slash with a **400 before any fogwall code runs**. The git server and transparent-proxy
ports keep the strict default. The allowlists match the raw, still-encoded URI, so `%2F` stays inside one segment rather
than being decoded into an extra path element that could shift which repository is authorized.

## Provider: GitHub (`gh`) — VERIFIED

Filled in from live `gh` 2.98.0 traffic (`GH_DEBUG=api`) exercising the full issue/PR CRUD matrix against a test repo.

### Transport

- **Issue/PR CRUD is 100% GraphQL** — every create/edit/comment/review/close is a `POST` to the GraphQL endpoint
  (`/graphql` on github.com; `/api/graphql` on GHES). No REST (`/api/v3`) was used for any of these operations. REST
  remains relevant for _other_ `gh` commands, but the contribution lifecycle this feature targets is entirely GraphQL.
- **Every command is a 2–3 request fan-out:** one or more read `query`s, then one `mutation`.

### Client setup (no gh-specific server support needed)

`gh` is redirected purely by host configuration — the proxy just needs to be reachable at a host `gh` can be pointed at
and speak plain GraphQL/REST pass-through. `GH_HOST` holds a host (optionally with a port), never a path; see
[Client redirection](#client-redirection--why-each-provider-gets-its-own-port) for why that shapes the mount:

```shell
export GH_HOST="<fogwall-host>"
export GH_TOKEN="<classic PAT>"          # or: gh auth login -h <fogwall-host>
gh issue create -R <fogwall-host>/<owner>/<repo> --title ... --body ...
gh pr create    -R <fogwall-host>/<owner>/<repo> --base main --head <branch> ...
```

- Classic PATs work; **fine-grained PATs are documented as not working** for cross-org issue/PR create (a gh/PAT
  limitation, not proxy-specific). The workaround on this path is a classic PAT: fogwall cannot substitute its own OAuth
  token here, since that grant is never handed to a client.
- Scopes observed / required: `repo`, `read:org` (`workflow` for some flows).
- Relevant request headers seen: `X-Github-Api-Version: 2022-11-28`, `Graphql-Features: merge_queue`,
  `Accept: application/vnd.github.merge-info-preview+json, ...`.
- **CLI version anchor:** `gh` sends its own version on every request — `User-Agent: GitHub CLI <version> ...` (verified
  `2.98.0` in the capture logs). If a future `gh` release changes wire behavior in a way the parser/ allowlist/node-ID
  map doesn't handle, this header is the hook to detect and branch on a known-bad version (log a warning, or gate a
  version-specific workaround) rather than only discovering the break from a failure mode. Not implemented yet — noted
  here as the anchor point for when/if it's needed.

### Mutation → node-ID map (the allowlist + resolver core)

Each mutation references its target by an **opaque global node ID**, and **the input key holding that ID differs per
mutation** — the resolver cannot look for a single field name:

| `gh` command       | schema mutation field  | gh `operationName`     | input node-ID key     | node type           |
| ------------------ | ---------------------- | ---------------------- | --------------------- | ------------------- |
| `issue create`     | `createIssue`          | `IssueCreate`          | `input.repositoryId`  | Repository (`R_`)   |
| `pr create`        | `createPullRequest`    | `PullRequestCreate`    | `input.repositoryId`  | Repository (`R_`)   |
| `issue edit`       | `updateIssue`          | `IssueUpdate`          | `input.id`            | Issue (`I_`)        |
| `issue close`      | `closeIssue`           | `IssueClose`           | `input.issueId`       | Issue (`I_`)        |
| `issue/pr comment` | `addComment`           | `CommentCreate`        | `input.subjectId`     | Issue or PR         |
| `pr edit`          | `updatePullRequest`    | `PullRequestUpdate`    | `input.pullRequestId` | PullRequest (`PR_`) |
| `pr review`†       | `addPullRequestReview` | `PullRequestReviewAdd` | `input.pullRequestId` | PullRequest (`PR_`) |
| `pr close`         | `closePullRequest`     | `PullRequestClose`     | `input.pullRequestId` | PullRequest (`PR_`) |

† Recorded for completeness; review is out of scope and **not allowlisted** — reviewers use the SCM's own UI.

Notes that drive the implementation:

1. **Allowlist on the schema mutation field** (`createIssue`, `updateIssue`, …), parsed from the AST — **not** on gh's
   `operationName` (which is gh-specific and can change, e.g. `IssueCreate`) and **not** on a substring.
2. **The mutation carries only the node ID, never `owner/repo`.** Resolution (§3c) is mandatory for authorization.
3. **The resolver must handle three node types** — `Repository` (`R_…`), `Issue` (`I_…`), `PullRequest` (`PR_…`) — the
   new base64 global IDs:
   ```graphql
   node(id: $id) {
     ... on Repository  { name owner { login } }
     ... on Issue       { repository { name owner { login } } }
     ... on PullRequest { repository { name owner { login } } }
   }
   ```
4. **Subject (issue/PR) IDs are safer to cache than repo IDs.** A GitHub issue "transfer" mints a _new_ node ID in the
   destination (the old ID stays a redirect), so `issueId → repo` has no rename/transfer staleness. The
   `repositoryId → owner/name` cache is the one that needs the conservative TTL.

### Fan-out & the cache-seed, verified

Each mutation is preceded by a read `query` that carries `owner`/`repo`(/`number`) in its **variables** and returns the
target node ID in its **response** — so the `nodeId → owner/repo` cache seeds from the caller's own traffic:

- `issue create` → `query IssueRepositoryInfo($owner,$name){ repository{ id … } }` → response `data.repository.id`
  **equals** the `createIssue` mutation's `input.repositoryId` (verified).
- `issue edit/comment/close` → `query IssueByNumber($owner,$repo,$number){ … issue{ id } }`.
- `pr edit/comment/review/close` → `query PullRequestByNumber($owner,$repo,$pr_number){ … pullRequest{ id } }`.
- `pr create` additionally fires `query RepositoryInfo` and `query PullRequestForBranch` (existing-PR check).

All the lookups are `query` type (default-allowed). Some commands fire extra reads (`PullRequestProjectItems`) — also
`query` type, no special handling.

### GitHub allowlist

Default-deny all mutations except this set, matched on the parsed mutation field:

```
createIssue, updateIssue, closeIssue,
createPullRequest, updatePullRequest, closePullRequest,
addComment
```

Review mutations (`addPullRequestReview` and friends) are deliberately absent — review is out of scope, so fogwall never
forwards it.

---

## Provider: GitLab (`glab`) — VERIFIED

Filled in from live `glab` v1.116.0 traffic (`GLAB_DEBUG_HTTP=true`) exercising the full issue/MR CRUD matrix against a
test repo (`gitlab.com`).

### Transport

- **Issue/MR CRUD is 100% REST v4** — every create/edit/comment/approve/close in the captured matrix is a plain
  `GET`/`POST`/`PUT` against `/api/v4/...` with a JSON body. No GraphQL was observed for any of these operations —
  GitLab's GraphQL surface exists but `glab` doesn't use it for this command set.
- **The project is addressed by URL-encoded `owner/repo` path**, not a numeric project ID — e.g.
  `/api/v4/projects/coopernetes%2Ftest-repo-gitlab/issues`. This answers §3c's open question directly: **the opaque-ID
  resolution/cache machinery built for GitHub is not needed for GitLab.** `owner/repo` is in the URL on every request,
  so gating can use the same path-matching model as the existing git-push `UrlRuleRegistry`/`AccessRule` — much closer
  to that than to GitHub's node-ID resolution.
- **Every mutating command is preceded by a `GET` on the same path** that already carries `owner/repo` (and, for
  issue/MR-scoped operations, the `iid`) — so there is no cache-seeding story to build here either; the path is
  self-describing on every request, cold or warm.
- **`glab` sends its own version** via `User-Agent: glab/v1.116.0 (linux, amd64)` — the same version-anchor pattern as
  `gh`'s `User-Agent: GitHub CLI <version> ...` (see the GitHub section above).

### Capture method (confirmed)

`glab`'s `--debug` flag does **not** produce wire-level output — it only adds CLI-side verbose logging. The flag that
works is the **`GLAB_DEBUG_HTTP`** environment variable:

```shell
GLAB_DEBUG_HTTP=true glab issue create -R <owner>/<repo> --title ... --description ... > cmd.log 2>&1
```

This prints full request/response pairs (method, path, headers, body) and already redacts the `Authorization` header
itself (`Authorization: [REDACTED]`) — no extra scrubbing needed for that header specifically.

### Operation → REST endpoint map

| `glab` command | Method | Path                                          | Target ID source                                                             |
| -------------- | ------ | --------------------------------------------- | ---------------------------------------------------------------------------- |
| `issue create` | POST   | `/projects/:path/issues`                      | path only                                                                    |
| `issue update` | PUT    | `/projects/:path/issues/:iid`                 | path + `iid` (from CLI arg, not a preceding lookup)                          |
| `issue note`   | POST   | `/projects/:path/issues/:iid/notes`           | path + `iid`                                                                 |
| `issue close`  | PUT    | `/projects/:path/issues/:iid`                 | body `{"state_event":"close"}`                                               |
| `mr create`    | POST   | `/projects/:path/merge_requests`              | path (+ numeric `target_project_id`, from a preceding `GET /projects/:path`) |
| `mr update`    | PUT    | `/projects/:path/merge_requests/:iid`         | path + `iid`                                                                 |
| `mr note`      | POST   | `/projects/:path/merge_requests/:iid/notes`   | path + `iid`                                                                 |
| `mr approve`†  | POST   | `/projects/:path/merge_requests/:iid/approve` | path + `iid`                                                                 |
| `mr close`     | PUT    | `/projects/:path/merge_requests/:iid`         | body `{"state_event":"close"}`                                               |

`:path` is the URL-encoded `owner%2Frepo` segment; `:iid` is the project-scoped issue/MR number (not a global ID),
always supplied by the CLI caller from the command-line argument or a preceding `GET`.

† Recorded for completeness; approval is a review operation, out of scope and **not allowlisted**.

### Fork MRs address the _source_ project — VERIFIED

`mr create` is the one operation whose URL does **not** name the repository fogwall must authorize against. Captured
from a real fork MR (fork `id 86130652` → upstream `id 53539888`, same namespace, `GLAB_DEBUG_HTTP=true`):

```
POST /api/v4/projects/coopernetes%2Ftest-repo-gitlab-fork/merge_requests
{"title":"…","source_branch":"fork-feature","target_branch":"main","target_project_id":53539888}
```

The URL segment is the **fork**; the **upstream** appears only as the numeric `target_project_id` in the body. The
response confirms the split — `source_project_id: 86130652`, `target_project_id: 53539888`, MR created on the upstream.

Since authorization targets the upstream (the repo the MR is opened on), a path-only matcher reads the wrong project
here. The rule:

- **Authorize on `target_project_id` when the request body carries it.**
- Fall back to the URL's project when it does not — a same-project MR, where the two are identical anyway.
- If `target_project_id` is present but cannot be resolved to a path, **deny**: an unidentifiable target cannot be
  authorized.

Every other GitLab operation in scope is unaffected: `mr update` and `mr note` address the MR by `iid`, which is scoped
to the target project, so their URL already names the upstream.

**Resolution is close to free.** `glab` fires a `GET /projects/:path` for _both_ projects immediately before the POST,
and each response carries `id` alongside `path_with_namespace`. That is the same cache-seeding opportunity as GitHub's
node-ID path — fogwall can populate the numeric-ID → path mapping from traffic it is already forwarding, so the cold
lookup should be rare. TTL remains a security parameter: a project ID outlives a rename.

> **Capture hazard.** `GLAB_DEBUG_HTTP` redacts `Authorization` but not response bodies, and `GET /projects/:path`
> returns `runners_token` in plaintext for a project owner. Scrub captures before sharing them.

Implementation note for when this dialect is built: since gating is path-based, it's a stronger candidate for reusing
`UrlRuleRegistry`-style matching than for extending the GitHub-specific `ScmApiAccessRule`/node-ID system — the two
providers' dialects are shaped differently enough that they likely shouldn't share a rule engine, consistent with the
"new rule system specific to API proxying, some duplication is fine" decision made for GitHub.

## Provider: Gitea (`tea`) / Forgejo (`fj`) — VERIFIED

Gitea and Forgejo share one REST API (Forgejo forked Gitea's); `tea` and `fj` are the two CLIs. Established by reading
the CLIs' own source and the SDKs they generate their requests from, cross-checked against live `tea --debug` output and
a local listener both binaries were pointed at. Pinned versions: `tea` 0.15.1 (`gitea.dev/sdk` v1.2.0), `fj` v0.6.0
(`forgejo-api` 0.11.0).

### Transport

- **100% REST v1** — no GraphQL anywhere in either CLI.
- **The repository is addressed as two ordinary path segments**, `/api/v1/repos/{owner}/{repo}/...`, each URL-encoded
  independently. As with GitLab, the authorization target is read straight off the path, so **§3c's opaque-ID resolution
  machinery is not needed**.
- **The issue/PR index is a plain project-scoped integer**, supplied by the caller.
- **Both CLIs advertise their version**: `tea/0.15.1 (linux/amd64) go-sdk/v1.2.0` and
  `forgejo-cli/0.6.0 (https://codeberg.org/forgejo-contrib/forgejo-cli/)` — the same version-anchor pattern as `gh` and
  `glab`. Both authenticate as `Authorization: token <pat>`.

### One dialect, not two

`tea` and `fj` talk to the same server API and differ only in **which subset** of it they exercise — never in the shape
of a call. So fogwall implements one Gitea/Forgejo dialect whose allowlist is the **union** of the two CLIs' endpoints.

That union matters, because the two reach the same user-facing operation by **different endpoints**:

| operation       | `tea`                                     | `fj`                                   |
| --------------- | ----------------------------------------- | -------------------------------------- |
| list PRs        | `GET /repos/{o}/{r}/pulls`                | `GET /repos/{o}/{r}/issues?type=pulls` |
| close PR        | `PATCH /repos/{o}/{r}/pulls/{n}`          | `PATCH /repos/{o}/{r}/issues/{n}`      |
| comment on a PR | `POST /repos/{o}/{r}/issues/{n}/comments` | same                                   |

Forgejo models a pull request as an issue, and `fj` routes through that model (`fj pr close` calls
`crate::issues::close_issue`). Allowlisting only the `/pulls` form silently breaks `fj`; only the `/issues` form
silently breaks `tea`.

**The two CLIs are deliberately not distinguished by `User-Agent`.** The header is chosen by the caller, so branching
authorization on it would let anyone select whichever rule set is looser. `ScmApiUserAgentFilter` exists, but it is
strictly subtractive — it can refuse client types a deployment doesn't want, never grant anything the allowlist and
permission engine would refuse.

### Operation → REST endpoint map

Paths are shown below the `/api/v1` mount point. `{n}` is the project-scoped index.

| operation            | Method | Path                                 | `tea` | `fj`                  |
| -------------------- | ------ | ------------------------------------ | ----- | --------------------- |
| issue create         | POST   | `/repos/{o}/{r}/issues`              | yes   | yes                   |
| issue update/close   | PATCH  | `/repos/{o}/{r}/issues/{n}`          | yes   | yes (also `pr close`) |
| issue/PR comment     | POST   | `/repos/{o}/{r}/issues/{n}/comments` | yes   | yes                   |
| comment update       | PATCH  | `/repos/{o}/{r}/issues/comments/{n}` | yes   | yes                   |
| PR create            | POST   | `/repos/{o}/{r}/pulls`               | yes   | yes                   |
| PR update/close      | PATCH  | `/repos/{o}/{r}/pulls/{n}`           | yes   | no                    |
| PR merge†            | POST   | `/repos/{o}/{r}/pulls/{n}/merge`     | yes   | yes                   |
| PR review (approve)† | POST   | `/repos/{o}/{r}/pulls/{n}/reviews`   | yes   | **no**                |

† Recorded for completeness; **not allowlisted**. Review is out of scope — reviewers use the SCM's own UI. Merge is a
maintainer operation with its own design questions and is tracked separately in #565.

Two consequences that shape the implementation:

- **`fj` cannot approve a pull request.** `repo_create_pull_review` exists in `forgejo-api` 0.11.0, but `fj` never calls
  it — `fj pr review` only lists. A genuine capability gap in the CLI, not a gap in the capture.
- **`tea` sends a full-object PATCH.** `tea pr close` emits every field alongside `"state":"closed"`
  (`{"title":"","base":"", ... ,"state":"closed", ...}`), so on the wire it is indistinguishable from `tea pr edit`.
  Rule granularity is method+path, never intent — the allowlist cannot separate the two, and shouldn't pretend to.

Endpoints the CLIs can also reach — labels, assignees, tracked time, dependencies, blocking, releases — are deliberately
**absent** from the allowlist and therefore denied, matching the #264 contribution-lifecycle scope.

### Fork PRs address the _upstream_ — VERIFIED

Unlike GitLab, Gitea/Forgejo names the repository fogwall must authorize against directly in the URL, even for a PR
opened from a fork. Captured with `tea --debug`:

```
POST https://gitea.com/api/v1/repos/coopernetes/test-repo/pulls
{"head":"someotheruser:some-fork-branch","base":"main","title":"…","body":"…"}
```

The path segment is the **upstream** — whatever `--repo` names — and the fork appears only in the body as
`head: "<user>:<branch>"`, the same shape GitHub uses. The path-based matcher therefore reads the correct project with
no extra work, and none of GitLab's `target_project_id` handling is needed here.

`fj` behaves the same way by construction: `--head` is forwarded verbatim (`prs.rs`, `Some(head) => Some(head)`) and the
repo comes from `-r/--repo` into `repo_create_pull_request(owner, repo, …)` — this same endpoint.

Note `tea` also fires non-repo-scoped reads around the create (`GET /orgs/{name}`, plus `GET /repos/{o}/{r}/labels`), so
a path-based rule engine has to tolerate paths carrying no `owner/repo`. Reads are gated only at provider level, so this
costs nothing today.

### Capture method (confirmed)

- **`tea --debug`** works: it prints the request method + full URL, the headers `tea` sets, and the request body. Two
  gaps: response bodies show only a Go pointer (`Response: &{...}`), and `Authorization`/`User-Agent` are added lower in
  the SDK so they never appear in the header map.
- **`fj` has no HTTP debug at all** — no verbosity flag, and no `RUST_LOG`/`env_logger`/`tracing-subscriber` strings in
  the binary. Reading `forgejo-cli` + the generated `forgejo-api` crate is the reliable route, and is what produced the
  table above. `fj` does honour `HTTPS_PROXY` and links OpenSSL (so it trusts the system CA store), which makes
  mitmproxy a workable fallback if raw bytes are ever needed — it was not needed here.

---

## Credential & scope model

**ID resolution uses the caller's token, never an app-level one.** fogwall resolves only what the caller can already
see, so an opaque ID they cannot read is a deny rather than a lookup. This never wrongly blocks: the caller is about to
operate on that target, so a token that cannot read it cannot perform the operation either.

The `id -> owner/repo` mapping is an objective fact rather than a per-user one, so the cache is shared across users.
Only the authorization decision is per-user, and that is never cached.

**OAuth scope granularity varies by provider.** This applies to fogwall-managed operations, where fogwall holds a token
on the user's behalf — the participant issue form in #563 and any future fogwall-hosted proposal UI, not the proxied-CLI
path, which forwards a PAT. How tightly that token can be scoped:

| provider      | narrowest useful scope for issue/PR writes                                     | credential-layer least privilege? |
| ------------- | ------------------------------------------------------------------------------ | --------------------------------- |
| GitHub        | `issues: write`, `pull_requests: write` as separate permissions                | yes                               |
| Gitea/Forgejo | granular scopes exist (`write:issue` and similar) — **exact names unverified** | probably                          |
| GitLab        | `api` — nothing narrower than full read/write API access                       | **no**                            |

GitLab has granular scopes for runners, registry and observability but nothing for core API resources, so a token scoped
for issue writing is indistinguishable from one that can do anything the user can. It remains bounded by the user's own
GitLab role, so it is an over-broad credential at rest rather than a privilege escalation — but the permission engine is
the only control there. Least privilege at the credential layer is not available on every provider.

## Deployment shapes — sketch

> Not operator-facing yet. Promote to [ADMIN_GUIDE.md](../ADMIN_GUIDE.md) once a shape has actually been run.

The port-per-provider requirement (see [Client redirection](#client-redirection--why-each-provider-gets-its-own-port))
reads like a constraint, but it maps cleanly onto ingress-based platforms, where the ports end up internal and each
provider is reached by hostname.

### Kubernetes / OpenShift

One `Service` exposing every proposal port, and one `Ingress` (or OpenShift `Route`) per provider pointing at its port:

| host                                  | → Service port | provider |
| ------------------------------------- | -------------- | -------- |
| `fogwall-github-api.corp.example.com` | 9443           | github   |
| `fogwall-gitlab-api.corp.example.com` | 9444           | gitlab   |
| `fogwall-gitea-api.corp.example.com`  | 9445           | gitea    |

Ingress terminates TLS on 443 and routes by host, so **the ports disappear from client configuration entirely** — which
is the form `GH_HOST` and `fj -H` want anyway:

```shell
GH_HOST=fogwall-github-api.corp.example.com          # no port
GITLAB_HOST=fogwall-gitlab-api.corp.example.com
tea login add --url https://fogwall-gitea-api.corp.example.com --token "$PAT"
fj -H https://fogwall-gitea-api.corp.example.com pr create "title" --body "body"
```

Each host needs its own certificate, and the git server / transparent proxy keep their existing ingress on the main port
— this is additive to whatever already fronts fogwall.

**Verify the ingress controller does not decode or normalize the request path.** GitLab addresses a project as a single
`owner%2Frepo` segment, and that encoded slash has to survive to fogwall intact — the same hazard as Jetty's
`AMBIGUOUS_PATH_SEPARATOR` default, one tier up. nginx-ingress changes path handling once a `rewrite-target` with a
capture group is involved; HAProxy-backed OpenShift Routes are generally pass-through. The failure mode is narrow and
confusing — GitLab denied or 404ing while GitHub and Gitea work — so confirm it rather than assume it.

Health probes can stay on the main port: a proposal listener that cannot bind fails `Server.start()` outright, so the
pod never reaches ready with a listener missing.

### Bare host / VM

No ingress, so the ports are the interface: clients are configured with `host:port` directly, as in the `local` profile.
Front it with TLS termination per provider host if you want the hostname form above.

### Later option — one port, routed by Host header

Where every provider already has its own hostname, the connectors could collapse to a single port, with each context
bound to its hostname rather than to its connector: `ContextHandler.setVirtualHosts` accepts real hostnames, not only
the `"@connectorName"` form `registerScmApiListeners` uses today. The change inside fogwall is small, since the
mechanism is already the one in use.

Deferred rather than dismissed. It is worth having as an option, but it only works once the surrounding infrastructure
exists — a DNS record and a certificate per provider hostname, and an ingress that preserves the `Host` header — and
none of that is fogwall's to provide or verify. A deployment that hasn't set it up would get no routing at all, with the
failure landing inside fogwall rather than where the missing configuration is.

Ports need none of that: they work on a laptop, on a bare host, and behind an ingress without changing anything, which
is why they are the default now. Host-based routing becomes attractive when a deployment wants one certificate and one
ingress rule per provider anyway — at which point the hostnames already exist and the prerequisite is free.

## Open design questions

**Head-SHA provenance.** The intent is to refuse opening a PR for code fogwall never inspected, by checking the head
against fogwall's own push records — otherwise a contributor can push branch A through fogwall, push branch B directly,
and open the PR on B. Two mechanics are unresolved:

- _The request carries a branch name, not a SHA._ Verified on GitLab: the MR create body has `"source_branch"`, and the
  SHA appears only in the response. So fogwall must resolve branch -> SHA itself at create time, costing a lookup and
  opening a TOCTOU window.
- _A PR tracks its branch._ A validated head can be replaced by a later push to the fork, and the PR follows it. The
  check is therefore only as strong as the guarantee that the fork's pushes also go through fogwall — a consistency
  check layered on governed pushes, not an independent control.

**GitHub fork PRs are untested.** `createPullRequest`'s `input.repositoryId` is expected to name the base repo, which
would make the existing resolver correct with no change. Captured only for same-repo PRs so far.

## Appendix: capturing a new provider

The GitHub section was produced with the procedure below; it generalizes to each CLI. The goal is a per-command log of
every request/response (bodies included) for the full CRUD matrix, with credentials scrubbed.

1. **Use a throwaway/test repo and a rotatable token.** Never capture against production data.
2. **Enable the CLI's API debug output** and tee each command to its own file:
   - `gh` (GitHub): `GH_DEBUG=api gh <cmd> … > cmd.log 2>&1` — **confirmed**; prints request line, headers, the GraphQL
     query + variables, and the response body.
   - `glab` (GitLab): `GLAB_DEBUG_HTTP=true glab <cmd> … > cmd.log 2>&1` — **confirmed**; `--debug` does _not_ show wire
     traffic (CLI-side verbose logging only). Prints request/response with `Authorization` already redacted.
   - `tea` (Gitea): `tea <cmd> … --debug` — **confirmed**; prints method + URL, the headers `tea` sets, and the request
     body. Response bodies are not usable (Go pointer dump), and `Authorization`/`User-Agent` are added lower in the SDK
     so they never appear.
   - `fj` (Forgejo): **no HTTP debug exists** — no verbosity flag, no `RUST_LOG`/`env_logger`/`tracing` support in the
     binary. Read the CLI source plus its generated API crate instead (that is how the Gitea/Forgejo table above was
     produced, and it is more reliable than a capture: it enumerates every endpoint the CLI can reach, not just the ones
     a session happened to exercise). If raw bytes are genuinely needed, `fj` honours `HTTPS_PROXY` and links OpenSSL,
     so mitmproxy with its CA in the system trust store works.
3. **Exercise the full matrix**, capturing the ordered fan-out per command: issue create → edit → comment; pr/mr create
   → edit → comment → review; then close both (close is also a capturable mutation and cleans up). Reuse an existing
   branch for the PR/MR head so nothing needs to be pushed.
4. **Scrub tokens** from every log before analysis (redact `Authorization:` lines and any `gh[posur]_…` / `github_pat_…`
   / provider token patterns). CLI debug output usually redacts `Authorization` already; scrub anyway.
5. **Extract** per command: request method + path (REST) or mutation field + variables (GraphQL), and note where the
   target ID appears and whether a preceding lookup already carries `owner/repo`.

6. **Check what the client does with a base path.** Point the CLI at a local listener configured with a sub-path
   (`http://127.0.0.1:8099/some/prefix`) and see whether the prefix survives — `gh` and `fj` silently discard it, which
   is what forces the per-provider listener described in
   [Client redirection](#client-redirection--why-each-provider-gets-its-own-port). A tiny Python
   `BaseHTTPRequestHandler` that logs the request line is enough, and needs no valid credential.

Reading the CLI's source and the SDK it generates requests from is worth doing alongside any capture, and is sometimes
the only route (`fj`). A capture proves what one session did; the source enumerates everything the CLI _can_ send, which
is what an allowlist actually has to cover.

This yields the per-provider table above and the answers to the resolution/cache questions in §3.
