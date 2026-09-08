# Conceptual model: three independent layers

Before diving into configuration details, it helps to understand that access control in fogwall is three orthogonal
layers that all must pass before a push is forwarded:

```text
1. Access rules       rules.allow / rules.deny
   "Is this repo even on the proxy's allowed list?"
         ↓
2. User permissions   permissions:
   "Is this user allowed to push to this specific repo?"
         ↓
3. Commit validation  commit:
   "Does the content of this push comply with policy?"
```

A push fails at the first layer that rejects it. A common misconfiguration is to add a repo to `rules.allow` but forget
to add a `permissions` entry for the user — or vice versa. Both are required.

**Access rules** are site-wide policy: they determine what the proxy will route at all, independently of who is pushing.
Think of them as a firewall rule list.

**User permissions** are per-user grants scoped to a provider and path. They determine whether a particular
authenticated user is permitted to push to (or review) a particular repository.

**Commit validation** runs against the push content: author/committer email policy, commit messages, commit-trailer
policy (DCO `Signed-off-by`, `Co-authored-by`), diff scanning, secret scanning. These apply to everyone regardless of
permissions.
