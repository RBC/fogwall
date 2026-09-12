---
target_identity: "file:/home/tom/repos/fogwall/fogwall-dashboard/frontend/src/pages"
timestamp: 2026-09-12T20-07-12Z
slug: fogwall-dashboard-frontend-src-pages
---

# Design Critique — fogwall dashboard (all 16 pages)

Method: dual-agent (16 A/B pairs, isolated; plus one role-differential pass against admin/dev/reviewer/observer
sessions). Evidence: 32 desktop+mobile screenshots and 20 role screenshots captured against the Playwright fixture
database.

## Design Health Score — 64% dashboard mean (~25.6/40)

| Page          | Score       | Weakest heuristic       |
| ------------- | ----------- | ----------------------- |
| Setup         | 27/32 (84%) | best on the dashboard   |
| Mirror cache  | 31/40 (78%) | Flexibility (2)         |
| Providers     | 24/32 (75%) | Match to real world (2) |
| Pushes (list) | 30/40 (75%) | Help (1)                |
| Legal         | 28/40 (70%) | Flexibility (2)         |
| Overview      | 25/36 (69%) | Error prevention (2)    |
| Push detail   | 27/40 (68%) | Error recovery (1)      |
| Contributions | 20/32 (63%) | Help (1)                |
| User detail   | 25/40 (63%) | Help (1)                |
| Profile       | 24/40 (60%) | Flexibility (1)         |
| Issues        | 23/40 (58%) | Status visibility (2)   |
| Groups        | 22/40 (55%) | Help (1)                |
| Repos         | 21/40 (53%) | Flexibility (1)         |
| Operations    | 21/40 (53%) | Help (1)                |
| Push diff     | 19/36 (53%) | Flexibility (1)         |
| Users         | 20/40 (50%) | Error prevention (1)    |

Pages a developer reads score highest; pages an operator acts through score lowest.

## Design specificity — split

Authored for fogwall: push detail (timeline, 14 named validation steps, identity badge, status-dependent re-push
guidance), mirror cache (copy frames cache health as correctness, not disk space), the status vocabulary, hand-drawn
icon paths.

Category-interchangeable: Users, Groups, Repos — competent implementations of a generic search/table/modal admin
pattern. What is edited is fogwall-specific; how it is edited is not.

Deterministic scan: 1 finding in 16 pages (`side-tab`, `border-l-4`, Setup.tsx:190). Rendered evidence contradicts it —
the border marks proportional informational callouts. False positive in context, though it does violate the project's
own hairline rule.

## Priority issues

[P0] One confirm() guards the entire access-control surface. Verified: 0 in Users/Groups/Repos/Profile/PushList, 1 in
UserDetail (delete user, line 342). Granting ADMIN/SELF_CERTIFY, removing an SCM identity, deleting a group or
permission rule, and bulk-approving pending pushes all execute on one click, most with no success confirmation. Fix once
in the shared admin skeleton: confirmation naming the consequence, plus a success toast.

[P0] Observer sees a complete attestation form with dead controls. Signed in as observer, a PENDING push renders
"Reviewing as observer", both attestation checkboxes with policy links, ticket field, reason textarea, then greyed
Approve/Reject with no explanation. Reads as broken rather than read-only. Replace with a one-line statement of
authority when the viewer cannot decide.

[P1] The product's vocabulary is undefined in the product. Help scored lowest dashboard-wide (mean ~1.5/4; scored 1 on
seven pages). Users.tsx and Groups.tsx contain one title= attribute each. Undefined: 8 grant types, DENIED vs REJECTED,
LITERAL/GLOB/REGEX, "locked (config)", REFUSED/TIMEOUT/RESET, and "Contributions" after the rename.

[P1] Overview answers nobody's question. Four tiles render "—" with "Metrics not yet available" across the top quarter
of the first screen; every role gets the same generic quick links. An approver cannot see that pushes await them.

[P1] Repos hides rule evaluation order. ruleOrder is the least prominent field; the DENY-override guarantee lives in
small grey text inside the add form. An operator cannot answer "which rule fires first?" without testing each rule.

[P2] Push diff is the least-integrated surface. diff2html supplies its own palette, spacing and row structure; no file
list, search, collapse, or progress for a large diff; toggle buttons reinvent button styling.

[P2] Mobile truncation is systemic; one case is a real bug. Headings, table columns and button labels clip at 390px
across most pages (follows from the desktop-only stance). Distinct defect: Providers wraps SSH endpoint URLs
character-by-character into a vertical column.

[P2] No keyboard affordances. Flexibility scored 1-2 on fifteen of sixteen pages (mean ~1.7). No approve/reject
shortcut, no search focus key, no saved filters.

## What is working

- The design system holds across 16 pages with near-zero drift; Consistency scored 3-4 nearly everywhere.
- Push detail: canonical timeline, all 14 steps visible without tabbing, failures expand inline, identity resolution in
  the header.
- Permission gating is correct — no leaks found. Admin nav gated, self-approval blocked, admin override labelled
  break-glass, observers cannot act.
- Setup front-loads the credential-helper trap that most setup docs omit.
- The rule-test tool on Repos validates rule logic before commit.

## Persona red flags

Approver: scans 14 steps the timeline already summarised; unguided required reason field produces "looks good" audit
records; bulk approve has no undo; admin override easy to arm unnoticed. Platform operator: cannot see who holds ADMIN
without opening each user; cannot see rule precedence; Operations requires running a check to learn health, then
recalling error-code meanings mid-incident. Blocked developer: Profile's Emails tab implies adding an email helps, but
emails never grant access; Permissions tab is a dead end with no request path. Auditor: cannot answer "who could approve
on date X" — no role column, no identity-change timestamps, no grant filter, no export.

## Minor observations

- toLocaleString() throughout; audit records read differently by browser locale.
- SCM usernames render in body text, violating the project's mono-for-machine-strings rule.
- Contributions packs nine data points per row with no visual grouping.
- Mirror cache's correctness-critical "state is per-pod" note is muted grey body text.
- Groups' grant dropdown offers eight options with no grouping or explanation.

## Questions to consider

1. If the timeline says "Validation passed (14 checks)", why does the reviewer read the list?
2. Is the Users list for identity management or permission auditing? It attempts both; roles are invisible while push
   activity gets a column.
3. What would make an observer's dashboard feel designed rather than disabled?
