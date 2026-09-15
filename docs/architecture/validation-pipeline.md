# Validation pipeline

Both proxy modes run equivalent validation logic. The filter (transparent proxy) and hook (server mode) names differ,
but they check the same things, grouped into the same **lifecycle stages**. Steps run in stage order; within a stage,
order is a fixed, core-owned position — there is no numeric order to tune, and the mandatory stages cannot be extended
from outside the core.

The stages, in order of precedence: `mandatory pre` → `custom pre` → `mandatory processing` → `custom post` →
`mandatory post`. The `custom` stages are the extension surface for plugins; the built-in pipeline occupies the
mandatory stages only.

## Push

| Stage                | What runs                                                                                                                                                                                                                                     |
| -------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| mandatory pre        | parse the request; short-circuit an already-approved re-push; enrich commit data                                                                                                                                                              |
| mandatory processing | URL allow/deny rules; user identity + push permission; author attribution; non-empty push; hidden-commit guard; author email, commit message, and content-pattern (WARN) checks; binary-blob detection; diff scan; GPG signature; secret scan |
| mandatory post       | validation summary; push-record audit; forwarding / finalizer                                                                                                                                                                                 |

## Fetch

Most validation steps are push-specific, so a fetch runs a much shorter chain — but both modes gate it on the URL
allow/deny rule:

- **Transparent proxy**: the filter chain runs, but only parse, the URL allow/deny rule (recording a fetch-store entry),
  and the finalizer act on a fetch.
- **Server mode**: a short servlet filter chain — parse, then the URL allow/deny rule — runs in front of JGit's
  upload-pack, which serves the clone/fetch from the local mirror, or refuses it when fetch serving is turned off for
  the provider.

The URL rule is the only fogwall gate on a fetch. Neither mode resolves identity, checks push permission, or inspects
payload — those are push-only, and the client's credentials are relayed to the upstream, which authenticates the fetch
itself. The approval lifecycle, the validation summary, and push-record persistence do not apply either: there is no
push payload to hold for review.

## Terminating and accumulating steps

Each step declares whether recording an issue ends the chain. Access-control steps (URL rule, push permission) and
structural guards (empty branch, hidden commits) are **terminating** — the push is refused at once, so no later stage
inspects content the pusher may not be allowed to write. Content-validation steps **accumulate** — every finding is
collected and reported together, so a developer sees all the problems in a single push. Enabling `server.fail-fast`
makes the accumulating steps stop at the first finding too.

Each step records a `PushStep` in the push record with a `StepStatus` of `PASS`, `WARN`, `FAIL`, `BLOCKED`, or
`SKIPPED`. `WARN` is a first-class outcome, not a lesser form of `FAIL` — a WARN step never blocks the push, it only
surfaces a finding on the push record for the reviewer's attention. The content-pattern (PII/national-ID) filters are
WARN-only by design on this pipeline, where a reviewer sees the finding; the SCM API surface runs the same bundles as a
blocking check, having no reviewer to show a warning to. Author attribution (commit-email attribution) can also run in
`warn` mode via `commit.attribution-policy`.
