# Git internals reference

Notes on git/JGit behaviour that inform how filters and hooks are written. Add a section here when you hit a non-obvious
edge case so the next person doesn't have to rediscover it.

For an overview of how fogwall uses JGit's server-side APIs (ReceivePackFactory, hook chain, forwarding, credential
flow), see [JGIT_INFRASTRUCTURE.md](JGIT_INFRASTRUCTURE.md).

---

## Tag objects

### Lightweight vs annotated tags

Git has two kinds of tags, and they behave very differently at the object level.

**Lightweight tag** — just a named pointer, stored as a ref file. The ref value is the SHA of the commit it points to
directly. There is no tag object in the object store.

```text
refs/tags/v1.0 → a3f9c1... (commit)
```

**Annotated tag** — a first-class git object of type `tag`. The ref points to the tag object SHA, not the commit SHA.
The tag object contains metadata (tagger, date, message) and a pointer to the tagged commit.

```text
refs/tags/v1.0 → b7d2e4... (tag object)
                     └─→ a3f9c1... (commit)
```

The key consequence: **`cmd.getNewId()` for an annotated tag push returns the tag object SHA, not a commit SHA.** Any
code that calls `RevWalk.parseCommit(cmd.getNewId())` directly will throw `IncorrectObjectTypeException` for annotated
tags.

### The `^{commit}` dereference

Both git and JGit support a peeling suffix to follow any chain of tag objects to the final commit:

```java
// Safe for both lightweight and annotated tags:
ObjectId commitId = repository.resolve(sha + "^{commit}");
```

For a lightweight tag, `sha` is already a commit SHA — `^{commit}` is a no-op. For an annotated tag, JGit follows the
tag → commit chain. For a chain of tags (a tag of a tag), it follows all the way down.

`^{tree}` works the same way but stops at a tree object instead of a commit.

`resolve()` returns `null` when the peel fails — for example when a tag points to a blob or tree rather than a commit
(legal but extremely rare). Always null-check the result.

### What git sends over the wire for a tag push

When you run `git push origin refs/tags/v1.0`:

- The packet line header is `<oldOid> <newOid> refs/tags/v1.0` (same format as a branch push).
- For a **lightweight tag** to an already-upstream commit, git sends a thin pack with **zero objects** because the
  commit already exists at the remote. Trying to read a pack entry from an empty pack produces garbage or a
  `DataFormatException` — this is normal, not a corruption.
- For an **annotated tag**, git sends a pack containing the tag object (type 4, `OBJ_TAG`). The tagged commit is not
  included if it already exists upstream.
- `commitFrom` (`oldOid`) is all-zeros for a new tag — the same value used for a new branch. Code that uses
  `commitFrom == zeros` as a signal for "new branch" must also account for new tags.

### How each hook/filter handles tags

#### S&F hooks (`CheckEmptyBranchHook`, `CheckHiddenCommitsHook`)

Tags push commits that already exist upstream. `CommitInspectionService.getCommitRange()` returns an empty list — the
commit at the tag tip is already reachable from existing heads, so it is not "new".

**`CheckEmptyBranchHook`** — an empty commit range on a zero-oldId ref would normally mean the branch has no new commits
(a reject condition). For tags this is always the case and is legitimate, so the hook skips any ref whose name starts
with `refs/tags/`.

**`CheckHiddenCommitsHook`** — calls `walk.parseCommit()` on `cmd.getNewId()`. For an annotated tag this throws. Fix:
resolve through `^{commit}` first.

```java
ObjectId commitId = repo.resolve(cmd.getNewId().name() + "^{commit}");
if (commitId == null) continue;
walk.markStart(walk.parseCommit(commitId));
```

All other S&F hooks (`AuthorEmailValidationHook`, `CommitMessageValidationHook`, etc.) delegate to
`CommitInspectionService.getCommitDetails()` or `getCommitRange()`, both of which use `^{commit}`. They are safe
transitively.

#### Proxy-mode filters

