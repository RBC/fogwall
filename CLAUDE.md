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
- **fogwall sits inline on every push at large-enterprise scale — the default path must stay cheap.** A validation step
  that's merely "a bit slow" in isolation becomes a real latency and throughput problem multiplied across an org's whole
  clone/push/fetch volume. Expensive work (deep diff scanning, external API calls, full-history inspection) is opt-in,
  not opt-out: gate it behind explicit config, default it off, and where practical make it size/scope-bounded so an
  operator who opts in can still bound the cost. When proposing a new validation feature, ask what it costs per push at
  high volume before asking whether it's a good idea.
- **Parity across peer technologies is a feature requirement, not a nice-to-have.** fogwall is general-purpose even when
  a given deployment only needs one driver. When a capability lands, it lands across the whole axis it sits on — or the
  gap is named in a tracked issue at ship time, never just a code comment ("JDBC-only for now" in a comment is how gaps
  get lost). Genuine exceptions exist (an upstream API that simply lacks the capability, a protocol difference that
  makes a feature impossible in one mode) — those are fine, but they are documented and deliberate, not the silent
  residue of implementing against whichever driver was convenient that day. The parity axes:
  - **both proxy modes** — transparent proxy and server mode (formerly store-and-forward), including both of server
    mode's transports (HTTP and SSH)
  - **both database families** — JDBC and MongoDB get equivalent store implementations; and within JDBC, the SQL
    derivatives (H2, Postgres, MySQL, MariaDB) behave consistently
  - **all providers** — GitHub, GitLab, Forgejo/Gitea/Codeberg, Bitbucket do the same thing wherever possible and
    reasonable

- **Prefer git primitives over provider APIs.** For commit, ref, reachability, and content questions, ask the local
  mirror through JGit. Provider REST APIs answer a looser question (fork-shared object storage, for one) and are
  reserved for what git cannot answer: identity resolution, key listing, repo visibility. If the mirror is wrong, fix
  its accuracy rather than swapping oracles.
- **fogwall is not in the encryption/KMS business.** Credential-at-rest features use stdlib primitives correctly
  (AES-GCM, IV/AAD handling, hard delete) behind a thin key-custody SPI. KMS integration and node-root threat models are
  platform concerns.
- **Docker is the primary distribution.** The Dockerfile is fully self-contained (no host tooling assumed) and image
  references always carry the `docker.io/` registry prefix.
- **Vendored data (pattern bundles etc.) is imported, not fetched.** A one-time import script pinned to a commit SHA,
  plus the upstream LICENSE verbatim routed through `generateThirdPartyNotices`. No submodules, no build-time fetches.

## Repository layout

| Module              | Purpose                                                                                                     |
| ------------------- | ----------------------------------------------------------------------------------------------------------- |
| `fogwall-core`      | Shared library: filter chain, JGit hooks, push store, provider model, approval abstraction                  |
| `fogwall-server`    | Standalone proxy-only server (`FogwallJettyApplication`) — no dashboard, no Spring                          |
| `fogwall-dashboard` | Dashboard + REST API (`FogwallDashboardApplication`) — Spring MVC, approval UI, depends on `fogwall-server` |

## Architecture

Two proxy modes, both configurable per-provider:

- **Server mode** (`/server/<provider>/<owner>/<repo>.git`; formerly "store-and-forward", still served at the deprecated
  `/push/…` alias) — JGit ReceivePack receives the push locally, runs a pre-receive chain of validation hooks, then
  forwards upstream using the client's credentials. `ServerReceivePackFactory` assembles the current hook roster.
- **Transparent proxy** (`/proxy/<provider>/<owner>/<repo>.git`) — Jetty's `ProxyServlet` forwards the request; a
  servlet filter chain parses and inspects the pack data before it reaches the upstream. `FogwallServletRegistrar`
  assembles the current filter chain.

The main behavioural difference between the modes is streaming: server mode can send progress to the client live via
JGit hooks, while the transparent proxy must buffer everything and send one response at the end of the filter chain (see
the streaming constraint below).

Server mode also has an SSH transport (`fogwall-server`'s MINA SSHD-based `SshGitServer` / `SshGitReceiveCommand` /
`SshGitUploadCommand`) alongside the HTTP one — it's the same mode, delegating to the same `ServerReceivePackFactory`
hook chain, just reached over `git-receive-pack`/`git-upload-pack` SSH commands instead of HTTP, with upstream auth via
the client's forwarded SSH agent. Not a third proxy mode; a second transport for the same one.

