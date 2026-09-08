# Proposals

_Available since v1.4.0, opt-in per provider._

Extends fogwall past `git push` into the rest of the contribution lifecycle — proxying the `gh` CLI's issue/PR
create-edit-comment-review traffic through the same identity resolution, permission engine, and audit trail as the
git-push path. See [SCM API proxy](../configuration/proposals.md) for the full config reference and
[docs/internals/scm-api-proxy.md](../internals/scm-api-proxy.md) for the design rationale; this section covers what an
operator needs to understand before turning it on.

## Token model and the egress assumption

Developers bring their own personal access token. fogwall forwards it upstream unchanged after inspecting the request,
and never mints or supplies a credential for this path.

[SCM OAuth](scm-oauth.md) is a separate mechanism, for fogwall-managed operations — today the account-linking UI, later
potentially fogwall acting on a user's behalf. It does not provide a token for a CLI.

Enforcement is **content interception**, not the credential. So what you get here depends on something fogwall does not
provide: **direct access to the SCM API has to be blocked elsewhere**, or a developer can bypass the proxy with the same
token. fogwall governs the sanctioned API host and inspects what goes through it; organization-wide egress control is
better served by traditional web proxies and network security appliances.

The git-push path already works this way — a developer with a valid PAT can `git push` straight to github.com if nothing
stops them.

## Enabling it

One switch, off by default, per provider — plus the port that listener will bind:

```yaml
providers:
  github:
    proposals:
      enabled: true
      port: 9443 # required — see "Each provider needs its own port" below
```

**Each enabled provider needs its own port.** The SCM API proxy does not share the main fogwall server port that serves
git traffic, and does not sit under a URL path: the dialect is mounted at the root of a dedicated listener
(`/api/graphql` for GitHub, `/api/v4/*` for GitLab, `/api/v1/*` for Gitea/Forgejo). That is forced by the clients — `gh`
and `fj` address the API from the host root and silently discard any path prefix — and a single shared listener would
collide between two instances of the same platform, since every GitLab claims `/api/v4`. fogwall refuses to start if a
provider has `proposals.enabled: true` with no port, rather than opening a listener no CLI could reach. Developers are
then given a host and port; see [the user guide](../user/proposals.md).

Optionally add `require-known-cli: true` to refuse callers whose `User-Agent` isn't one of the four recognised SCM CLIs
— browsers, bare `curl`, unrecognised automation. The raw header is recorded on every audit record either way, which is
how you spot a CLI upgrade changing its wire format.

> [!WARNING] `User-Agent` is set by the client and can be forged. This is hardening, not a security control — it can
> only deny requests that would otherwise be allowed, and never grants anything.

## TLS on the proposals listeners

**Every one of these ports has to be reachable over HTTPS.** `gh`, `glab`, `tea` and `fj` all address a custom host over
HTTPS and give you no way to ask for plain HTTP, so a plaintext listener is unreachable by the tools it exists to serve.
TLS must terminate somewhere in front of it — you have two shapes:

- **Terminate at the edge.** An ingress, route, or load balancer per provider port, with fogwall's listeners left on
  plain HTTP behind it. This is the usual Kubernetes/OpenShift shape: one Service exposing the proposals ports, one
  Ingress per provider, each with its own hostname and certificate.
- **Terminate at fogwall.** Configure [`server.tls`](../configuration/tls.md) and every proposals listener inherits it
  automatically — same certificate, its own port. There is no per-provider TLS block to configure: the certificate is
  issued per hostname and these listeners differ only by port. If you give each provider its own hostname _and_
  terminate at fogwall, the certificate's SANs must cover all of them.

fogwall can't tell whether something upstream is terminating TLS for it, so it doesn't guess: with `server.tls` unset it
logs a warning at startup naming each plaintext listener. That warning is expected and harmless in the edge-termination
shape. Treat a CLI reporting a connection or handshake error against a proposals port as this, until ruled out.

If you terminate at fogwall with a certificate from an internal CA, the CLIs are Go binaries and will need that CA in
their trust store (or `SSL_CERT_FILE` pointing at it) — worth saying in whatever you hand developers.

**If you terminate at the edge, check that your ingress does not decode or normalise the request path.** GitLab
addresses a project as a single `owner%2Frepo` segment, and Gitea encodes a repository-relative file path into one
segment of its blob endpoints. Both encoded slashes have to reach fogwall intact: decoded, the segment splits and the
request names a different repository, which fogwall refuses. nginx-ingress changes path handling once a `rewrite-target`
with a capture group is involved; HAProxy-backed OpenShift Routes are generally pass-through.

