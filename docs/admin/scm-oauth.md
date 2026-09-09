# SCM OAuth account linking

_Available since v1.4.0._

Lets developers link their proxy account to an upstream SCM identity via OAuth from their profile page, instead of
typing a free-text SCM username. See [SCM OAuth](../configuration/scm-oauth.md) for the full config reference; this
section covers operator setup steps and operational behaviour.

## Registering a GitHub App

For **identity linking only** — verifying a user's GitHub identity and importing their emails and SSH keys — fogwall's
linking flow works with a **GitHub App** (GitHub's currently recommended integration type), and this is the least-scope
option. If you also enable the **dashboard issue feature** (`issues-enabled`) for a GitHub provider, use a **classic
OAuth App instead** — see the note below, because a GitHub App cannot do it.

1. Create the app under your org's GitHub settings (or a personal account, for testing).
2. **Account permissions** — grant exactly:
   - **Email addresses**: Read-only
   - **Git SSH keys**: Read-only
3. **Callback URL**: `https://<your-fogwall-host>/api/scm-oauth/<provider-name>/callback`, where `<provider-name>` is
   the top-level `providers:` key this app's `oauth:` block is nested under — e.g. `github`, not the literal string
   "github.com". This is always your fogwall host, regardless of whether the provider instance points at github.com, a
   GHEC-with-data-residency `*.ghe.com` tenant, or a self-managed GHES host — only the _outbound_
   authorize/token/user-API calls fogwall makes differ by host, not where GitHub calls back to. `<your-fogwall-host>` is
   exactly `server.service-url` (the bare origin, no path suffix — see the breaking-change note below if you're
   upgrading from a pre-1.4.0 release).
4. Generate a client secret and note the client ID. **No private key is needed** — a GitHub App's private key
   authenticates the app/installation itself (server-to-server), which this user-to-server linking flow never uses; only
   the client-id/client-secret pair is used, for the token exchange.
5. Install the app on your GitHub org (or your personal account, for testing) so it can be authorized by member
   accounts.

Repeat with a second, separately registered app for each additional GitHub-type provider instance you run (e.g. one app
for github.com/GHEC, a second for a `*.ghe.com` tenant) — each needs its own client-id/secret, set under that distinct
`providers:` entry's own nested `oauth:` block.

### GitHub issue filing needs a classic OAuth App, not a GitHub App

The dashboard issue feature has fogwall act **as the user** to write issues on whichever repository they hold the grant
for. A GitHub App cannot do this: it is **installation-gated** — even its user-to-server tokens only reach repositories
the app is _installed_ on, and installation requires repo/org admin, so it can never act on a public or third-party repo
the user does not administer. A **classic OAuth App** is the only GitHub credential whose user token acts on any
repository the user can access, no installation.

So for a GitHub provider with `issues-enabled`, register a **classic OAuth App** (GitHub → Settings → Developer settings
→ **OAuth Apps**, a different registration from GitHub Apps) with the same callback URL shape as above. fogwall requests
the scopes it needs at link time — `read:user`, `user:email`, `read:public_key` (identity, emails, SSH keys) plus
**`repo`** (the issue write; GitHub has no "issues only" OAuth scope, so `repo` is the narrowest that permits it, or
`public_repo` if only public repositories are in scope). The `repo` scope is only added when `issues-enabled` is set, so
an identity-only deployment still requests read-only scopes. Keep the GitHub App for fogwall's own installation-token
operations (service-account features); use the OAuth App for user-on-behalf issue filing.

## Registering a GitLab OAuth application

Under **User Settings → Applications** (or your GitLab instance's admin area for an instance-wide app): set the same
callback URL shape as above, and check the **`read_user`** scope. fogwall's authorize request asks for exactly this
scope for a GitLab-type provider — unless the provider has `issues-enabled`, in which case it asks for the broader
**`api`** scope (there is no narrower GitLab scope that permits an issue write), so grant that scope on the application
instead. It isn't otherwise configurable.

## Registering a Forgejo/Gitea OAuth application

Under the instance's own **Settings → Applications** page: register an OAuth2 application with the same callback URL
shape as above, and note the generated client ID/secret. Request the `read:user` scope — fogwall's authorize request
asks for exactly this for a `forgejo`-type provider, unless the provider has `issues-enabled`, in which case it also
asks for **`write:issue`** so it can file issues on the user's behalf.

This works against a self-hosted Forgejo/Gitea instance you administer, and also against Codeberg — its OAuth2
applications live under **Settings → Applications → Manage OAuth2 Applications** (or under an organization's own
settings, for an org-owned app), same flow as any other Forgejo instance.

## What `strict` identity mode changes operationally

With `scm-oauth.identity-mode: strict`, `CheckUserPushPermissionHook` only honors OAuth-verified SCM identities for push
authorization — on both HTTP and SSH transports. A user whose only SCM identity is manually/free-text entered (or who
hasn't linked one at all) gets a clear push-time rejection pointing them at the profile page to link via OAuth. There is
no fallback to permissive behavior if OAuth linking becomes unavailable (see token encryption key handling below) — the
two are deliberately decoupled: a token-encryption problem disables the _link/callback_ endpoints, never push
authorization, so `strict` mode's guarantee can't be silently weakened by an infrastructure fault.

`POST /api/me/identities` (manually adding an SCM identity) is also disabled in `strict` mode, both in the dashboard UI
and server-side on the endpoint itself — a manually-entered identity would never actually be usable for push
authorization in this mode, so allowing it to be added would only create a confusing dead state. This is narrower than
it might sound: it does **not** affect `POST /api/me/emails` — commit-author-email verification is governed by the
independent `commit.attribution-policy` setting, not `scm-oauth.identity-mode`.

## What happens on unlink

`DELETE /api/scm-oauth/<provider>/unlink` (the "Unlink" button in the profile page's SCM Identities tab) removes:

- the verified SCM identity itself
- the stored OAuth token (with a best-effort revocation call to the provider)
- any SSH keys that were imported from that provider
- any emails that were imported from that provider's verified-emails list

If the same SSH key or email was also verified by a second linked provider (e.g. the same key registered on both GitHub
and GitLab), unlinking one only removes that provider's claim on it — the key/email stays registered, now attributed
solely to the remaining provider(s), and is only fully removed once no linked provider claims it anymore. Re-linking is
always available to restore the identity and re-import SSH keys/emails if needed.

## Production checklist addition: token encryption key

See [Production checklist](production-checklist.md) below for database/TLS. For SCM OAuth specifically: generate a
32-byte key and mount it as a secret rather than relying on the local-devex auto-generated fallback:

```bash
openssl rand -base64 32 > fogwall-scm-oauth-key
```

```yaml
scm-oauth:
  token-encryption-key-path: /run/secrets/fogwall-scm-oauth-key
```

If this is left unset, fogwall auto-generates and persists a key under `./.data/` and logs a loud `WARN` on every
startup — fine for local development, but that file may not survive a container restart/redeploy in production. If lost,
every linked user simply needs to re-link (push authorization is never affected).
