# fogwall — Claude context

## Positioning

fogwall is not "a Java rewrite of git-proxy that solves an OSPO-approval problem." Think of it as a general-purpose
**gateway/integration layer for an enterprise's software estate**, with git push validation as the first fully-built use
case, not the ceiling. Design decisions should keep the door open for:

- **SDLC/SCM control plane** — a single policy-enforcement chokepoint sitting in front of heterogeneous SCM platforms
  (GitHub, GitLab, Bitbucket, Forgejo/Gitea, and eventually non-git systems), so a regulated org doesn't need bespoke
  compliance tooling bolted onto each one individually.
- **M&A / subsidiary integration gateway** — a way to bridge two orgs' disparate SCM estates during an acquisition or
  integration without granting direct cross-boundary network access, while still enforcing each side's policy.
- **Inner-source enablement** — the trust/approval/audit layer that lets a regulated org run an internal
  open-source-style contribution model without each app team reinventing review and provenance controls.

When evaluating a new feature, prefer the more general abstraction (provider-agnostic, protocol-agnostic where
reasonable) over one that only serves the git-push case, even if git push is what's shipping today.

## Design principles

fogwall sits at a security boundary. When a design choice pits security against convenience, security wins — but treat
that as a rare, real tradeoff to name explicitly, not a reflex; a control developers route around because it's unusable
isn't actually providing security.

- **Security is non-negotiable.** Never weaken the correctness of a validation or approval control for the sake of
  ergonomics. Where a feature must pick between "safe by default" and "convenient by default," default to safe and make
  the convenient path an explicit, visible opt-in (self-certify grants, admin override, auto-approve mode) — never a
  silent default.
- **Auditability and transparency are part of the security model, not a nice-to-have.** Every decision fogwall makes
  (blocked, approved, forwarded, overridden) should be explainable after the fact — who, which rule, what evidence — not
  just enforced in the moment. A feature that can't produce an audit trail for its own decisions isn't done.
- **Don't let roadmap ambition become shipped-system complexity.** The gateway/integration-layer vision above is a north
  star, not a mandate to wire every backlog item into one interdependent system. Prefer features that are individually
  optional and composable — an org should be able to run only the pieces it needs — over a design where understanding or
  operating one feature requires understanding all of them. If a new capability would raise the baseline complexity for
  someone not using it, that's a signal to make it opt-in or a separate module rather than folding it into the core
  path.

## Repository layout

| Module              | Purpose                                                                                                     |
| ------------------- | ----------------------------------------------------------------------------------------------------------- |
| `fogwall-core`      | Shared library: filter chain, JGit hooks, push store, provider model, approval abstraction                  |
| `fogwall-server`    | Standalone proxy-only server (`FogwallJettyApplication`) — no dashboard, no Spring                          |
| `fogwall-dashboard` | Dashboard + REST API (`FogwallDashboardApplication`) — Spring MVC, approval UI, depends on `fogwall-server` |

## Architecture

Two proxy modes, both configurable per-provider:

- **Store-and-forward** (`/push/<provider>/<owner>/<repo>.git`) — JGit ReceivePack receives the push locally, runs a
  pre-receive hook chain (`AuthorEmailValidationHook` → `CommitMessageValidationHook` → `ValidationVerifierHook`), then
  `ForwardingPostReceiveHook` pushes upstream using the client's credentials.
- **Transparent proxy** (`/proxy/<provider>/<owner>/<repo>.git`) — Jetty's `ProxyServlet` forwards the request; a
  servlet filter chain (`ParseGitRequestFilter` → `EnrichPushCommitsFilter` → validation filters) inspects the pack data
  before it reaches the upstream.

Virtually all core features (validation rules, approval model, provider abstraction) must be shared between the two
modes. The main difference is that store-and-forward can stream progress messages live to the client via JGit hooks,
while transparent proxy must buffer everything and send one response at the end of the filter chain.

## Client output — streaming constraint