The failure mode is narrow enough to be confusing — GitLab denied or 404ing while GitHub works fine — so confirm it
rather than assume it. `curl` a project path with an encoded slash through the ingress and check what fogwall logs as
the request URI.

## What the allowlist permits

Enabling a provider does not expose its API. fogwall forwards a fixed set of operations, held in code rather than
configuration, and denies everything else:

| permitted                                    | denied                                                        |
| -------------------------------------------- | ------------------------------------------------------------- |
| issue create, edit, close, comment           | submitting a review, approving                                |
| PR/MR create, edit, close, comment           | merge                                                         |
| label, assignee and reviewer-request changes | release, tracked-time, dependency and project-board endpoints |

Labels, assignees and reviewers are permitted whichever way the CLI sends them — as fields on the create, or as the
separate follow-up call each CLI makes when the same attribute is changed by an edit. **Requesting** a review is
permitted; **submitting** one is not, and they are different endpoints. See [Authorization](#authorization) for the full
scope of a `PROPOSE` grant.

Anything the allowlist does not recognise is denied, so a CLI reaching a new endpoint after an upgrade is refused rather
than forwarded. Per-CLI command names are in [User Guide](../user/proposals.md); the endpoint and mutation tables behind
them are in [docs/internals/scm-api-proxy.md](../internals/scm-api-proxy.md).

## Authorization

Per-repo authorization for mutations goes through the ordinary `permissions:` mechanism — grant a user `PROPOSE` on the
repos they should be able to file issues/PRs against (see [Permissions](../configuration/permissions.md)). `PROPOSE` is
its own grant: a user can hold it without push access, or hold `PUSH` without it.

It covers the full request surface of the allowlisted endpoints on a matching repo, not only title, body and comment —
and not only _fields_. Where a CLI changes an attribute through its own call rather than a field, that call is
allowlisted too and authorized against the same repo: GitHub sends `replaceActorsForAssignable` for any `--assignee` and
`requestReviewsByLogin` for any `--reviewer`, and Gitea reaches `POST /issues/{n}/labels` for `--add-labels`. Both forms
are behind one of the CLIs' own flags (`glab mr update --target-branch`, `gh pr edit --base`, `--add-assignee`,
`--add-label`, `--milestone`, `--lock-discussion`), and `tea` PATCHes the whole object on every edit. Three
consequences:

- A pull/merge request's **base branch** can be retargeted, always within the same repository — no allowlisted edit
  endpoint takes a repository-valued field, so a proposal cannot be moved elsewhere.
- A few associations reach **beyond the repo**: GitHub projects are org-level, and on GitLab a milestone or epic can be
  group-level. (GitHub and Gitea milestones are repo-scoped.) This is the only effect not confined to the matched repo.
- One command is often **several audited operations**. `gh pr create --label --assignee --reviewer` is a create plus
  three follow-up calls, each authorized against the same repo and recorded separately, so the audit trail holds more
  rows than the developer ran commands.

Merge, and submitting or approving a review, stay out of reach: separate endpoints, none allowlisted. Requesting a
reviewer is permitted — a different operation from giving the verdict.

## Content inspection

The prose a proposal carries — a pull/merge request title and description, a comment body — is inspected before it is
forwarded, against three sets of rules: the blocked literals and patterns in `proposals.block`; gitleaks, when
`secret-scan.enabled` is on; and the built-in PII/identifier bundles, when `content-patterns.enabled` is on with at
least one bundle selected. `proposals.block` is separate from `diff-scan.block`: one governs pushed diffs, the other
proposal content. Secret scanning and the pattern bundles are shared with the push path — neither is diff-specific.

This is not optional hardening. Without it, a contributor blocked from _pushing_ a secret can paste the same secret into
a pull request description and fogwall relays it verbatim.

Inspection reads the **whole request body**, not a list of known fields: every key and scalar in the JSON, at any depth,
plus the raw bytes. The raw reading covers anything an extractor does not name — a dialect gaining a new prose field
stays covered — while the decoded reading defeats escaping, since a token written as an escape sequence matches nothing
as raw text but is plain once decoded. GitHub adds a third reading, the GraphQL query's own literals: a GraphQL request
wraps its query in JSON, so decoding the transport leaves GraphQL's own string escaping intact, and arguments inlined in
the query text never appear as JSON values at all.

A content violation is recorded as `REJECTED`. `DENIED` is for operations that are not allowlisted, or that the caller
holds no `PROPOSE` grant for.

```yaml
proposals:
  block:
    literals:
      - "internal.corp.example.com"
    patterns:
      - '(?i)https?://[a-z0-9.-]*\.corp\.example\.com\b'
```

