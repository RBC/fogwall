# Two proxy modes

## Server mode (`/server/<provider>/<owner>/<repo>.git`)

The upstream repository is cloned locally on first access. When a developer pushes, JGit's `ReceivePack` receives the
entire pack locally before anything is forwarded. Pre-receive hooks validate the push; if it passes (and any required
approval is granted), a post-receive hook forwards it to upstream using the developer's credentials.

This mode can stream progress messages to the git client in real time via JGit sideband packets — so the developer sees
`remote: [step] author email OK` lines as each validation step completes.

> **Naming:** this mode was formerly called _store-and-forward_. It is served under the canonical `/server/…` prefix as
> of 1.4.0; the legacy `/push/…` prefix still routes to it as a deprecated alias, so existing git remotes keep working.

## Transparent proxy (`/proxy/<provider>/<owner>/<repo>.git`)

An HTTP reverse proxy forwards the git protocol directly to upstream. A servlet filter chain inspects the pack data
before it reaches upstream. Validation results are collected and, if anything fails, a single error response is sent.
The developer's git client is talking to a forwarding proxy, not a JGit endpoint, through a single HTTP request/response
cycle. A temporary local clone is still used to unpack the pack data and walk the commit range for validation, but the
push is forwarded via HTTP proxy rather than a JGit `push` command.

This mode cannot stream incremental feedback. The reason is structural: an HTTP response is a single buffered reply. The
filter chain runs to completion inside one request/response cycle — there is no mechanism to flush partial output to the
git client mid-chain. Validation filters accumulate their results; `ValidationSummaryFilter` and `PushFinalizerFilter`
collect everything and write one response at the end. Server mode avoids this constraint entirely because JGit's
`ReceivePack` owns the connection and can call `sendMessage()` at any point, streaming sideband packets to the client as
each hook completes.

## Choosing a mode

| Concern                       | Server mode                                | Transparent proxy                                                         |
| ----------------------------- | ------------------------------------------ | ------------------------------------------------------------------------- |
| Live progress feedback        | Yes — per-step sideband messages           | No — single terminal response                                             |
| Local storage required        | Yes — receives the push into a local clone | Yes — clone needed for pack inspection                                    |
| Approval workflow             | Blocks git session until approved          | Records push, polls for approval (requires second push)                   |
| Pack inspection               | Via JGit `ReceivePack` APIs                | Pack unpacked into local clone for inspection, then HTTP-proxied upstream |
| Resumable push after approval | Same session                               | New push to `/proxy/` re-run detects prior approval                       |

Both modes share the same validation logic and push store. Both are always active for every configured provider — there
is currently no per-provider toggle to disable one mode.

## Proposals (a dedicated listener per provider)

_Available since v1.4.0, opt-in per provider — see [docs/internals/scm-api-proxy.md](../internals/scm-api-proxy.md)._

A third HTTP surface, opt-in per provider (`providers.<name>.proposals.enabled`), for SCM CLI tools rather than git
itself — proxying `gh`'s issue/PR, `glab`'s issue/MR, and `tea`/`fj`'s issue/PR create-edit-comment-review traffic
instead of a git push. Unlike the two modes above, it does not touch a local repository clone; it inspects and relays a
small request/response pair.

Unlike server mode and the transparent proxy, which share the main port under path prefixes, **each enabled provider
gets its own listener** (`providers.<name>.proposals.port`) with its dialect mounted at that listener's root —
`/api/graphql`, `/api/v4/*`, `/api/v1/*`. This is forced by the clients: `gh` and `fj` address the API from the host
root and discard any path prefix, and a single shared root listener would collide between two instances of the same
platform. `registerScmApiListeners` binds each context to its connector using Jetty's `"@connectorName"` virtual-host
form, and relaxes URI compliance on the GitLab and Gitea/Forgejo connectors so an encoded separator isn't rejected as an
ambiguous path separator — GitLab names a project as one `owner%2Frepo` segment, and Gitea encodes a repository-relative
file path into one segment of its blob endpoints. The GitHub connector keeps the strict default. The relaxation only
gets those requests past the parser; `ScmApiRestPathPolicy` decides where a `%2F` is actually permitted, per dialect.

