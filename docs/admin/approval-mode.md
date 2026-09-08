# Approval mode

```yaml
server:
  approval-mode: auto # auto | ui
```

| Mode   | Behaviour                                                                                                                                                                                            |
| ------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `auto` | Clean pushes are immediately approved and forwarded after validation. No reviewer needed. Good for teams that use validation as a guardrail without a manual review step, and for solo contributors. |
| `ui`   | Every push enters `PENDING` state and waits for a reviewer to approve or reject in the dashboard. The `git push` command stays open until a decision is made.                                        |

`SELF_CERTIFY` permission interacts with `ui` mode: users with the capability and per-repo entitlement can self-review
their own push in the dashboard. The review step still happens — they attest to and record their own approval. This
signals to operators and the audit log that the pusher has reviewed and accepted responsibility for the changes. Other
users' pushes still require a peer reviewer.

The dashboard module (`fogwall-dashboard`) always uses `ui` mode. The standalone server module defaults to `auto`.