With no `proposals.block` entries configured, only secret scanning applies.

Secret scanning **fails closed here**, unlike the push path: if scanning is enabled but the scanner cannot run, the
proposal is refused. A push that slips through is still recorded and reviewable afterwards, whereas a forwarded proposal
has already published its text upstream where fogwall cannot reach it.

### PII bundles block here, rather than warning

Content-pattern bundles (`content-patterns.bundles` — SIN, SSN, NINO and the rest) are
[WARN-only on the push path](../configuration/content-pattern-scanning.md): a match is surfaced to the human reviewer
every push already requires, and never blocks. A proposal has no such reviewer — it is forwarded or refused — so a
warning recorded against a description that is already upstream is not a control. A match therefore refuses the
proposal, recorded as `REJECTED` alongside the data type and jurisdiction. The matched value itself is never written to
the audit record; it is the thing the rule exists to withhold.

Set `content-patterns.scan-proposals: false` to keep bundle scanning on pushes while leaving proposals to
`proposals.block` and secret scanning alone.

When content inspection is what refused a request, the request variables are deliberately **not** stored on the audit
record. The offending text is the payload, so keeping it would put the secret fogwall just blocked into fogwall's own
database; the recorded reason still names the rule that matched, with the matched value redacted by the scanner.

## Why reads and mutations are gated differently

Mutations get real per-repo enforcement, checked against that user's `PROPOSE` grants — how the target repo is
determined differs by dialect: GitHub's GraphQL mutation carries only an opaque node ID, resolved to `owner/repo` via a
cache (TTL is itself a security parameter — see the configuration reference); GitLab's REST calls carry `owner/repo`
directly in the URL, so no resolution step is needed. Reads (`gh issue list`, `glab mr list`, etc.) are **not**
individually resolved or permission-checked in any dialect — they are forwarded for any authenticated caller, which
keeps read traffic cheap. Per-repo read gating is not currently implemented.

## Audit trail

Every proxied **mutation** produces one audit record — who, the resolved repo, the operation performed, the request
payload (GitHub's GraphQL variables, or the REST body on the other dialects), and the allow/deny outcome — following the
same auditability bar as the push path. The payload is dropped from a record whose content inspection refused the
request, so a secret fogwall blocked is not kept by fogwall; the reason names the rule and field instead. These are
viewable in the dashboard under **SCM API** (a plain list, no approval workflow — these are already-decided audit
records), or queryable directly from the `scm_api_action_records` table/collection.

A forwarded mutation's record also carries **what the upstream answered**: its HTTP status, and — read from the
upstream's own response — the pull/merge request or issue the mutation created or touched. Those live in a second
table/collection, `scm_api_proposals`, keyed on what the upstream calls the thing (provider, repository, kind, number),
holding its URL, title and current state as last reported through fogwall, and pointing back at the action records that
created and last touched it. The audit log stays append-only; the registry is what changes when a proposal opened
through fogwall is later closed through it. A proposal opened elsewhere and then edited or closed through fogwall is
registered from that response too. The one gap is GitHub: `gh`'s edit and close mutations return nothing but an
acknowledgement, so a GitHub proposal fogwall never saw created stays unregistered until a response names it. Merges are
not proxied, so a `merged` state is only ever what an upstream response reported for a proposal fogwall was touching.

A **refused** request is recorded too, once the caller has been authenticated — including one fogwall turned away
because the endpoint matched no allowlist rule, where there is no operation to name and `mutation_field` is null (the
reason carries the method and path instead). That case is how you notice a CLI upgrade has started calling an endpoint
the allowlist doesn't know: the failures show up here, attributed to a user and a client version, rather than reaching
you as a bug report.

Two things are deliberately **not** recorded. Successful **reads** produce no record, which is what keeps the read path
close to pass-through. Neither does anything refused _before_ authentication — an unrecognised client, or a token that
resolves to no user — matching the push path, where a request rejected before it parses as a push writes no push record.
It also means writing to the audit trail costs valid credentials, so it can't be filled by an anonymous caller.

Reads are also not restricted by path: a `GET` against the provider's API, or a GraphQL `query`, is forwarded for any
authenticated caller. A read changes nothing upstream, and it returns only what the caller's own token would return
asking the provider directly — fogwall relays that token and grants nothing on top of it. What it does mean is that **if
fogwall is the sanctioned route to a self-hosted provider inside your perimeter, read traffic through it is not in
fogwall's audit trail.** The provider's own access logs are the record of what was read. Worth knowing you are relying
on them, rather than discovering it during an investigation.
