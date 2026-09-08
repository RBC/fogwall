# Commit validation

Per-commit checks (identity, email policy, message content, trailer policy) apply to both server mode and transparent
proxy modes.

```yaml
commit:
  # Committer email policy — the committer is the employee who ran git commit or git rebase.
  # This is the primary corporate control: enforce that your staff use their work identity.
  # Rebased commits from external contributors still pass as long as the committer email is valid.
  # Also applied to the tagger of an annotated tag push: git fills the tagger line from the same
  # user.email as the committer line, so there is no separate tagger policy to configure.
  committer:
    email:
      # Unified allow/block rules — see "Email policy rules" below.
      rules:
        - { action: allow, field: domain, match: regex, value: "corp\\.example\\.com$" }
        - { action: block, field: local, match: regex, value: "^(noreply|no-reply|bot|nobody)$" }

  # Author email policy — the author is whoever originally wrote the commit.
  # Configure this only if you want to disallow rebasing external contributors' commits.
  # When set, any commit whose author email is not permitted is blocked — developers must open
  # PRs from the original fork rather than rebasing upstream changes. Omit to allow external
  # author emails (the most common setup).
  author:
    email:
      rules:
        - { action: allow, field: domain, match: regex, value: "corp\\.example\\.com$" }

  message:
    block:
      literals:
        - "WIP"
        - "DO NOT MERGE"
      patterns:
        - '(?i)(password|secret|token)\s*[=:]\s*\S+'

  # Commit-trailer policy — DCO Signed-off-by and Co-authored-by. Both controls are enforce-or-off
  # and apply to both transports. See "Trailer policy" below.
  trailers:
    signed-off-by:
      require: false # true = every commit must carry a Signed-off-by trailer (DCO)
      require-author-match: false # true = the Signed-off-by email must equal the commit author's
    co-authored-by:
      policy: off # off | ban | allowlist | require
      # Under `allowlist`, each co-author email is checked against these rules (same shape as above):
      email:
        rules:
          - { action: allow, field: domain, match: regex, value: "corp\\.example\\.com$" }
          - { action: allow, field: address, match: literal, value: "noreply@anthropic.com" }
```

## Email policy rules

`author.email`, `committer.email`, and `co-authored-by.email` all share one shape: an ordered list of `rules`. Each rule
is `{ action, field, match, value }`:

| Key      | Values                           | Meaning                                                                                          |
| -------- | -------------------------------- | ------------------------------------------------------------------------------------------------ |
| `action` | `allow` \| `block`               | Whether a match permits or rejects the email.                                                    |
| `field`  | `domain` \| `local` \| `address` | Which part of the address to test (`local` is before `@`, `address` is the full `local@domain`). |
| `match`  | `literal` \| `regex`             | `literal` is case-insensitive exact equality; `regex` uses find-semantics. Default `regex`.      |
| `value`  | string                           | The literal string or regex source.                                                              |

Evaluation: **a block match always rejects**; then, **if any allow rule is present, the email must match at least one**
to pass. With no allow rule, anything not blocked is permitted. This lets you express, for example, "allow `@corp.com`
and the single bot address `noreply@anthropic.com`, but block the `svc-` service accounts":

```yaml
rules:
  - { action: allow, field: domain, match: regex, value: "corp\\.example\\.com$" }
  - { action: allow, field: address, match: literal, value: "noreply@anthropic.com" }
  - { action: block, field: local, match: regex, value: "^svc-" }
```

> **Deprecated aliases.** The older `email.domain.allow` and `email.local.block` single-regex keys are still accepted
> for one minor release and are folded into equivalent `allow domain regex` / `block local regex` rules at startup (a
> deprecation warning is logged). Migrate to the `rules` list — it can express blocks on domains, allows on local-parts,
> full-address matching, and literals, none of which the old keys could.

## Trailer policy

Two independent, enforce-or-off controls over commit-message trailers (both apply to server mode and transparent proxy):

- **`signed-off-by.require`** — reject any commit lacking a `Signed-off-by:` trailer (Developer Certificate of Origin).
  With **`require-author-match: true`**, at least one `Signed-off-by` email must equal the commit's own author email
  (the DCO "sign off your own work" rule).
- **`co-authored-by.policy`** — one of:
  - `off` (default) — no restriction.
  - `ban` — reject any commit that carries a `Co-authored-by:` trailer (e.g. legal teams requiring a single attributable
    author per commit).
  - `require` — reject any commit that has no `Co-authored-by:` trailer.
  - `allowlist` — reject a `Co-authored-by` whose email is not permitted by `co-authored-by.email.rules` (the same
    email-policy shape above), e.g. to permit only approved internal bots.

Captured `Signed-off-by` and `Co-authored-by` trailers are also stored on the push record and shown per-commit in the
dashboard push detail, independent of any policy.