The proxy pipeline sees the same two objects as the S&F hooks — the packet line SHAs and the pack data — but runs as
servlet filters without JGit's `ReceivePack` infrastructure.

**`ParseGitRequestFilter`** — extracts `branch`, `commitFrom`, `commitTo` from the packet line, then tries to parse the
first pack object as a commit. For a tag push this fails (the pack contains a tag object or no new objects). The parse
exception is caught; `requestDetails.commit` is left null. `requestDetails.branch` is set to the full ref name (e.g.
`refs/tags/v1.0`), so `GitRequestDetails.isTagPush()` works correctly downstream.

**`CheckUserPushPermissionFilter`** — uses the commit author email to identify the pushing user. Null commit → null
email → rejects with "Unknown User". Fix: skip the email check for tag pushes; the user is already verified by HTTP
basic auth.

**`CheckEmptyBranchFilter`** — empty `pushedCommits` + zero `commitFrom` looks like an empty branch push. Fix: skip for
tag refs, same reasoning as the S&F hook.

**`CheckHiddenCommitsFilter`** — calls `walk.parseCommit(repo.resolve(toCommit))` where `toCommit` is the tag object
SHA. Fix: use `repo.resolve(toCommit + "^{commit}")`, consistent with all other `CommitInspectionService` callers.

**`EnrichPushCommitsFilter`** — unpacks the pack objects into the local repo clone using JGit's `PackParser`, which
handles tag objects fine. Then calls `CommitInspectionService.getCommitRange()` (fixed via `^{commit}`), which returns
empty for a tag on an existing commit. Normal behaviour.

**`ScanDiffFilter`** — calls `getDiff(repo, fromCommit, toCommit)`. `toCommit + "^{tree}"` peels through the tag chain
to the tree; this works correctly. For a new tag (`fromCommit == zeros`) the diff base falls through to
`findNewBranchBase()`, which also uses `^{commit}` and returns null (no new commits), so the diff is against the empty
tree. This produces a full-snapshot diff of the tagged commit, which is harmless for typical content checks.

**`SecretScanningFilter`** — passes `commitFrom`/`commitTo` to `gitleaks git`. Gitleaks calls native `git log`, which
peels tags natively. No special handling needed.

---

## Branches and refs

### What the proxy sees on the wire

Every `git push` sends one or more **packet lines** before the pack data. Each line has the format:

```text
<oldOid> <newOid> <refName>\0<capabilities>
```

| Field     | Meaning                                                                                                   |
| --------- | --------------------------------------------------------------------------------------------------------- |
| `oldOid`  | The SHA the client believes the ref currently points to on the remote. All-zeros (`0000…`) for a new ref. |
| `newOid`  | The SHA the client wants the ref to point to after the push. All-zeros for a **ref deletion**.            |
| `refName` | Full ref path: `refs/heads/main`, `refs/tags/v1.0`, etc.                                                  |

The null byte `\0` separates the ref triple from the capability string (e.g. `report-status side-band-64k`). Only the
**first** packet line carries capabilities; subsequent lines omit the `\0…` suffix.

`GitReceivePackParser.parsePush()` splits this line and populates `PushInfo` (proxy mode) or JGit's `ReceiveCommand`
carries the same triple (S&F mode).

### Determining the push type from the packet line

The packet line SHAs encode what kind of ref update is happening:

| `oldOid` | `newOid` | `refName`            | Meaning                                            |
| -------- | -------- | -------------------- | -------------------------------------------------- |
| `000…0`  | `abc123` | `refs/heads/feature` | **New branch** — create pointing at `abc123`       |
| `abc123` | `def456` | `refs/heads/feature` | **Branch update** — FF or force push from `abc123` |
| `abc123` | `000…0`  | `refs/heads/feature` | **Branch deletion** — remove the ref               |
| `000…0`  | `abc123` | `refs/tags/v1.0`     | **New tag** — see "Tag objects" section            |

In S&F mode, JGit's `ReceiveCommand.Type` enum maps these directly: `CREATE`, `UPDATE`, `UPDATE_NONFASTFORWARD`,
`DELETE`.

