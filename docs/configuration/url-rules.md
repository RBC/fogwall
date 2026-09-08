# URL rules

URL rules control which repositories are accessible through the proxy. fogwall is **default-deny**: if no allow rules
are configured for a provider, all pushes and fetches to that provider are rejected. At least one allow rule must match
for a request to proceed.

Rules use a unified `match` block that specifies what to match against (`target`), the pattern string (`value`), and how
to interpret it (`type`). Evaluation is first-match-wins by `order` — identical to iptables/firewall rule semantics.

`order` is optional for YAML-configured rules. When omitted, it's inferred from the entry's position within its
`allow[]`/`deny[]` array — the first entry gets `0`, the second `100`, and so on, leaving gaps for later insertion. An
explicit `order` always takes precedence over the inferred position. Rules created via the dashboard or REST API have no
array position to infer from, so they use a fixed default (`100`) unless an explicit `order` is set.

```yaml
rules:
  allow:
    # Specific repo by exact slug — both operations, scoped to one provider
    - enabled: true
      order: 110
      operation: BOTH
      provider: github
      match:
        target: SLUG
        value: /RBC/fogwall
        type: LITERAL

    # All repos under an owner — fetch only, any provider
    - enabled: true
      order: 120
      operation: FETCH
      match:
        target: OWNER
        value: finos
        type: GLOB

  deny:
    # Block a specific repo across all operations
    - enabled: true
      order: 100
      match:
        target: SLUG
        value: /myorg/forbidden-repo
        type: LITERAL
```

To allow all repositories on a provider (open mode):

```yaml
rules:
  allow:
    - enabled: true
      order: 110
      operation: BOTH
      provider: internal-github
      match:
        target: OWNER
        value: "*"
        type: GLOB
```

## URL rule properties

| Property       | Type    | Default      | Description                                                                |
| -------------- | ------- | ------------ | -------------------------------------------------------------------------- |
| `enabled`      | boolean | `true`       | Whether this entry is active                                               |
| `order`        | int     | _(position)_ | Evaluation order (lower = earlier; first match wins). Optional — see below |
| `operation`    | string  | `BOTH`       | `FETCH`, `PUSH`, or `BOTH` — which git operation this entry matches        |
| `provider`     | string  | _(all)_      | Provider name to scope this entry to; omit or leave blank for all          |
| `match`        | object  | —            | Repository match criteria — see below                                      |
| `match.target` | enum    | `SLUG`       | What to match: `SLUG` (the full repository path), `OWNER`, or `NAME`       |
| `match.value`  | string  | —            | The pattern to match against the chosen target                             |
| `match.type`   | enum    | `GLOB`       | How to interpret the pattern: `LITERAL`, `GLOB`, or `REGEX`                |

## Pattern matching

Each rule matches exactly one thing — the `match` block selects what URL part to test (`target`) and how to test it
(`type`). To express an AND condition (e.g. specific owner AND specific name prefix), use `target: SLUG` and write a
single glob or regex that covers both parts.

**Values are compared to the request path verbatim, for every match type.** These are URL rules, so a `SLUG` value
carries the leading `/` the path has — write `/acme/repo`, not `acme/repo`. `OWNER` and `NAME` are path segments and
carry no slash. fogwall does not normalise either way: a value whose leading `/` disagrees with its target can never
match, and is named in a startup warning rather than silently matching nothing.

**Case is the one exception, and is ignored for every match type.** Every supported provider resolves `owner/repo`
case-insensitively, so `/acme/widgets` and `/Acme/Widgets` are the same repository upstream — a rule written in one
casing covers both. This matters most for `DENY`: rules are first-match-wins, so a deny that missed on casing would hand
the request to whatever broader allow sits below it.

### Nested namespaces

The repository name is the **last** path segment and the owner is **everything before it**, so a repository path is not
limited to two segments. For a GitLab subgroup project `/group/subgroup/project`:

| Target  | Value matched against     |
| ------- | ------------------------- |
| `SLUG`  | `/group/subgroup/project` |
| `OWNER` | `group/subgroup`          |
| `NAME`  | `project`                 |

GitLab is the only supported provider that produces such a path — GitHub owners are a single segment, and Forgejo/Gitea
and Bitbucket address owner and repository as two plain segments. The same derivation applies to every path fogwall
serves: both proxy modes, and both of server mode's transports.

Two consequences when writing rules for subgroup projects:

- An `OWNER` value must name the whole namespace (`group/subgroup`), not just the top-level group. A `GLOB` of `group/*`
  matches one level of nesting; `group/**` matches any depth.
- A `SLUG` value must name every segment. A `LITERAL` of `/group/subgroup` does **not** admit the projects inside that
  subgroup — write `/group/subgroup/*` as a `GLOB` for that.

### LITERAL

Exact string match, ignoring case. The path shape is not normalised — `/acme/repo` matches the slug `/acme/repo` and
`/ACME/Repo`, while `acme/repo` matches nothing.

### GLOB

Wildcard matching using `*` (any characters) and `?` (single character), ignoring case. Both the pattern and the
candidate are folded before matching, so `GLOB` behaves the same on every host — without that it would inherit the
filesystem's own case rules and differ between Linux and macOS.

