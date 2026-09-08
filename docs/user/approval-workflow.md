# Understanding the approval workflow

What happens after validation depends on how the administrator has configured the approval mode:

## Auto-approve (`approval-mode: auto`)

Clean pushes (no validation failures) are immediately approved and forwarded. You see output like the example above — no
human reviewer is needed. This is the typical setting for solo developers or teams that use validation as a guardrail
without a manual review step.

## Review required (`approval-mode: ui`)

After validation passes, the push enters a **PENDING** state and waits for a reviewer to approve it in the dashboard.
You will see:

```text
remote: 🔗  View push record: http://fogwall.corp.example.com/dashboard/push/4d6196fb-...
remote: ⚠  Push requires review. Waiting for approval...
remote: 🔑  Push ID: 4d6196fb-4cc3-47d1-ac6d-17fbcc5f71d3
remote:    Review at: http://fogwall.corp.example.com/dashboard/push/4d6196fb-...
remote: Awaiting review... (5s elapsed, ~1794s remaining)
remote: .
remote: Awaiting review... (10s elapsed, ~1789s remaining)
```

The push command stays open, printing keepalive dots while it waits. Once a reviewer approves in the dashboard, the
proxy forwards the push and the command completes:

```text
remote: ✅  Push approved by reviewer
remote: Updating references: 100% (1/1)
remote: 🔗  Forwarding to https://github.com/myorg/myrepo.git...
remote:   Pushing 1 ref(s) to upstream...
remote:   ✅  refs/heads/my-feature -> OK
remote: ✅  Forwarding complete
To http://fogwall.corp.example.com/server/github.com/myorg/myrepo.git
 * [new branch]      my-feature -> my-feature
```

If no approval comes, your git client will eventually time out. You can re-run the push — it will resume waiting for
approval on the existing push record rather than creating a new one.

## Attestation questions

The administrator may configure attestation questions that you must answer before a push is approved. These appear in
the dashboard push record view, not in the terminal. A reviewer (or yourself, if you have `SELF_CERTIFY` permission for
the repo) answers them as part of the approval step. A question may carry one or more linked references (e.g. a link to
the internal policy the attestation is checking against) — these render as clickable links alongside the question so
reviewers can check the source policy before attesting.