In proxy mode, `GitRequestDetails` exposes helper methods:

- `isRefDeletion()` — `commitTo` is all-zeros
- `isTagPush()` — `branch` starts with `refs/tags/`

There is no explicit `isNewBranch()` helper; filters check `commitFrom.matches("^0+$")` directly.

### New branches — what makes them tricky

A new-branch push (`oldOid` = zeros) doesn't tell you which commits are "new". The pack may contain many commits, but
some of them may already exist on the remote under a different branch. Only the commits **not reachable from any
existing ref** are genuinely new in this push.

Both modes solve this the same way — via `CommitInspectionService.getCommitRange()`:

```java
// New branch path (fromId is null or zero):
var logCmd = git.log().add(toId);
for (Ref ref : repository.getRefDatabase().getRefsByPrefix("refs/heads/")) {
    if (ref.getObjectId() != null) logCmd.not(ref.getObjectId());
}
```

This walks backward from the pushed tip, excluding anything reachable from existing branch heads. The result is only the
commits that are genuinely new.

**S&F mode**: JGit's `ReceivePack` has already unpacked the objects into its own repository, so `getCommitRange()` works
against that repo directly.

**Proxy mode**: `EnrichPushCommitsFilter` must first clone/fetch the upstream and unpack the push's pack data into the
local clone (see "How proxy mode gets a repository" below), then `getCommitRange()` can walk the combined object store.

### Branch updates — the commit range

For an existing branch update (`oldOid` is a real SHA), the commit range is straightforward:

```bash
git log oldOid..newOid
```

`CommitInspectionService.getCommitRange()` uses `git.log().addRange(fromId, toId)`, which is JGit's equivalent. This
returns exactly the commits introduced by this push.

### Force pushes (non-fast-forward)

A force push rewrites history. `oldOid` is no longer an ancestor of `newOid`.

In S&F mode, JGit classifies this as `ReceiveCommand.Type.UPDATE_NONFASTFORWARD`.
`ForwardingPostReceiveHook.buildRefUpdates()` sets `force=true` for these so the upstream accepts the rewrite.

In proxy mode, the request is forwarded as-is — the upstream git server decides whether to accept the force push based
on its own configuration. The proxy's filter chain still runs validation on the new commits, but `getCommitRange()` may
behave unexpectedly: `addRange(oldId, newOid)` only returns commits reachable from `newOid` but not `oldOid`. If the
branches diverged, commits on the old branch that were dropped are **not** included — the range shows only what was
added, not what was removed.

### Ref deletions

When `newOid` is all-zeros, the client is deleting a ref. There are no objects in the pack and no commits to validate.

**S&F mode**: `ReceiveCommand.Type.DELETE`. Hooks that iterate commands skip `DELETE` types explicitly (e.g.
`CheckEmptyBranchHook`, `CheckHiddenCommitsHook`, `DiffGenerationHook`). `ForwardingPostReceiveHook` handles deletion by
creating a `RemoteRefUpdate` with a null source ref — JGit translates this to a delete on the upstream.

