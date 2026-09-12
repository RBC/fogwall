# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Four audiences share one dashboard, and all four confirmed as real. They are not four different kinds of person: the
same individual is often a developer, an admin and the auditor of their own system, so the audiences describe what
someone is doing at a moment, not who they are.

- **Developers whose push was blocked** — arrive from a terminal error to see why, and to unblock themselves. Their loop
  is otherwise entirely git CLI; the dashboard is a destination they are sent to, not one they live in. **They are
  impatient, and correctly so** — they are interrupted mid-task and want the answer and the fix, not a tour. Time spent
  in the UI is time taken from the work the push was for.
- **Approvers / reviewers** — open a blocked push, read its diff and per-step results, approve or reject. The approval
  queue is the daily job, and it is repetitive: the same loop many times in a sitting.
- **Platform operators / admins** — run fogwall itself: providers, users, groups, permissions, mirror cache, operations.
  Configuration and fleet health, not individual pushes. **Frequently developers themselves**, with the same
  expectations of speed and keyboard operability; an admin surface here is not a place where slowness is excused.
- **Auditors / compliance reviewers** — read the record after the fact: who approved what, on what evidence. Read-only
  and periodic. Their constraint is absolute: they must be able to see everything and perturb nothing.

The navigation answers three questions, and a new surface belongs to whichever one it serves:

- **What is happening** — Pushes, Contributions, Issues.
- **Who can do what** — Users, Groups, Repos and their permission rules.
- **Is it healthy** — Operations, Mirror cache, Providers.

## Product Purpose

fogwall is a git-aware gateway between developers and upstream SCM hosts (GitHub, GitLab, Bitbucket, Forgejo/Gitea).
Every push — any branch, any tag — is policy-checked, content-scanned, identity-verified, and gated behind review before
it reaches upstream; every fetch is audited. The dashboard is the human surface over that: the approval queue, the audit
record, and the operator's configuration view.

Success is a decision that can be explained after the fact — who, which rule, what evidence — not merely enforced in the
moment.

## Positioning

A single policy-enforcement chokepoint in front of heterogeneous SCM platforms, rather than bespoke compliance tooling
bolted onto each one. Because it speaks the git protocol itself (JGit) rather than wrapping provider APIs, it answers
commit, ref, reachability and content questions from a local mirror, and works the same across every upstream it fronts.
Beyond push validation it is positioned as a general gateway/integration layer for an enterprise's software estate — M&A
and subsidiary SCM integration without cross-boundary network access, and inner-source enablement.

## Operating Context

- Self-hosted by the operating organization: container image (`ghcr.io/rbc/fogwall`) or Helm chart; a dashboard+API
  image and a standalone proxy-only image are alternatives, not a shared-database topology.
- Inline on every push, clone and fetch at enterprise volume — the default path is latency-sensitive and expensive
  checks are opt-in.
- Developer feedback streams to the terminal during the push, over HTTP(S) and SSH. The dashboard is consulted when a
  push is blocked, when a review is pending, or when the record is being examined.
- Two proxy modes (transparent proxy, and server mode with HTTP and SSH transports), configured per provider.
- Persistence is JDBC (H2, Postgres, MySQL, MariaDB) or MongoDB. Stored push history is a live audit log.

## Capabilities and Constraints

Enforcement: diff and commit-message scanning (custom patterns plus built-in PII bundles), secret scanning with findings
redacted at rest, binary-blob detection, GPG signature verification, commit attribution policy, one permission model
across every upstream, and proxying of the SCM CLIs (`gh`, `glab`, `tea`, `fj`) for outbound PR/MR and comment content.

Terminology the UI is built on, and which future work should not rename casually: **Pushes** (with statuses PENDING,
APPROVED, FORWARDED, REJECTED, CANCELED, RECEIVED, ERROR), **Steps** (per-validation results: PASS, WARN, FAIL, BLOCKED,
SKIPPED), **Contributions** (the SCM API proxy's audit records), **Providers**, **Repos**, **Users**, **Groups**,
**Mirror cache**, **Operations**.

Identity is always SCM identity — a token or SSH key resolved to a provider login. Commit email is client-controllable
metadata and never grants access.

Constraints on change: config keys, SQL schema, Mongo collections and REST response shapes are past 1.0 and carry
backwards-compatibility obligations. Safety is the default and convenience is an explicit, visible opt-in, never a
silent one.

Stack for the UI is fixed by the existing codebase: React 19, React Router, Tailwind CSS 4, Vite, TypeScript; Playwright
for end-to-end tests. Dark mode already exists as a user-toggled preference and is a supported mode, not a variant to
drop.

## Brand Commitments

No binding corporate brand. fogwall is a public, Apache-2.0 open-source project and the UI should read as its own
product rather than as any one company's internal tool. The existing logo and favicon (`frontend/public/favicon.svg`,
`frontend/src/assets/logos`) stay; nothing else about the identity is fixed.

## Evidence on Hand

- Running system with real push history in the deployed database, and a local dev stack (fogwall + Gitea) for
  reproducing flows.
- Product documentation at <https://rbc.github.io/fogwall/> (user, admin, configuration, architecture guides in
  `docs/`).
- A demo recording of a failing-then-fixed push (`demos/demo-push-fail-then-fix.gif`).
- Playwright fixtures under `fogwall-dashboard/frontend/tests/fixtures` for seeded UI states.

No customer testimonials, adoption numbers, benchmarks, pricing, or named references exist. Future work must not
fabricate them.

## Product Principles

1. **A decision that can't be explained afterwards isn't done.** Every blocked, approved, forwarded or overridden
   outcome carries who, which rule, and what evidence.
2. **Safe by default; convenient by explicit opt-in.** Self-certification, admin override and auto-approve are visible
   choices an operator makes, never silent defaults.
3. **The inline path stays cheap.** fogwall sits on every push at enterprise scale; expensive inspection is opt-in and
   scope-bounded.
4. **Parity across peers is a requirement, not a nicety.** Both proxy modes, both database families, all providers
   behave the same way, or the gap is documented and deliberate.
5. **Optional and composable over interdependent.** An organization runs only the pieces it needs; a new capability must
   not raise the baseline for someone not using it.
6. **This audience's time is the scarce resource.** The users are impatient developers, and a control that costs a click
   on the common path will be routed around or dismissed reflexively — which buys no safety and spends real time. Guard
   rails are earned by irreversibility, not applied by category: what can be undone is applied immediately and offered
   back, and only what genuinely cannot be undone interrupts. Safety and speed are not opposed here; treating every
   action as dangerous is what makes the dangerous ones invisible.

## Accessibility & Inclusion

WCAG 2.2 AA is a required standard, not an aspiration. Contrast, full keyboard operability, visible focus, and
screen-reader semantics are non-negotiable in both light and dark modes.