| Target  | `*` behaviour                                                                         |
| ------- | ------------------------------------------------------------------------------------- |
| `SLUG`  | Does **not** cross `/` — use `/acme/*` for one level, `/acme/**` for any depth        |
| `OWNER` | Does **not** cross `/` — a nested namespace needs `group/*` or `group/**` (see above) |
| `NAME`  | Repo names cannot contain `/` — `*` matches any valid name                            |

| Pattern (GLOB, target=SLUG) | Matches                                     | Does NOT match  |
| --------------------------- | ------------------------------------------- | --------------- |
| `/acme/repo` _(LITERAL)_    | `/acme/repo`                                | `/acme/other`   |
| `/acme/*`                   | `/acme/repo`, `/acme/my-service`            | `/other/repo`   |
| `/acme/service-*`           | `/acme/service-api`, `/acme/service-worker` | `/acme/repo`    |
| `/acme/repo-?`              | `/acme/repo-1`, `/acme/repo-a`              | `/acme/repo-12` |

### REGEX

Full Java regular expression. A few things to know before writing regex rules:

- **Full-string match**: the pattern must match the entire candidate string — there are no implicit anchors, but
  `matches()` semantics apply. A pattern of `acme` does **not** match `/acme/repo`; write `/acme/.*` or `.*acme.*`.
- **`/` does not need escaping**: Java regex uses strings, not a `/pattern/` literal syntax. Write `/acme/.*` not
  `\/acme\/.*`.
- **Case-insensitive already**: patterns are compiled with `CASE_INSENSITIVE`. An inline `(?i)` stays valid and is
  harmless, but is no longer needed.
- **Anchoring**: explicit `^` and `$` are redundant with `matches()` but harmless if included.

| Pattern (REGEX, target=SLUG) | Matches                               | Does NOT match      |
| ---------------------------- | ------------------------------------- | ------------------- |
| `/acme/.*`                   | `/acme/repo`, `/ACME/Repo`            | `/other/repo`       |
| `/(acme\|partner)/.*`        | `/acme/repo`, `/partner/repo`         | `/other/repo`       |
| `/acme/service-[0-9]+`       | `/acme/service-1`, `/acme/service-42` | `/acme/service-api` |

| Pattern (REGEX, target=NAME)       | Matches                      | Does NOT match |
| ---------------------------------- | ---------------------------- | -------------- |
| `(?i)(^&#124;-)secret(-&#124;$).*` | `secret-config`, `my-secret` | `secretariat`  |
| `migrate-.*`                       | `migrate-app`, `migrate-db`  | `old-migrate`  |

```yaml
rules:
  deny:
    # Block any repo whose name contains "secret" as a distinct word segment (case-insensitive)
    - enabled: true
      order: 50
      operation: PUSH
      match:
        target: NAME
        value: "(?i)(^|-)secret(-|$).*"
        type: REGEX

    # Block repos matching multiple owner orgs using alternation
    - enabled: true
      order: 51
      operation: BOTH
      match:
        target: OWNER
        value: "(blocked-org|suspended-org)"
        type: REGEX
```

## Real-world URL rule examples

**Gateway for a specific SCM — allow all push and fetch:**

```yaml
rules:
  allow:
    - enabled: true
      order: 110
      operation: BOTH
      provider: internal-github
      match:
        target: OWNER
        value: "*"
        type: GLOB
```

**Allow repos under a set of known owner orgs, identified by a name prefix:**

```yaml
rules:
  allow:
    - enabled: true
      order: 110
      operation: BOTH
      provider: internal-github
      match:
        target: OWNER
        value: "team-(alpha|beta|gamma)"
        type: REGEX
```

**Allow push only for repos whose name starts with a known prefix:**

When repos are identified by a project code followed by a hyphen, match on `NAME` so the rule applies regardless of
which org the repo lives under.

```yaml
rules:
  allow:
    - enabled: true
      order: 110
      operation: PUSH
      provider: internal-github
      match:
        target: NAME
        value: "proj0-*"
        type: GLOB

  # Multiple project prefixes — one rule per prefix, or combine with REGEX alternation:
  allow:
    - enabled: true
      order: 111
      operation: PUSH
      provider: internal-github
      match:
        target: NAME
        value: "(proj0|proj1|shared)-.*"
        type: REGEX
```

**Combine owner and name matching (AND condition):**

Use `target: SLUG` with a glob or regex — the slug is the full repository path, so a single pattern can constrain both
parts.

```yaml
rules:
  allow:
    # Allow fetch from the source SCM for a specific org + name prefix (glob AND)
    - enabled: true
      order: 110
      operation: FETCH
      provider: source-github
      match:
        target: SLUG
        value: "acquired-org/migrate-*"
        type: GLOB

    # Allow push to the destination SCM — stricter control with regex
    - enabled: true
      order: 120
      operation: PUSH
      provider: dest-gitlab
      match:
        target: SLUG
        value: "/migrated-org/migrate-.*"
        type: REGEX
```