**Store-and-forward** uses JGit `ReceivePack` pre-receive hooks. Each hook can call `rp.sendMessage()` at any point and
the message streams to the git client immediately as a sideband progress packet (`remote: …`). This is how per-step
progress lines are sent live.

**Transparent proxy** uses servlet filters. The HTTP response is a single buffered reply — there is no mechanism to
stream partial output mid-filter-chain. Validation filters must _accumulate_ their result and return;
`ValidationSummaryFilter` (order `Integer.MAX_VALUE - 3`) and `PushFinalizerFilter` (order `Integer.MAX_VALUE - 1`)
collect everything and write one response at the end using `sendGitError`.

## Lineage

fogwall's push-validation core traces back to [finos/git-proxy](https://github.com/finos/git-proxy) — the Node.js
original designed the Action/Step model, Sink interface, approval lifecycle, and multi-provider architecture that
fogwall's own abstractions are informed by. Refer to it for prior art when porting or extending that specific piece of
the system. It is a reference point, not a spec fogwall is obligated to mirror going forward — fogwall's roadmap
(gateway/integration-layer use cases above) extends past what git-proxy set out to do.

## Development

Detailed build, test, run, and Docker Compose instructions live in [CONTRIBUTING.md](CONTRIBUTING.md) — treat it as the
source of truth for exact commands, since it's written for human contributors and kept current. In short:

- `./gradlew spotlessApply && ./gradlew build` — format then compile + unit test
- `./gradlew e2eTest` — e2e tests (requires Docker/Podman)
- `bash compose.sh -- up -d` — local stack (fogwall + Gitea); see CONTRIBUTING.md for auth/db overlay flags
- Gradle caches test results — pass `--rerun` when adding tests or touching coverage-relevant code, e.g.:
  `./gradlew :fogwall-core:test :fogwall-core:jacocoTestCoverageVerification --rerun`. Always verify the jacoco
  threshold locally before pushing — CI runs without cache and will catch it.

## Commit conventions

- Always squash related commits into one before pushing — use `git reset --soft` to squash, not `git rebase -i`
  (requires TTY).
- Always include a `Co-Authored-By` trailer crediting the Claude model that did the work (e.g.
  `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>` — use the current model's name, not this example, if it
  differs). This is a project transparency requirement.
- Always include `closes #N` / `resolves #N` in commit messages when addressing a GitHub issue.
- Never add `[ci skip]` to commits unless explicitly asked.

## Backwards compatibility

Past the 1.0.0 line (current version well past it — see `build.gradle`) — respect backcompat, don't break freely:

- **Config keys** — don't rename/remove without a deprecation path; accept old and new for at least one minor release.
- **SQL schema** — changes go through `DatabaseMigrator` (new migration file + registry entry); never edit an applied
  migration.
- **Mongo collections** — don't rename once shipped; a rename needs a migration step (copy + drop), documented.
- **REST API shapes** — additive only, no breaking field removals.
- Java APIs inside `fogwall-core` are still internal and can break between minors until a stable embedding story is
  declared.

Before renaming a config key, table, column, or collection: pause and ask — the answer is almost always "ship a
migration instead."

## Testing conventions

- Always use JUnit assertions (`org.junit.jupiter.api.Assertions.*`) — not manual `if`/`throw` checks.
- E2e tests use Testcontainers (Gitea) + `JettyProxyFixture`. Credentials in the clone URL are forwarded to upstream
  Gitea, so they must be valid Gitea credentials. Use `GiteaContainer.ADMIN_USER`/`ADMIN_PASSWORD` or create test users
  via `createTestUser()` / `addTestUserAsCollaborator()` — never invent fake usernames that won't authenticate upstream.

## Configuration

Refer to [docs/CONFIGURATION.md](docs/CONFIGURATION.md) for detailed docs on YAML config structure, environment variable
overrides, and provider-specific settings.

## Roadmap & architecture

There are gists linked in the root README. Only look up these details as necessary for planning refactors or
understanding design rationale. The code itself is the source of truth for how the system works ultimately.
