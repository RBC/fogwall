# Commit attribution policy

> Formerly `commit.identity-verification`. The old key still binds (with a deprecation warning) but will be removed in a
> future release — rename it to `commit.attribution-policy`.

```yaml
commit:
  attribution-policy:
    committer: warn # warn | strict | off (default: warn)
    author: off # warn | strict | off (default: off)
```

For every push, the proxy runs two checks:

1. **SCM login check** — calls the upstream provider's user API with the token supplied in the git credentials (the HTTP
   Basic-auth password). The returned login (e.g. GitHub `login`, GitLab `username`) is matched against the
   authenticated fogwall user's `scm-identities`. This check is **always enforced** regardless of the
   `attribution-policy` mode — a push from a token that cannot be matched to a registered proxy user is always blocked.

2. **Commit email check** — every author and committer email in the pushed commits is checked against the authenticated
   fogwall user's `emails` list. These emails are populated independently of the SCM: they come from the IdP on
   LDAP/OIDC login, or from additional associations added via the dashboard. This is what ties commit attribution back
   to a verified real person. The `attribution-policy` mode controls this check only.

<!-- prettier-ignore-start -->
> [!NOTE]
> The HTTP Basic-auth username in the remote URL is not used for identity resolution. It is ignored by all providers (except Bitbucket). Configure your remote URL with any username — `git`, `me`, your actual name — it makes no difference.
<!-- prettier-ignore-end -->

## Modes

`attribution-policy` controls the **commit email check** only, independently for `committer` and `author`. The SCM login
check is always enforced.

| Mode     | Behaviour                                                                                       | Use when                                                                       |
| -------- | ----------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------ |
| `strict` | Blocks the push if any commit email cannot be matched to the authenticated fogwall user         | Production — enforces that every commit is attributed to the person who pushed |
| `warn`   | Allows the push through but emits a sideband warning to the git client and records the mismatch | Rolling out to an existing team — lets you observe mismatches before enforcing |
| `off`    | Commit email check is disabled entirely                                                         | Migrations or environments where email data is not yet populated               |

<!-- prettier-ignore-start -->
> [!CAUTION]
> `warn` is not a security control. Pushes succeed regardless of the email check outcome. Only `strict` blocks mismatched commits. The default is `warn` to avoid breaking existing deployments on first install.
>
> **Committer vs author:** the two are checked independently. `committer` defaults to `warn` — the committer is who last touched the commit object, i.e. the pusher on their own work. `author` defaults to `off` because rebased or cherry-picked commits legitimately preserve a different original author, so blocking on it would reject valid workflows. Enable `author: strict` only on closed boundaries (private-to-private, M&A integration) where every commit must be authored by the pusher; leave it `off` for open-source contribution flows.
<!-- prettier-ignore-end -->

## Token scope requirements

The SCM login check calls `GET /user` (or equivalent) on the upstream SCM using the pusher's token. The token must carry
at least the following scope:

| Provider | API endpoint                           | Additional scope                                                       |
| -------- | -------------------------------------- | ---------------------------------------------------------------------- |
| GitHub   | `GET https://api.github.com/user`      | No additional scopes required for either classic or fine-grained PATs. |
| GitLab   | `GET {uri}/api/v4/user`                | `read_user` or `api` (not recommended)                                 |
| Codeberg | `GET https://codeberg.org/api/v1/user` | `read:user`                                                            |
| Gitea    | `GET https://gitea.com/api/v1/user`    | `read:user`                                                            |

If the token is missing the required scope or cannot be resolved to a registered proxy user, the push is blocked
regardless of `attribution-policy` mode.

## Prerequisites

Both checks require the user record to be populated before a push. A push from a token that cannot be matched to any
registered proxy user is always blocked. Use `attribution-policy` with `committer: warn` during rollout to allow pushes
through while users register their commit emails; the SCM identity must be registered before any push can proceed.

```yaml
users:
  - username: alice
    password-hash: "{bcrypt}$2a$12$..."
    roles:
      - ADMIN # optional; defaults to [USER] if omitted
    emails:
      - alice@example.com
    # push-usernames: HTTP Basic-auth usernames accepted for this user when pushing.
    # The proxy username is always implicitly valid; these are additional aliases.
    # Useful when git clients send a fixed username (e.g. "git") that differs from
    # the proxy username. Stored internally as SCM identities under the "proxy" provider.
    push-usernames:
      - git
      - alice-bot
    scm-identities:
      - provider: github
        username: alice-gh
      - provider: gitlab
        username: alice
```
