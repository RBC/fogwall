# Diff scan

Push-level check applied once per push against the aggregate diff (all commits combined). Only added lines (`+`) are
scanned — deletions and context lines are ignored.

```yaml
diff-scan:
  block:
    - '(?i)https?://[a-z0-9.-]*\.corp\.example\.com\b'
    - { match: literal, value: "internal.corp.example.com" }
```

## Content matchers

`diff-scan.block`, `commit.message.block`, and `scm-api.block` share one shape: an ordered list of matchers, each either
a bare string or a `{ match, value }` mapping. Every match blocks — content matchers have no allow action and no field,
unlike the [email policy matchers](commit-validation.md#email-policy-matchers).

| Key     | Values               | Meaning                                                                      |
| ------- | -------------------- | ---------------------------------------------------------------------------- |
| `match` | `literal` \| `regex` | `literal` is a substring test; `regex` uses find-semantics. Default `regex`. |
| `value` | string               | The literal string or regex source.                                          |

A matcher with only a `value` needs no mapping: a bare scalar string in the list is taken as a regex, so `block` is a
plain list of regexes until a `literal` entry needs the `{ match: literal, value: … }` form.

**Matching is case-sensitive.** A `literal` matches the exact case, and a `regex` is case-sensitive unless it opts in
with an inline `(?i)` flag (e.g. `(?i)wip`). Case is never folded silently — a hostname or token that is case-specific
stays that way.

> **Deprecated shape.** `block` also accepts the older `{ literals: [...], patterns: [...] }` object for one minor
> release, folded into equivalent matchers at startup (a deprecation warning is logged): each `literals` entry becomes a
> `literal` matcher, each `patterns` entry a `regex` matcher. Migrate to the list form.
