# User accounts

fogwall supports four authentication backends. **LDAP, AD, and OIDC are the expected production choices.** Local auth
manages users in the database (add/remove users, reset passwords via the dashboard) with passwords defined in YAML
config. It is self-contained and requires no external directory, but every user must be provisioned manually. It is
suitable for small teams or single-operator deployments; LDAP, AD, or OIDC are preferable when the org already has a
directory.

## Authentication backends

| Backend          | `auth.provider` | When to use                                                    |
| ---------------- | --------------- | -------------------------------------------------------------- |
| Local (static)   | `local`         | Dev / demo only. Passwords in YAML config.                     |
| LDAP             | `ldap`          | Generic LDAP directory (OpenLDAP, 389 DS, etc.)                |
| Active Directory | `ad`            | On-premises AD domain. UPN bind, no `user-dn-patterns` needed. |
| OIDC             | `oidc`          | Keycloak, Okta, Entra ID, Dex, etc.                            |

See [Authentication](../configuration/authentication.md) for the full config reference and worked examples.

## How users are provisioned per backend

**Local:** users are defined entirely in the `users:` YAML block. Each entry needs a username, BCrypt password hash, and
at least one email. Roles and SCM identities are set here too. Changes require a config reload.

```yaml
users:
  - username: alice
    password-hash: "{bcrypt}$2a$12$..."
    roles: [ADMIN]
    emails:
      - alice@corp.example.com
    scm-identities:
      - provider: github/github.com
        username: alice-github
```

**LDAP / AD:** users are provisioned automatically on first login. The proxy creates a user record from the directory
attributes returned at bind time. The `mail` attribute (if present) is stored as a locked email — locked means it cannot
be edited from the profile UI, since the directory is the source of truth. Roles are assigned via `auth.role-mappings`
(LDAP group CNs → role names). When `role-mappings` is configured, a user who does not match any mapped group is
**denied access entirely** — they authenticate successfully against the directory but are refused by the proxy. This is
intentional: the proxy is not open to all directory users by default. To grant baseline access, map a broad group (e.g.
all-staff) to `USER`, or set `auth.require-role-mapping: false` to treat the directory purely as an authentication
mechanism and grant `ROLE_USER` to anyone who authenticates. See
[Role mappings](../configuration/authentication.md#role-mappings).

SCM identities and permissions still need to be set up after first login — either by the user themselves from their
profile page, by an admin via the dashboard, or via a supplemental `users:` YAML entry (which can carry `scm-identities`
without a `password-hash` for IdP-authed users).

**OIDC:** same auto-provisioning and deny-by-default behaviour as LDAP. Groups from the configured `groups-claim`
(default: `groups`) are mapped to roles via `auth.role-mappings`. Email comes from the `email` claim in the ID token.
Users whose token carries no matching group claim are denied access.

## Dashboard roles

Roles control what a user can do in the dashboard and REST API:

| Role             | What it grants                                                                                                                                                   |
| ---------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `USER` (default) | View push records; approve or reject pushes they have `REVIEW` permission on; manage their own profile (emails, SCM identities)                                  |
| `ADMIN`          | Everything USER can do, plus: create/delete users, reset passwords, manage any user's profile, view all push records                                             |
| `SELF_CERTIFY`   | Grants the **capability** to self-approve pushes. This is the prerequisite gate — it must be present before any per-repo `SELF_CERTIFY` permission takes effect. |

`ROLE_USER` is granted to every authenticated user automatically when no `role-mappings` are configured (open mode).
When `role-mappings` are configured, access is deny-by-default — a user must belong to at least one mapped group or they
are refused login entirely. Map a broad group to `USER` to grant baseline access to all directory members.

`ROLE_SELF_CERTIFY` is the prerequisite gate for self-approval. It represents the capability, attested by your org's IdP
or IAM process. Self-approval requires **both** this role and a per-repo `SELF_CERTIFY` permission entry — neither alone
is sufficient. This separation lets organisations externalise the capability grant (who is trusted to self-certify at
all) to their existing directory/IAM procedures, while the per-repo entitlement remains managed inside fogwall.

How to grant `ROLE_SELF_CERTIFY`:

- **LDAP / AD / OIDC:** add `SELF_CERTIFY` to `auth.role-mappings` and map it to the appropriate IdP group.
- **Local auth (config user):** add `SELF_CERTIFY` to `roles:` in the user's `users:` YAML entry.
- **Local auth (dashboard-created user):** tick the "Grant self-certify role" box in the Add User form. Local auth is
  intended for demonstration and proof-of-concept; a production deployment grants the capability through its IdP
  instead.

<!-- prettier-ignore-start -->
> [!NOTE]
> Organisations that require mandatory peer review (four-eyes) for all activity should simply not grant `SELF_CERTIFY` role or permissions. If no user holds `SELF_CERTIFY`, all pushes require a separate reviewer.
<!-- prettier-ignore-end -->

**Roles are dashboard-level access only.** They do not control which repos a user can push to — that is what permissions
(below) are for.

## Emails and SCM identities

Every user record carries two independent data sets that the proxy uses to verify identity on each push:

**Emails** — the set of email addresses the user commits with (i.e. the value in `git config user.email`). On every
push, every author and committer email in the incoming commits is checked against this list. If an email is not
registered to the authenticated user, the push fails in `strict` mode or warns in `warn` mode. This is what ties commit
attribution to a verified real person.

**SCM identities** — the upstream provider username(s) for this user (e.g. their GitHub login). On every push, the proxy
calls the upstream API using the PAT supplied in the git credentials and checks the returned username against this list.
This confirms that the token being used actually belongs to the person who authenticated with the proxy, not a shared or
borrowed token.

These are two independent checks and both must pass in `strict` mode. They catch different things: a commit email
mismatch means the developer's git client is misconfigured or the commit is attributed to someone else; an SCM identity
mismatch means the token does not belong to the authenticated user.

**How emails are populated:**

- Local auth: set in the `users:` YAML block; editable from the profile UI.
- LDAP/AD: the directory `mail` attribute is imported on first login as a locked email (not editable from the UI — the
  directory is the source of truth). Additional emails can be added via the admin dashboard.
- OIDC: the `email` claim from the ID token is imported on first login as a locked email.

**How SCM identities are populated:**

There is no automatic source for SCM identities — they must be added manually regardless of auth backend. After first
login, either the user themselves or an admin can add SCM identities from the profile page in the dashboard. For
example: provider `github/github.com`, username `alice-gh`. Users manage their own profile; admins can manage any user's
profile.

For local auth, SCM identities can also be set in the `users:` YAML block:

```yaml
users:
  - username: alice
    # ...
    scm-identities:
      - provider: github/github.com
        username: alice-gh
      - provider: gitlab/gitlab.com
        username: alice
```

Until SCM identities are populated, pushes from that user will fail identity verification in `strict` mode. Use
`attribution-policy` with `committer: warn` during rollout to let pushes through while identities are being registered.
See [Identity verification](../user/identity-verification.md) for the developer-facing view of what these checks look
like at the terminal.

## Disabling local admin when using an IdP

When LDAP, AD, or OIDC is configured, the static `users:` block still works and is evaluated alongside the IdP. In most
production setups you want to remove static local accounts (or at minimum remove any with `roles: [ADMIN]`) once
IdP-based login is confirmed working. See #103 for planned enforcement of this.
