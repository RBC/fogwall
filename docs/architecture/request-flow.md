# Request flow

## Server mode push

```
git push → /server/<provider>/<owner>/<repo>.git
             │
             ▼
     ServerRepositoryResolver
       • clone/fetch upstream repo locally
       • extract credentials from Authorization header
             │
             ▼
     ServerReceivePackFactory
       • assemble hook chain (see below)
             │
       ┌─────┴──────────────────────────────────┐
       │  Pre-receive hooks (ordered)            │
       │  1. PushStorePersistenceHook            │  record RECEIVED
       │  2. Validation hooks (see below)        │  emit per-step sideband messages
       │  3. PushStorePersistenceHook            │  record PENDING or BLOCKED
       │  4. ApprovalPreReceiveHook              │  block until approved / auto-approve
       └─────┬──────────────────────────────────┘
             │  (if approved)
       ┌─────┴──────────────────────────────────┐
       │  Post-receive hooks                     │
       │  1. ForwardingPostReceiveHook           │  push to upstream with dev's credentials
       │  2. PushStorePersistenceHook            │  record FORWARDED or ERROR
       └─────────────────────────────────────────┘
```

## Authenticating a server mode request

Server mode cannot forward a request it has no credentials for, and a git client only sends credentials after a 401
challenge — so `BasicAuthChallengeFilter` has to decide, before the servlet runs, whether this request needs one.

Push is unambiguous: the push is forwarded upstream using the developer's own token, so it is always challenged.

Fetch is not. The mirror is cloned from upstream on every open, so a fetch of a private repository must be able to carry
credentials — but challenging every fetch makes public repositories unclonable by anyone who has no credential to offer,
and a client that answers the challenge with an unrelated or expired token is rejected by providers such as GitHub even
on a repository they would have served anonymously. Guessing in either direction breaks a real workflow.

So fogwall asks upstream instead. `UpstreamAuthProbe` issues the git advertisement itself —
`GET <repo>/info/refs?service=git-upload-pack`, no credentials — and reads the answer: `200` means anonymous reads are
served, anything else means they are not. That is provider-agnostic, needs no REST API and no per-provider visibility
field, and follows the repository's real visibility rather than a configured assumption.

Two properties keep it cheap and safe. Only an _unauthenticated_ fetch probes at all — a request already carrying an
`Authorization` header is passed straight through — and verdicts are cached per repository, so a burst of anonymous
clones costs one upstream round trip. Any unclear answer (timeout, 404, 5xx) is treated as "credentials required",
because a probe that cannot reach upstream must never be the reason a repository becomes anonymously readable.

The transparent proxy needs none of this: it forwards to upstream directly, so upstream issues its own challenge. SSH
authenticates by key before any git command runs.

## Repository path derivation

Owner, name and slug all come from one place, `RepoPath` — the transparent proxy's `ParseGitRequestFilter`, server
mode's `ServerReceivePackFactory` and `RepositoryUrlRuleHook`, the SSH transport's route resolution, and
`RepoPermissionService`'s `OWNER`/`NAME` targets each parse the request path through it rather than splitting the path
themselves. A URL rule and a permission check evaluated for the same request have to compare against the same strings; a
per-call-site split is how they drift apart, and a rule that silently matches a different repository than the permission
check did is a containment failure rather than a cosmetic inconsistency.

The rule is that the repository name is the **last** path segment and the owner is everything before it, so a path is
not capped at two segments — a GitLab subgroup project `/group/subgroup/project` has owner `group/subgroup`. Splitting
at the first separator, or keeping only the first two segments, reads such a path as the subgroup itself. Every segment
is validated, not just the first and last, because a nested owner is several segments and traversal in any one of them
must not reach an upstream URL or a cache key. A path that does not parse yields nothing at all and each caller rejects:
a partial reading of a repository path is never safe to authorize against.

`RepoPathMatching` is the companion half: `RepoPath` decides which part of a path a pattern is compared against, and
`RepoPathMatching` decides how that comparison is made. Both the URL-rule evaluator and the permission service route
through it, so neither can drift on the answer. Its one job today is case folding — every provider fogwall speaks to
resolves `owner/repo` case-insensitively, so comparing case-sensitively let a recased path miss a `DENY` and fall
through to a broader allow beneath it. Folding also takes `GLOB` off the host filesystem's case rules, which otherwise
made the same rule behave differently on Linux and macOS. Case is the only thing folded: the path's shape — its leading
slash and its segment boundaries — is still compared verbatim, because those are what the operator wrote in a URL rule.

## Transparent proxy push