**Proxy mode**: `GitReceivePackParser.parsePush()` checks `newCommit.equals(ZERO_OID)` and skips pack parsing entirely
(there's nothing to parse). `GitRequestDetails` will have `commitTo` = zeros, `commit` = null, `pushedCommits` = empty.
`isRefDeletion()` returns true, and filters should check this early and skip.

---

## How the proxy gets commit data

The two proxy modes obtain commit metadata very differently.

### S&F mode: JGit ReceivePack

JGit's `ReceivePack` handles the entire git protocol server-side. When the client pushes, JGit:

1. Receives the pack data and unpacks objects into the local repository
2. Creates `ReceiveCommand` entries for each ref update
3. Calls the pre-receive hook chain with access to the full `Repository`

Hooks can call any JGit API — `RevWalk`, `DiffFormatter`, `git.log()` — because the objects are already in the local
object store. No special setup required.

The repository is a bare repo managed by the S&F servlet, one per provider+repo combination.

### Proxy mode: clone + unpack

Proxy-mode filters run as servlet filters on an HTTP request. They don't have a local repository by default — the
request is just bytes on the wire being forwarded to the upstream.

`EnrichPushCommitsFilter` bridges this gap:

1. **Clone/fetch**: `LocalRepositoryCache.getOrClone(remoteUrl)` maintains a bare clone of each upstream repository.
   First push triggers a `git clone --bare --depth 100`; subsequent pushes do `git fetch --depth 100`. The cache is
   keyed by `owner_reponame` (derived from the URL).

2. **Unpack push data**: The push's pack data (from the HTTP request body) is fed into JGit's `PackParser`, which
   inserts the objects into the local clone's object store. This is the equivalent of what `ReceivePack` does internally
   in S&F mode.

3. **Walk commits**: With objects now in the local clone, `CommitInspectionService` can walk the commit range, generate
   diffs, etc.

The local clone is published on `GitRequestDetails.localRepository` so all downstream filters can use it.

#### Shallow clone implications

The default clone depth is 100 commits. This means:

- `getCommitRange()` for a new branch will only walk back 100 commits. Commits beyond that depth are not in the local
  clone and won't appear in the range.
- `getDiff()` for a new branch uses `findNewBranchBase()` to diff against the parent of the oldest new commit. If the
  oldest new commit's parent is beyond the shallow boundary, `resolve(parentSha + "^{tree}")` returns null and the diff
  falls back to the empty tree (full-snapshot diff).
- Secret scanning via gitleaks is passed `commitFrom..commitTo` and runs `git log` natively — it respects the shallow
  boundary silently.

For most pushes this is fine. A push with more than 100 new commits on a new branch is unusual, and the shallow clone
can be deepened via configuration (`cloneDepth`).

---

## Diff generation

### Where diffs are generated

Diffs are generated in both modes but through different code paths:

| Mode  | Component                        | When                            | What                                     |
| ----- | -------------------------------- | ------------------------------- | ---------------------------------------- |
| S&F   | `DiffGenerationHook` (order 280) | Pre-receive, post-validation    | Push diff + optional default-branch diff |
| Proxy | `ScanDiffFilter` (order 300)     | After `EnrichPushCommitsFilter` | Push diff only                           |

Both ultimately call `CommitInspectionService.getFormattedDiff(repo, fromCommit, toCommit)`.

### How diffs are computed

`CommitInspectionService.getDiff()` resolves both sides to tree objects, then runs JGit's `DiffFormatter`:

```java
ObjectId oldId = isNullCommit(fromCommit)
    ? findNewBranchBase(repository, toCommit)  // new branch: diff against merge base
    : repository.resolve(fromCommit + "^{tree}");  // existing branch: diff against old tip
ObjectId newId = repository.resolve(toCommit + "^{tree}");
```

The `^{tree}` peel works for both commits and annotated tags — it follows the chain down to the commit, then to its
tree.

### New branch diff base (`findNewBranchBase`)

For a new-branch push, diffing against the empty tree would show the entire repo snapshot — useless for review and would
trigger false-positive secret scan findings on existing files.

Instead, `findNewBranchBase()` finds the oldest new commit (same "exclude existing refs" walk as `getCommitRange()`),
then returns the **tree of that commit's first parent**. This means the diff shows only the changes introduced by the
new commits, not the entire history they're built on.

If the oldest new commit is a root commit (no parent), the base is null, and the diff does fall back to the empty tree —
but this only happens for genuinely new repositories.

### Default-branch diff (S&F only)

`DiffGenerationHook` generates a second diff when pushing to a non-default branch: the total diff of
`defaultBranch..commitTo`. This helps reviewers see the full scope of a feature branch without having to check it out.

The default branch is resolved from `HEAD` (which in a bare clone is a symbolic ref to the remote's default branch),
falling back to `refs/heads/main` or `refs/heads/master`.

This diff is stored as a separate `PushStep` with step name `diff:default-branch` and tagged as
`type: auto:default-branch` so the dashboard UI can label it appropriately.

### Hidden commits detection

The "hidden commits" check exists in both modes (`CheckHiddenCommitsHook` / `CheckHiddenCommitsFilter`) and catches a
subtle attack vector: a developer could create a branch from unapproved commits that haven't been pushed yet. Git's pack
protocol bundles all objects needed by the receiving side, including ancestor commits that the remote doesn't have.

The algorithm is:

1. **introduced** = commits from `getCommitRange(oldId, newId)` — the explicit push range
2. **allNew** = `RevWalk` from `newId`, marking all existing refs as uninteresting
3. **hidden** = `allNew` minus `introduced`

If hidden is non-empty, the push is rejected. The developer needs to get the hidden commits approved and pushed first,
then retry.

---

## Pack data parsing

### What `GitReceivePackParser` does (proxy mode only)

In proxy mode, `ParseGitRequestFilter` needs to extract commit metadata from the raw HTTP request body before JGit ever
touches it. The request body contains:

1. Packet lines (ref updates + capabilities)
2. A flush packet (`0000`)
3. Pack data (the `PACK` signature followed by pack objects)

`GitReceivePackParser.parsePush()` reads the packet line via JGit's `PacketLineIn`, then parses the first object from
the pack data manually:

- Scans for the `PACK` signature (4 bytes: `P`, `A`, `C`, `K`)
- Skips the 12-byte pack header (signature + version + object count)
- Reads the first pack entry's type+size header (variable-length encoding)
- Inflates the zlib-compressed object data
- If the type is `OBJ_COMMIT` (1), parses the raw commit content for author, committer, parent, message, and GPG
  signature

This is a **best-effort parse of the first object only**. It handles the common case (a commit push where the tip commit
is the first pack entry) but intentionally does not handle:

- Delta objects (`OBJ_OFS_DELTA`, `OBJ_REF_DELTA`) — logged as a warning
- Tag objects (`OBJ_TAG`, type 4) — throws "No commit object found"
- Packs where the commit is not the first entry
- Empty packs (lightweight tag pointing to an existing commit)

These failures are caught by the `try/catch` in `parsePush()`, and `PushInfo.commit` is left null.
`EnrichPushCommitsFilter` downstream recovers full commit data from the local clone anyway — the pack-parsed commit is
just an early-availability optimization for `ParseGitRequestFilter`.

---

## Large pushes and chunked transfer encoding

### The problem

When a push's pack data exceeds the git client's `http.postBuffer` (default 1 MiB), git switches from a single
`Content-Length` POST to `Transfer-Encoding: chunked`. The body content is identical — pkt-line ref updates + flush +
PACK data — but the HTTP framing changes from "here's N bytes" to "here are chunks of variable size, terminated by a
zero-length chunk."

Many reverse proxies deployed in front of fogwall do not faithfully forward chunked request bodies. Observed failure
modes (confirmed on HAProxy-based OpenShift Routes):

- **Early termination**: the proxy forwards the first HTTP chunk (a few bytes of pkt-line data), then sends the chunked
  terminator. The server receives a valid but tiny body — just the pkt-line length prefix — and `ParseGitRequestFilter`
  fails with `EOFException: Short read of block`.
- **Request splitting**: the proxy dechunks the body, buffers the remainder, and forwards it as a separate
  `Content-Length` request. The server sees two requests: one with a few bytes of pkt-line data, and a second with raw
  PACK binary (no pkt-line prefix). The second request fails with `Invalid packet line header` because the body starts
  mid-stream.
- **Keepalive contamination**: when the server doesn't consume the full body of the truncated first request, leftover
  bytes bleed into the next request on the same TCP connection. The next request's body starts with binary PACK data
  instead of pkt-line headers.

Small pushes (< 1 MiB) use `Content-Length` and are unaffected — the proxy forwards the body as a single unit.

This is not a Jetty bug or a fogwall bug. The same issue affects any HTTP backend behind a proxy that doesn't support
chunked request forwarding. GitHub, GitLab, and Gitea avoid this because they either terminate HTTP at the edge (no
generic proxy in the path) or explicitly configure their proxy layer for streaming uploads.

### Server-side mitigation: `BlockingContentHandler`

`BlockingContentHandler` is a Jetty `Handler.Wrapper` that reads the full request body at the core Handler level before
the servlet layer sees it. It uses Jetty 12's `Content.Source.read()` / `Content.Source.demand()` cycle directly on the
`Request` object:

1. Call `read()` — returns a `Content.Chunk` or `null`
2. If `null`: call `demand()` with a `CountDownLatch` callback, then `await()` until more data arrives from the network
3. If a chunk: copy its bytes into a `ByteArrayOutputStream`, release the chunk
4. Repeat until a chunk with `isLast()=true`

The accumulated body is wrapped in a `BufferedBodyRequest` (a `Request.Wrapper` that overrides the `Content.Source`
methods) so the servlet layer's `HttpInput` reads from the buffered copy. Both the transparent proxy filter chain
(`RequestBodyWrapper.readAllBytes()`) and the store-and-forward path (JGit's `ReceivePack`) get the complete body
without touching the network.

GET requests pass through without buffering.

#### Why not use Jetty's `EagerContentHandler`?

`EagerContentHandler` was the first attempted fix. It is designed to eagerly buffer the full body before dispatching to
the servlet. However, its internal `RetainedContentLoader.getInvocationType()` returns `NON_BLOCKING`, which causes
`doHandle()` to be called synchronously on Jetty 12's EPC (Execute-Produce-Consume) reserved thread — where blocking I/O
does not work. The body was still truncated in production.

#### Why not just dispatch to a blocking thread?

The second attempt used a simple `Handler.Wrapper` that called `request.getContext().execute(...)` to move servlet
execution to a `QueuedThreadPool` worker thread, then relied on the servlet layer's `HttpInput.readAllBytes()`. This
also failed — `HttpInput` returned `-1` prematurely even on a blocking thread.

The third attempt used `Content.Source.asInputStream(request).readAllBytes()` at the Handler level, bypassing
`HttpInput`. This also returned truncated data — `ContentSourceInputStream` wraps the same `Content.Source` and
exhibited the same premature EOF behaviour.

The working approach reads from `Content.Source` directly using the `read()`/`demand()` loop, which is the lowest-level
API available and handles partial delivery correctly regardless of transfer encoding or proxy reframing.

### Client-side workaround

Increasing the git client's post buffer avoids chunked encoding entirely:

```bash
git config --global http.postBuffer 524288000
```

This forces git to buffer the entire pack in memory and send it as a single `Content-Length` POST, which all proxies
handle correctly. The tradeoff is higher client memory usage for large pushes.

### Proxy-side fixes

If you control the reverse proxy, configure it to buffer the full request before forwarding to the backend:

**nginx**:

```
proxy_request_buffering on;   # buffer the full request before forwarding (default)
client_max_body_size 500m;    # allow large pack uploads
```

**HAProxy**:

```
option http-buffer-request     # buffer the full request before forwarding
timeout http-request 300s      # allow time for large chunked uploads to complete
```

Consult your proxy's documentation for equivalent settings if you use a different load balancer.

---

### Why the pack parser exists alongside `EnrichPushCommitsFilter`

`ParseGitRequestFilter` runs at order `MIN_VALUE + 1` — it's the first filter. It needs to populate `GitRequestDetails`
before any other filter runs. `EnrichPushCommitsFilter` runs at `MIN_VALUE + 2` — immediately after — but requires a
network clone/fetch which may fail.

The pack parser gives `ParseGitRequestFilter` a synchronous, no-network way to extract the head commit's metadata. If it
succeeds, `requestDetails.commit` is available immediately. If it fails (tag push, delta-only pack, etc.), the commit is
null and filters that need it wait for `EnrichPushCommitsFilter` to populate `pushedCommits` from the local clone.
