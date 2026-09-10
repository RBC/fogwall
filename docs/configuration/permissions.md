# Permissions

Permissions control which proxy users can push to or review pushes from specific repositories. They are checked
**after** URL rules: a push that is blocked by a deny rule never reaches the permission check.

Permissions are hot-reloadable (see [Reloadable sections](hot-reload.md#reloadable-sections)).

```yaml
permissions:
  # LITERAL (default): exact /owner/repo match
  - username: alice
    provider: github
    match:
      target: SLUG
      value: /myorg/myrepo
      type: LITERAL
    grant: PUSH

  # GLOB: wildcard repo name under a specific owner
  - username: bob
    provider: gitlab
    match:
      target: SLUG
      value: /myorg/*
      type: GLOB
    grant: PUSH_AND_REVIEW

  # OWNER target: grant access to all repos under an org
  - username: carol
    provider: github
    match:
      target: OWNER
      value: myorg
      type: GLOB
    grant: REVIEW

  # REGEX on SLUG: match repos under multiple orgs
  - username: dave
    provider: github
    match:
      target: SLUG
      value: "/team-(alpha|beta)/.*"
      type: REGEX
    grant: PUSH_AND_REVIEW

  # SELF_CERTIFY: trusted contributor who can approve their own clean pushes.
  # Requires both this permission entry AND the SELF_CERTIFY role on the user.
  - username: trusted
    provider: github
    match:
      target: SLUG
      value: /myorg/myrepo
      type: LITERAL
    grant: SELF_CERTIFY
```

## Permission properties

| Property       | Type   | Default           | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| -------------- | ------ | ----------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `username`     | string | —                 | Proxy username (must match a `users:` entry or a DB user)                                                                                                                                                                                                                                                                                                                                                                                                            |
| `provider`     | string | —                 | Provider name as defined in `providers:` config                                                                                                                                                                                                                                                                                                                                                                                                                      |
| `match`        | object | —                 | Repository match criteria — see below                                                                                                                                                                                                                                                                                                                                                                                                                                |
| `match.target` | enum   | `SLUG`            | What to match: `SLUG` (the full repository path), `OWNER`, or `NAME`                                                                                                                                                                                                                                                                                                                                                                                                 |
| `match.value`  | string | —                 | The pattern to match against the chosen target                                                                                                                                                                                                                                                                                                                                                                                                                       |
| `match.type`   | enum   | `GLOB`            | How to interpret the pattern: `LITERAL`, `GLOB`, or `REGEX`                                                                                                                                                                                                                                                                                                                                                                                                          |
| `grant`        | enum   | `PUSH_AND_REVIEW` | What the user may do: `PUSH`, `REVIEW`, `PUSH_AND_REVIEW`, `SELF_CERTIFY`, `ISSUE`, `PROPOSE`, `MERGE`, `MAINTAIN`. `ISSUE`/`PROPOSE`/`MERGE` (v1.4.0+) are independent of `PUSH`/`REVIEW`; `ISSUE` gates the [dashboard issue form](../admin/proposals.md#filing-issues-from-the-dashboard-no-cli) (the narrow floor under `PROPOSE`), `PROPOSE`/`MERGE` the [SCM API proxy](proposals.md). `MAINTAIN` bundles `PUSH`, `PROPOSE` and `MERGE` for a sole maintainer. |

## Pattern matching

Permissions support the same three match types as URL rules (LITERAL, GLOB, REGEX) applied to the same three targets
(SLUG, OWNER, NAME), matched the same way — including case-insensitively, so a grant covers the repository it names
whatever casing the push uses. See [Pattern matching](url-rules.md#pattern-matching) above for full semantics including
regex behaviour.

GLOB on `target: SLUG` follows slug path conventions:

| Pattern (GLOB, target=SLUG) | Matches                                     | Does NOT match |
| --------------------------- | ------------------------------------------- | -------------- |
| `/acme/repo`                | `/acme/repo`                                | `/acme/other`  |
| `/acme/*`                   | `/acme/repo`, `/acme/my-service`            | `/other/repo`  |
| `/acme/service-*`           | `/acme/service-api`, `/acme/service-worker` | `/acme/repo`   |
| `/*/proj0-*`                | `/acme/proj0-api`, `/other/proj0-db`        | `/acme/other`  |

<!-- prettier-ignore-start -->
> [!NOTE]
> **Conflict detection:** At config load time and when saving via the dashboard API, fogwall rejects any new permission entry whose pattern overlaps with an existing entry for the same user and provider. Two entries overlap when they are equal ignoring case, or when one is a GLOB/REGEX pattern that would match the other's value. This prevents silent misconfiguration where the effective permission depends on evaluation order.
<!-- prettier-ignore-end -->

## Real-world permission examples

**Allow a user to push any repo on a specific provider:**

```yaml
permissions:
  - username: alice
    provider: internal-github
    match:
      target: OWNER
      value: "*"
      type: GLOB
    grant: PUSH_AND_REVIEW
```

**Allow push to repos whose name starts with a project code:**

```yaml
permissions:
  - username: alice
    provider: internal-github
    match:
      target: NAME
      value: "proj0-*"
      type: GLOB
    grant: PUSH

  # Or match the same prefix across any owner using SLUG:
  - username: alice
    provider: internal-github
    match:
      target: SLUG
      value: "/*/proj0-*"
      type: GLOB
    grant: PUSH
```

**Regex — match repos under multiple owner orgs:**

```yaml
permissions:
  - username: alice
    provider: internal-github
    match:
      target: SLUG
      value: "/team-(alpha|beta)/.*"
      type: REGEX
    grant: PUSH_AND_REVIEW
```

**Self-certify for a trusted committer scoped to a prefix:**

A trusted committer needs both entries: `PUSH_AND_REVIEW` to be able to push, and `SELF_CERTIFY` to bypass the peer
review requirement. These cover separate code paths and are not treated as conflicting.

```yaml
permissions:
  # Push and review access
  - username: trusted-dev
    provider: internal-github
    match:
      target: NAME
      value: "proj0-*"
      type: GLOB
    grant: PUSH_AND_REVIEW

  # Self-certify on the same scope (requires SELF_CERTIFY role on the user too)
  - username: trusted-dev
    provider: internal-github
    match:
      target: NAME
      value: "proj0-*"
      type: GLOB
    grant: SELF_CERTIFY
```

## Grant

| Value             | Effect                                                                                                                                                                                                     |
| ----------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `PUSH`            | User may push to matching repositories                                                                                                                                                                     |
| `REVIEW`          | User may approve or reject pushes submitted by others                                                                                                                                                      |
| `PUSH_AND_REVIEW` | Shorthand for both PUSH and REVIEW; does **not** include SELF_CERTIFY                                                                                                                                      |
| `SELF_CERTIFY`    | Trusted contributor: may approve their own clean pushes without a peer reviewer. Requires the `SELF_CERTIFY` role as well                                                                                  |
| `ISSUE`           | User may file and follow up on issues through the [dashboard issue form](../admin/proposals.md#filing-issues-from-the-dashboard-no-cli) — the narrow floor under `PROPOSE`, independent of `PUSH`/`REVIEW` |
| `PROPOSE`         | User may open and edit pull/merge requests and issues through the [SCM API proxy](proposals.md) — independent of `PUSH`/`REVIEW`                                                                           |
| `MERGE`           | User may merge a pull/merge request through the SCM API proxy's maintainer path — independent of `PUSH`/`REVIEW`/`PROPOSE`                                                                                 |
| `MAINTAIN`        | Sole-maintainer bundle: `PUSH` + `PROPOSE` + `MERGE` in one entry. Deliberately excludes `SELF_CERTIFY` — that stays a separate grant                                                                      |

<!-- prettier-ignore-start -->
> [!IMPORTANT]
> `SELF_CERTIFY` is a two-key lock: the user must have both a `SELF_CERTIFY` permission entry for the repository _and_ the `SELF_CERTIFY` role (set via `users[].roles` or `auth.role-mappings`). Either alone is not sufficient.
<!-- prettier-ignore-end -->