```
git push → /proxy/<provider>/<owner>/<repo>.git
             │
             ▼
     Servlet filter chain (ordered)
       ParseGitRequestFilter      extract pack metadata from packet lines
       EnrichPushCommitsFilter    clone/fetch upstream repo; unpack inflight pack into a per-request quarantine; walk commit range
       AllowApprovedPushFilter    prior-approved? skip validation, proxy directly
       UrlRuleAggregateFilter     evaluate ALLOW/DENY rules
       CheckUserPushPermissionFilter   resolve identity; check repo permissions
       CommitAttributionPolicyFilter  verify commit author/committer email
       [content validation filters — see below]
       ValidationSummaryFilter    collect all issues
       PushFinalizerFilter        save push record; wait for approval if required
             │
             ▼
     FogwallServlet (Jetty AsyncProxyServlet)
       • HTTP proxy pass-through to upstream
       • on response: update push record → FORWARDED or ERROR
```

### Per-request object quarantine

Validation has to read a push's objects before it can decide anything about them, but the mirror behind
`/proxy/<provider>/...` is shared by every request for that repository. Unpacking straight into it means a rejected push
leaves its content there permanently — including the content policy just refused.

`QuarantineObjectStore` gives each push its own scratch object store instead:

- the quarantine `Repository` shares the mirror's **git directory**, so it sees the mirror's refs and can still answer
  "what does this push actually introduce";
- its **object directory** is a temporary directory, so every write lands there;
- the mirror's object directory is registered as an **alternate**, which is what lets thin-pack deltas resolve against
  objects the mirror already has.

Downstream filters receive the quarantine as `GitRequestDetails.localRepository`, so they see the union: mirror contents
plus this push. `EnrichPushCommitsFilter` wraps the rest of the chain in try-finally and deletes the quarantine when the
request ends.

In this mode nothing is ever promoted back into the mirror. An accepted push's objects reach it the same way everything
else does — by being fetched from upstream once they exist there — which keeps the mirror a reflection of upstream
rather than an accumulation of everything anyone attempted. (Server mode differs; see below.) If a quarantine cannot be
created the filter logs a warning and falls back to the mirror: the loss is disk hygiene, not a validation result, so it
is not worth failing a push over.

This is the same shape as git's own `receive-pack` quarantine (`tmp_objdir`, exposed to hooks as `GIT_QUARANTINE_PATH`):
temporary object directory, real one as an alternate, hooks run against that view. JGit has no equivalent, hence the
local implementation. Note it is roughly the _inverse_ of a worktree — a worktree shares the object database and
isolates the index and HEAD, whereas this shares refs and isolates objects, so a worktree would not help here.

The one deliberate departure from git's version is the last step: git migrates objects into the real store on success,
because for git the receive is authoritative and those objects have nowhere else to come from. Here the mirror is a
cache of upstream, so not promoting is both simpler and a stronger guarantee.

Server mode quarantines too, with one difference. There JGit's `ReceivePack` applies the ref updates to the shared git
directory once the pre-receive hooks pass, so the objects those refs name have to be in the mirror by then — discarding
them would leave the mirror pointing at objects that no longer exist. `QuarantinePromotionHook` runs as the last
pre-receive hook and moves the objects across only when nothing has been rejected; if it fails, it rejects the push,
because a half-promoted push is worse than a refused one. The HTTP path's quarantine is torn down by
`QuarantineCleanupFilter` on the server-mode mapping; the SSH path scopes it to `SshGitReceiveCommand`, which has no
servlet request to hang it off.

So both modes discard a rejected push's objects. The transparent proxy additionally never promotes, because it never
applies ref updates. There, JGit's `ReceivePack` owns the inserter and writes into the mirror before the pre-receive
hooks run, so the same guarantee needs a different mechanism.

Both mirrors — server mode's and the transparent proxy's — are held by a `LocalRepositoryCache`, and the dashboard
exposes each one for operator inspection and manual invalidation over `/api/admin/cache` (`ROLE_ADMIN`). The cache is
in-memory and per-pod, so this is a per-pod operational view, not distributed state; invalidating an entry deletes its
local clone (keeping the cache root) so the next access re-clones from upstream — the recovery path for a stale or
poisoned mirror without a restart.

**Concurrency.** A mirror is shared across concurrent requests, so the cache coordinates access at three points. First
clones are serialized on a **per-repository lock** (keyed on the cache key): threads racing on the first access to the
same repo dedupe to one clone, while first clones of _different_ repos run in parallel. Upstream refreshes for a repo
are serialized on that repo's own lock so two fetches never write the same bare repo at once. The **serve path is
deliberately left lock-free**, though: a refresh (writer) may run while an upload/receive or content inspection (reader)
uses the same mirror, and readers are not blocked. This is safe because a `git fetch` is additive — it never deletes the
objects a concurrent reader is serving — so the worst normal outcome is that the reader sees a slightly stale snapshot
and the client re-fetches. A read/write lock was rejected because, to be safe, it would either starve refreshes under
sustained fetch traffic or tax the hot serve path; the full rationale (and the shallow-mirror `cloneDepth=0` escape
hatch for the one transient edge case) lives on `LocalRepositoryCache`. Separately, JGit's process-global pack-window
cache is tuned once at startup for a many-mirror server (larger `packedGitLimit`/open-file budget, mmap off) rather than
its desktop-git defaults; these are engine internals, intentionally not fogwall config keys.