The three platforms use genuinely different wire formats, so each gets its own filter chain rather than one shared
pipeline forced to fit all — the chains are plain `jakarta.servlet.Filter`s (not `FogwallFilter`s — the git-specific
`GitRequestDetails`/`PushStep` request model doesn't apply here), registered by `FogwallServletRegistrar`:

```
GitHub (GraphQL), via registerScmApiProxy:
ScmApiAuditFilter (outermost, try/finally — one record per mutation, plus refusals of authenticated callers)
  └─ ScmApiAuthenticateFilter (token → fogwall identity, via the same PushIdentityResolver git push uses)
       └─ ScmApiGitHubGateFilter (parse → allowlist/resolve/authorize a mutation, or provider-level-gate a read)
            └─ ScmApiContentInspectionFilter (blocked terms, secrets, PII bundles over the whole payload)
                 └─ ScmApiGraphQlForwardServlet (relays to the GraphQL endpoint with the caller's own token)

GitLab (REST), via registerScmApiProxyGitLab:
ScmApiAuditFilter (same as above)
  └─ ScmApiAuthenticateFilter (same as above)
       └─ ScmApiGitLabGateFilter (allowlist method+path → authorize using owner/repo straight from the URL)
            └─ ScmApiContentInspectionFilter (same as above)
                 └─ ScmApiRestForwardServlet (relays the sub-path/query/body to the provider's REST API base URL)

Gitea/Forgejo (REST), via registerScmApiProxyForgejo:
ScmApiAuditFilter (same as above)
  └─ ScmApiAuthenticateFilter (same as above)
       └─ ScmApiUserAgentFilter (classify + audit the client; optionally refuse non-CLI callers)
            └─ ScmApiForgejoGateFilter (same shape as GitLab's, different allowlist table)
                 └─ ScmApiContentInspectionFilter (same as above)
                      └─ ScmApiRestForwardServlet (shared with the GitLab dialect)
```

(`ScmApiUserAgentFilter` sits in all three chains; it is shown once, above, to keep the other two readable.)

Mechanics that carry the actual security decisions:

- **AST-based mutation allowlisting (GitHub).** The GraphQL request body is parsed (`graphql-java`) into a real AST; the
  allowlist matches on the parsed mutation's schema field name, never a substring of the raw query text — a client alias
  or a string literal containing a mutation name can't spoof the check.
- **Opaque node-ID resolution (GitHub only).** A GraphQL mutation references its target only by an opaque node ID, never
  `owner/repo` — `GitHubNodeIdResolver` resolves it (cached, with a TTL that is a security parameter, not just a perf
  knob: a node ID can outlive a repo rename/transfer) before `RepoPermissionService.isAllowedToPropose` can run. GitLab
  has no equivalent step: its REST calls carry `owner/repo` directly in the URL (verified from live `glab` captures —
  see docs/internals/scm-api-proxy.md), so `ScmApiGitLabGateFilter` reads the authorization target straight off the
  matched path via `GitLabRestAllowlist`.
- **One dialect for `tea` and `fj`.** The two Gitea/Forgejo CLIs speak the same server API and differ only in which
  subset they use, so `ForgejoRestAllowlist` is the union of both. The union is load-bearing: `tea pr close` sends
  `PATCH /pulls/{n}` while `fj pr close` sends `PATCH /issues/{n}`, so allowlisting one form silently breaks the other
  CLI. They are deliberately **not** told apart by `User-Agent` — that header is caller-controlled, so branching
  authorization on it would let a caller select the looser rule set.
- **`User-Agent` is evidence, never an input to a decision.** `ScmApiUserAgentFilter` records the raw header (each CLI
  advertises its version, the anchor for spotting a wire-format change after an upgrade) and can optionally refuse
  unrecognised client types. It is strictly subtractive: enabling it only ever denies more, so a forged header buys
  nothing beyond the baseline the allowlist and permission engine already enforce.
- **Allowlists match the raw, undecoded URI.** `getPathInfo()` is decoded by the container, which would split GitLab's
  `acme%2Fwidgets` into two segments — turning every `glab` mutation into a fail-closed denial, and in principle letting
  an encoded slash shift which repository is authorized. `ScmApiRestPath` reads `getRequestURI()` instead.
- **The upstream response is read, not just relayed.** A push response is an acknowledgement; a client-server API's
  response is the authoritative statement of what now exists, and fogwall already holds it. The forwarders relay a
  mutation's response to the client while keeping a bounded copy, and `ProposalRegistrar` then reads it through the
  dialect's `ProposalResponseReader` — number, URL, node ID and state — into the `scm_api_proposals` registry, a mutable
  current-state table that the append-only `scm_api_action_records` point at via `proposal_id`. A read is still streamed
  and never kept. The registry write happens after the client has its response and never throws into the request, so a
  registry failure costs the link, not the mutation.
- **Content inspection covers the payload, not a field list.** `ProposalContentInspector` runs `proposals.block`,
  gitleaks and the content-pattern bundles over the raw bytes, every JSON key and scalar at any depth, the query string
  in both forms, and — for GitHub — the GraphQL query's own literals. It **fails closed**: unlike the push path, a
  proposal that cannot be scanned is refused, because a forwarded one has already published its text upstream. For the
  same reason the PII bundles block here rather than warning as they do on a push — a warning needs a reviewer, and this
  path holds nothing for one to look at.
- **Reads stay cheap in all dialects.** No dialect resolves or permission-checks reads individually — an authenticated
  caller's reads are forwarded, keeping the default read cost near pass-through.
- **No URL rule layer.** `UrlRuleRegistry` gates the git path because a fetch of a public repository arrives with no
  credential, leaving the URL as the only thing to match on. Every request here is authenticated —
  `ScmApiAuthenticateFilter` refuses a missing token and one that resolves to no fogwall user — so authorization runs
  against the caller directly, through `RepoPermissionService`.