## Client output — streaming constraint

**Server mode** uses JGit `ReceivePack` pre-receive hooks. Each hook can call `rp.sendMessage()` at any point and the
message streams to the git client immediately as a sideband progress packet (`remote: …`). This is how per-step progress
lines are sent live.

**Transparent proxy** uses servlet filters. The HTTP response is a single buffered reply — there is no mechanism to
stream partial output mid-filter-chain. Validation filters must _accumulate_ their result and return;
`ValidationSummaryFilter` and `PushFinalizerFilter` collect everything and write one response at the end using
`sendGitError`.

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
- Gradle caches test results — pass `--rerun` when adding or changing tests, e.g.
  `./gradlew :fogwall-core:test --rerun`. Locally, run targeted unit tests plus a compile; leave coverage gates and e2e
  to CI — it is the arbiter and runs without cache.

## Git workflow

Branches and commits:

- Always start a new feature branch from an up-to-date `origin/main` — `git fetch origin main` first, branch from
  `origin/main`, not a possibly-stale local `main`.
- Always squash related commits into one before pushing — use `git reset --soft`, not `git rebase -i` (requires TTY).
  Compute the squash base against a freshly fetched `origin/main`, never the local `main` ref — it goes stale in
  multi-worktree setups and silently pulls already-merged commits back into the squash. Exception: never squash across
  commits that differ by author or model trailer; that multi-commit structure is the provenance record.
- When a PR branch falls behind, rebase onto `origin/main` and force-push. Never `git merge origin/main` into the branch
  — the repo only allows merge-commit merges, so an in-branch merge commit produces two merge commits on the final
  merge.
- Rebase and squash locally, never through the GitHub UI ("Update branch", web rebase, web edits). Commits must carry
  the developer's signature, not GitHub's key.
- Never use `git add -A` — the working tree may hold sensitive or scratch files that don't belong in version control.
  Stage paths explicitly.
- Never disable commit signing or hooks (`--no-verify`) unless explicitly asked. The pre-commit hook runs formatting,
  linting, and PMD; it catches many code smells and is the signal that the harness is going off the rails.
- Never add `[ci skip]` to commits unless explicitly asked.

Commit messages:

- Plain language: lead with what a developer saw go wrong, then the cause, then the fix. Dense graph shorthand is not
  documentation. The same applies to PR bodies.
- Always include a `Co-Authored-By` trailer crediting the Claude model that did the work (e.g.
  `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>` — use the current model's name, not this example, if it
  differs). This is a project transparency requirement. Never append a `Claude-Session:` trailer — session URLs are
  garbage-collected within weeks.
- Always include `closes #N` / `resolves #N` when addressing a GitHub issue — but grep for the actual implementation
  first. A docs-only PR once closed #83 and an unrelated commit closed #107.

Merging:

- `gh pr merge` is always `--merge`; squash and rebase merges are disabled on the repo.
- After a force-push, check `autoMergeRequest` and re-arm with `gh pr merge N --merge --auto` if it dropped. A PR
  sitting at `mergeStateStatus: UNKNOWN` with green checks is stalled behind main, not merged — rebase it.

## Issue and PR hygiene

This is a public repository.

- Update an issue or PR body when state changes; don't litter threads with progress comments. Only the latest state
  matters, and edit history is there if anyone ever needs it.
- No "Generated with Claude Code" footers on issues or PRs.
- Only use labels that already exist; never create new ones. Don't prefix an issue title with a word that is already
  applied as a label.
- Say "point N", not "#N", for numbered sub-items inside an issue — GitHub linkifies `#N`.
- Never open issues on external repos on the maintainer's behalf; note the upstream tracker for manual follow-up.

## Code conventions

- **Comments describe the code, not its history.** No issue references in comments or javadoc for fogwall's own features
  or bugs (`/** MyCoolFeature (#123) … */`), and no version numbers ("deferred to 1.x"). Both go stale the moment a
  follow-up lands; that context belongs in release notes and issues. The one exception is a workaround for an external
  project, e.g. `// workaround for https://github.com/foo/bar/issues/123`.
- **YAML config is user-facing.** Its audience is admins, operators, policy authors, and developers (including plugin
  and extension authors). Tuning knobs for JGit, Jetty, and other internals that only matter in specific deployment
  scenarios do not go through the YAML/Gestalt loader; expose them as documented environment variables an operator
  _could_ set but isn't expected to know about (precedent: the server-tuned JGit pack-window cache).
