# Identity and credentials

Every git operation through fogwall crosses two legs: the client authenticates to fogwall, and fogwall reaches the
upstream provider. This page lists each identity and credential involved on either leg, who issues it, where it is
stored, what it can do, and how it is revoked. It is written for an organisation's IAM and security reviewers.

## The two legs, by mode

| Mode                          | Client → fogwall                                                    | fogwall → upstream                                                                   |
| ----------------------------- | ------------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| Transparent proxy (`/proxy`)  | The client's own SCM credential, over HTTP Basic                    | The same credential, relayed unchanged                                               |
| Server mode, HTTP (`/server`) | The client's own SCM credential, or a fogwall-issued git credential | The client's credential; or, for a fogwall credential, the user's linked OAuth token |
| Server mode, SSH              | The client's SSH key, registered with fogwall                       | The client's own key, through the forwarded SSH agent                                |
| SCM API listeners             | The client's own SCM token                                          | The same token, relayed unchanged                                                    |

In every mode fogwall resolves the pushing user before it runs its checks, and every push record names that user.

## Identities

**Dashboard user.** A person who signs in to the dashboard, through local accounts (bcrypt password hashes in config or
the database), LDAP, Active Directory or OIDC. The same username is the fogwall user a push resolves to. Roles (`USER`,
`ADMIN`, `SELF_CERTIFY`) are stored with the user; with LDAP or OIDC they can be derived from directory groups through
`auth.role-mappings`. Removing the user removes their roles, permissions, SSH keys, linked OAuth tokens and
fogwall-issued git credentials.

**SCM identity.** An account on an upstream provider, recorded against a fogwall user as `(provider, login)`. It is
entered by hand or proved by OAuth account linking, which marks it verified. Push identity is always the SCM account a
credential belongs to, never commit metadata. With `scm-oauth.identity-mode: strict`, only verified identities count.

## Credentials

### Client's own SCM credential

- **Issued by** the provider (a personal access token, an OAuth token, or a password where the provider allows one).
- **Stored by fogwall:** never. fogwall calls the provider's user API with it to find the SCM login, and caches the
  answer under a SHA-512 hash of the credential for a bounded time. The credential itself is held in memory for the
  length of the request.
- **Can do:** whatever the provider allows it. fogwall adds no rights and removes none upstream.
- **Revoked** at the provider.

### SSH key

- **Issued by** the developer. The public key is registered with fogwall on the profile page, in config, or imported
  from the provider when the account is linked.
- **Stored:** the public key and its fingerprint. The private key never reaches fogwall; the upstream push uses the
  client's forwarded agent.
- **Revoked** by removing the key from the profile or the provider.

### Linked OAuth token

- **Issued by** the provider, when the user links their account from the dashboard profile (the authorization-code flow,
  against the OAuth application configured under `providers.<name>.oauth`).
- **Stored:** the access and refresh tokens, encrypted with AES-256-GCM under the key at
  `scm-oauth.token-encryption-key-path`. Expired access tokens are renewed with the refresh token.
- **Can do:** the scopes requested at link time. Identity and key import only, unless the provider has dashboard issue
  filing (an issue write scope) or brokered pushes (a repository write scope) enabled.
- **Revoked** by unlinking, which deletes the tokens and asks GitHub or GitLab to revoke them; at the provider, after
  which fogwall can no longer renew it; or by age under `scm-oauth.max-link-age`, counted from when the user authorized
  the link.

### fogwall-issued git credential

- **Issued by** fogwall, from the user's profile, on deployments where some provider has `oauth.brokered-push`.
- **Stored:** only a SHA-256 hash of its secret, with its name, creation time, expiry and last use. The value is shown
  once, when it is issued or rotated.
- **Can do:** authenticate its user to fogwall's server mode over HTTP, on providers with `brokered-push`. The provider
  does not recognise it, and fogwall never sends it upstream. A push made with it passes every fogwall control, then is
  forwarded with the user's linked OAuth token.
- **Revoked** by its owner or an administrator, by removing the user, or by expiry under
  `auth.git-credentials.max-lifetime`. Rotation replaces its value.

### Service credentials

- **Provider API token** (`providers.<name>.api-token`): a token fogwall uses on its own behalf, where a provider's SSH
  key listing requires authentication.
- **OAuth application secret** (`providers.<name>.oauth.client-secret-path`): used for the code exchange and token
  refresh of linked OAuth tokens.
- **Token encryption key** (`scm-oauth.token-encryption-key-path`): encrypts linked OAuth tokens at rest.
- **Dashboard API key** (`FOGWALL_API_KEY`): a shared key for REST automation, carrying no user identity.

Each is read from a file or the environment and is under the operator's custody.

## Trust boundaries

- The transparent proxy and the SCM API listeners never substitute a credential. What upstream sees is what the client
  sent.
- In server mode, fogwall holds the pushed objects locally until its checks and approval pass. With a client credential,
  the forward uses that credential; with a fogwall credential, it uses the linked OAuth token, looked up when the
  forward happens.
- A fogwall credential presented for a provider without `brokered-push` is refused, not relayed.
- A push is never forwarded without a usable credential. A request whose linked OAuth token cannot be used is refused,
  with the remedy, before anything is received. A token that lapses while the push awaits review fails the forward, and
  the push record ends in `ERROR`.

## What an organisation can turn off

| Capability                          | Off by                                                      |
| ----------------------------------- | ----------------------------------------------------------- |
| OAuth account linking               | `providers.<name>.oauth.enabled: false` (the default)       |
| Brokered pushes and git credentials | `providers.<name>.oauth.brokered-push: false` (the default) |
| Unverified SCM identities           | `scm-oauth.identity-mode: strict`                           |
| Server mode fetches                 | `server.serve-fetch` or `providers.<name>.serve-fetch`      |
| SSH transport                       | `server.ssh.enabled: false` (the default)                   |
