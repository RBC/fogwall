# Reviewing a push

If you have been asked to review a push, or you are an administrator, log in to the dashboard and open the **Pushes**
page. Pushes awaiting review have status **PENDING**.

## Push record states

| State       | Meaning                                                        |
| ----------- | -------------------------------------------------------------- |
| `RECEIVED`  | Push has arrived and is being processed                        |
| `PENDING`   | Validation passed; awaiting a reviewer's decision              |
| `APPROVED`  | Approved by a reviewer (or self-certified) — will be forwarded |
| `FORWARDED` | Successfully sent to the upstream SCM                          |
| `REJECTED`  | Reviewer declined the push                                     |
| `BLOCKED`   | Validation failed — push will not be forwarded                 |
| `CANCELED`  | Canceled by the pusher or an administrator                     |

## Approving or rejecting

Open the push record to see the full diff, commit list, and validation results. You can:

- **Approve** — forwards the push to the upstream. If attestation questions are configured, you must answer them before
  approving.
- **Reject** — blocks the push. The reason field is optional but recommended — it is shown to the pusher in the
  dashboard and helps them understand what to fix.

The reason field is recorded in the audit log regardless of whether it is shown to the pusher.

## Self-certification

If you have `SELF_CERTIFY` permission for the repository, you can approve your own pushes from the push record view. The
approval is recorded in the audit log with a self-certification flag, distinguishing it from peer review. Attestation
questions still apply.

## Who can review

By default any authenticated user can review any push they did not push themselves. If your administrator has set
`server.require-review-permission: true`, you need an explicit `REVIEW` permission entry for the repository to approve
or reject. Contact your administrator if you receive a 403 trying to approve a push.

An admin reviewing another user's push may tick **admin override** to approve on admin authority when the assigned
reviewer is unavailable, bypassing the review-permission check. This is a break-glass action, recorded in the audit log.
It never applies to an admin's own push — approving your own push always requires self-certification, whether or not you
are an admin.
