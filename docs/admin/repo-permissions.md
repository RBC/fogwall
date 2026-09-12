# Repo permissions

Permissions control which users can push to which repos, and who can review pushes.

```yaml
permissions:
  - username: alice
    provider: github/github.com
    path: /myorg/myrepo
    grant: PUSH
```

## Operations

| Value             | What it grants                                                                                                                                                                                                                                                                                                                                 |
| ----------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `PUSH`            | User can submit pushes to this repo for validation and review                                                                                                                                                                                                                                                                                  |
| `REVIEW`          | User can approve or reject pushes to this repo submitted by others                                                                                                                                                                                                                                                                             |
| `PUSH_AND_REVIEW` | Shorthand for both PUSH and REVIEW                                                                                                                                                                                                                                                                                                             |
| `SELF_CERTIFY`    | Per-repo entitlement: this user may self-approve pushes to this repo. Requires `ROLE_SELF_CERTIFY` (the capability role) to also be present — see [Dashboard roles](user-accounts.md#dashboard-roles). Does **not** imply PUSH or REVIEW; grant those separately if needed.                                                                    |
| `ISSUE`           | User can file, edit, comment on and close/reopen issues on this repo through the dashboard's issue form — and nothing more (no pull/merge-request or push ability). The narrow floor under `PROPOSE`, which is a superset; needs the provider `issues-enabled` and the user's account linked for it via OAuth. Independent of PUSH and REVIEW. |
| `PROPOSE`         | User can open and edit pull/merge requests and issues on this repo through the [SCM API proxy](scm-api.md). A superset of `ISSUE`. Independent of PUSH and REVIEW; implies neither.                                                                                                                                                            |
| `MERGE`           | User can merge a pull/merge request on this repo through the SCM API proxy's maintainer path. Independent of PUSH, REVIEW and PROPOSE; implies none of them.                                                                                                                                                                                   |
| `MAINTAIN`        | Sole-maintainer bundle: `PUSH` + `PROPOSE` + `MERGE` in one entry, so a trusted maintainer is one permission instead of three. Deliberately **excludes** `SELF_CERTIFY` — bypassing peer review stays a separate, explicit grant.                                                                                                              |

## SELF_CERTIFY — for solo contributors

`SELF_CERTIFY` is the right choice for a developer who works independently and does not have a team reviewer. Without
it, pushes in `ui` approval mode wait indefinitely for someone else to approve them.

Self-approval requires **two** things — both must be in place:

1. The `SELF_CERTIFY` role (capability gate) — granted via `auth.role-mappings` or `roles: [SELF_CERTIFY]` in local
   config. This is the org-level attestation that the user is trusted to self-certify at all.
2. A `SELF_CERTIFY` permission entry for the specific repo — the per-repo entitlement.

To set up a trusted solo contributor who approves their own work:

```yaml
# Step 1: grant the SELF_CERTIFY capability role (local auth example)
users:
  - username: bob
    password-hash: "{bcrypt}$2a$12$..."
    roles: [SELF_CERTIFY] # or via auth.role-mappings for LDAP/AD/OIDC

# Step 2: grant the per-repo entitlement
permissions:
  - username: bob
    provider: github/github.com
    path: /myorg/myrepo
    grant: PUSH
  - username: bob
    provider: github/github.com
    path: /myorg/myrepo
    grant: SELF_CERTIFY
```

Bob's pushes are validated as normal (commit rules, secret scanning, identity checks). Once validation passes, the proxy
records a self-certification in the audit log and forwards without waiting for a reviewer.

If Bob also needs to review others' pushes to that repo, add a third entry with `grant: REVIEW`.

## Permission groups

_Available since v1.3.0._

For teams larger than a handful of users, granting permissions one entry per user gets unwieldy. A `groups:` block
grants the same target/match model to every member at once:

```yaml
groups:
  - name: platform-team
    description: Platform engineering
    members: [alice, bob, carol]
    grants:
      - provider: github/github.com
        path: /myorg/*
        path-type: GLOB
        grant: PUSH
```

A member's effective access is the union of their direct `permissions:` entries and every group they belong to — groups
are additive, not a replacement for per-user grants. Groups defined in YAML are read-only in the dashboard (config is
the source of truth); groups created via the dashboard UI are DB-backed and fully editable there. Both kinds show up
together in the **Groups** admin page.

## Path matching

Paths default to exact (`LITERAL`) matching, ignoring case — `/myorg/repo` covers a push to `/MyOrg/Repo`, since both
name the same repository upstream. Use `path-type` for wildcards:

```yaml
# GLOB — all repos under an owner
- username: alice
  provider: gitlab/gitlab.com
  path: /myorg/*
  path-type: GLOB
  grant: PUSH

# REGEX — Java regex matched against the full repository path
- username: alice
  provider: github/github.com
  path: \/myorg\/service\-.*
  path-type: REGEX
  grant: PUSH
```

### Nested namespaces (GitLab subgroups)

A repository path is not limited to two segments: the repository name is the last segment and the owner is everything
before it. A GitLab subgroup project `/group/subgroup/project` has owner `group/subgroup` and name `project`, and its
slug keeps every segment. This applies to permissions, group grants and access rules alike, and to every path fogwall
serves — both proxy modes and both of server mode's transports. See
[Nested namespaces](../configuration/url-rules.md#nested-namespaces) for the per-target breakdown.

If you run GitLab with subgroups, check any rule or grant that names a subgroup: a value of `/group/subgroup` matches
that path only, not the projects inside it. Write `/group/subgroup/*` as a `GLOB` to cover them. An entry that stops
matching blocks the push rather than admitting it, so the failure is visible — the affected users are told the
repository is not permitted.

## Permissions vs access rules

A user with `PUSH` permission on `/myorg/myrepo` can still be blocked if `/myorg/myrepo` is not in `rules.allow`. Both
must be satisfied. The distinction:

- **Access rules** → "does the proxy route this repo at all?" — operator policy
- **Permissions** → "can this user push to it?" — per-user grant

A wildcard allow rule (`slugs: ["*/*"]`) effectively means "route everything" and shifts all control to the permissions
layer. A tightly scoped allow rule means you do not need to worry about accidentally granting a user permission to a
repo the proxy does not handle.
