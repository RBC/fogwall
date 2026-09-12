# Validation pipeline

Both modes run equivalent validation logic. The filter/hook names differ, but they check the same things in the same
order.

| Order   | What it checks                                                                        |
| ------- | ------------------------------------------------------------------------------------- |
| 50–199  | URL allow/deny rules (config + DB-sourced)                                            |
| 150     | User identity — developer must have a proxy account and push permission for this repo |
| 160     | Author attribution — git commit author must match the authenticated proxy user        |
| 210     | Non-empty push — at least one new commit                                              |
| 220     | Hidden commits — pack must not contain commits outside the declared push range        |
| 250–260 | Author email and commit message patterns (allow/block regex)                          |
| 265     | Content pattern scan — commit messages (national ID/PII bundles) — **WARN-only**      |
| 290     | Binary blob detection — magic-byte signature sniffing, with MIME-type allow/deny      |
| 300     | Diff content scan (blocked literals and patterns)                                     |
| 320     | GPG commit signature validation                                                       |
| 340     | Secret scanning (gitleaks)                                                            |
| 345     | Content pattern scan — diff (national ID/PII bundles) — **WARN-only**                 |

Each step records a `PushStep` in the push record with a `StepStatus` of `PASS`, `WARN`, `FAIL`, `BLOCKED`, or
`SKIPPED`. `WARN` is a first-class outcome, not a lesser form of `FAIL` — a WARN step never blocks the push, it only
surfaces a finding on the push record for the reviewer's attention. The content-pattern (PII/national-ID) filters are
WARN-only by design on this pipeline, where a reviewer sees the finding; the SCM API surface runs the same bundles as a
blocking check, having no reviewer to show a warning to. `CommitAttributionPolicyFilter` (order 160, commit-email
attribution) can also run in `warn` mode via `commit.attribution-policy`. All steps always run (fail-fast is
configurable); issues accumulate and are reported together.