- **`*Settings` POJOs default fully inert** — `enabled = false`, empty lists, no limits. Real defaults live only in the
  shipped YAML files (`SecretScanSettings` is the precedent).
- **One global config key before per-provider or per-entity variants**, until someone actually asks for the granularity.

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

## Dependency injection conventions

- **Constructor injection only, everywhere** — `private final` fields via Lombok `@RequiredArgsConstructor`, or an
  explicit constructor when a param needs `@Qualifier` or `Optional<T>`. Field `@Autowired` and `@Resource` are banned;
  `@Autowired(required = false)` especially so.
- **A genuinely optional collaborator is an explicit `Optional<T>` constructor param** (deployment-conditional beans
  like the storage-backend split) **or a no-op implementation** — never a nullable field with use-site `!= null` guards.
  A required control dependency is `Objects.requireNonNull`'d with a message naming the consequence.
- **Conditionality lives in the composition roots only** — `JettyConfigurationBuilder` (server) and
  `FogwallDashboardApplication`'s `registerSingleton` block (dashboard). Everything they hand out is non-null; anything
  conditional gets resolved inside them. No `BeanFactoryPostProcessor`, no conditional bean registration for beans that
  always exist.

## Testing conventions

- Always use JUnit assertions (`org.junit.jupiter.api.Assertions.*`) — not manual `if`/`throw` checks.
- E2e tests use Testcontainers (Gitea) + `JettyProxyFixture`. Credentials in the clone URL are forwarded to upstream
  Gitea, so they must be valid Gitea credentials. Use `GiteaContainer.ADMIN_USER`/`ADMIN_PASSWORD` or create test users
  via `createTestUser()` / `addTestUserAsCollaborator()` — never invent fake usernames that won't authenticate upstream.
- When a build or test fails or hangs, run it unfiltered into a file and grep the file. Never pipe through `grep FAILED`
  / `tail` — a hung test never writes `build/test-results`, so the console stream is the only record.
- A feature isn't done until it has been pushed or fetched through the running proxy end to end. Unit tests plus a clean
  compile have hidden unwired filter chains before.
- The HTTP Basic username is meaningless for identity — the token drives resolution (only Bitbucket differs). Test
  scripts use `me`; never switch it to a real handle or document it as an identity input.

## Build and CI

- Transitive CVE pins go in the root `build.gradle` subprojects `eachDependency` table with `because 'CVE fix: GHSA-…'`.
  Never declare the vulnerable artifact as a dependency, not even as a platform/BOM. The `buildscript` force block
  covers only the plugin classpath: it can't fix an image-scan finding, and it is the only thing that clears
  plugin-classpath Dependabot alerts.
- CI binaries (grype, cosign…) are installed by downloading the release tarball plus `checksums.txt` and verifying with
  `sha256sum -c`. Never `curl | sh`, never the tool's own install script, never a hardcoded hash.
- Add workflow steps to the existing job that already did the prerequisite work; don't create new jobs. Scope
  `secrets.*` to the step's `env:`, not the job's.
- Dockerfile digest pins must be multi-arch _index_ digests, verified with `skopeo inspect --raw … | sha256sum`. PR CI
  builds amd64 only; arm64 breakage only surfaces post-merge.
- `compose.sh` is for the main stack only. The perf harness uses bare `docker compose -f perf/docker-compose.yml` per
  `perf/README.md`.

## Configuration

Refer to [docs/CONFIGURATION.md](docs/CONFIGURATION.md) for detailed docs on YAML config structure, environment variable
overrides, and provider-specific settings.

## Documentation upkeep

When a PR introduces or materially changes a user-facing feature, check whether it needs a docs update as part of that
PR — don't let doc drift accumulate to be reconciled later in a big batch:

- [docs/USER_GUIDE.md](docs/USER_GUIDE.md) — anything a developer pushing through the proxy would need to know
- [docs/ADMIN_GUIDE.md](docs/ADMIN_GUIDE.md) — anything an operator configuring/running fogwall would need to know
- [docs/CONFIGURATION.md](docs/CONFIGURATION.md) — any new or changed config key
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — any new abstraction, pipeline step, or design rationale worth
  explaining for contributors

Not every change needs all four — use judgment — but check rather than skip the check.

## Roadmap & architecture

There are gists linked in the root README. Only look up these details as necessary for planning refactors or
understanding design rationale. The code itself is the source of truth for how the system works ultimately.
