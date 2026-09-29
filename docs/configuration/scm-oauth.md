# SCM OAuth

_Available since v1.4.0._

Lets a proxy user link their account to an upstream SCM identity (GitHub, GitLab, or a Forgejo/Gitea/Codeberg instance)
via OAuth from their profile page, instead of typing their SCM username into a free-text field. A successful link sets
`verified = true` on that identity, which `scm-oauth.identity-mode: strict` can then require for push authorization —
closing the gap where a manually entered SCM username is trusted with no proof the pusher actually controls it. Linking
also imports the SCM provider's own verified emails and registered SSH public keys, so a developer doesn't have to
re-enter data the provider already has confirmed.

```yaml
scm-oauth:
  # permissive (default): any linked SCM identity is usable for push authorization, verified or not — today's
  # behaviour, unchanged.
  # strict: only OAuth-verified identities count, on both HTTP and SSH push paths. On HTTP the account the token
  # belongs to must itself be one OAuth verified — a verified identity on the same provider does not vouch for a
  # hand-typed sibling, or for a user matched by email. On SSH the connecting key must be one OAuth linking
  # imported — a key added by hand on the profile page no longer resolves an SCM identity, even if the provider
  # would confirm it is registered there.
  identity-mode: permissive

  # Path to a file holding a base64-encoded 32-byte AES-256-GCM key, used to encrypt linked OAuth tokens at rest.
  # If unset, a key is auto-generated under ./.data/ for local development only — a loud warning is logged on every
  # startup when this happens. Production deployments MUST set this to a durable, backed-up location (see
  # the administrator guide's production checklist); losing an auto-generated key just means every linked user has to
  # re-link — push authorization itself is never affected by a token-encryption problem.
  token-encryption-key-path: /run/secrets/fogwall-scm-oauth-key

  # How long a linked account can be used after the user authorized it, as an ISO-8601 duration (PT12H, P7D, P30D).
  # Unset for no limit. Refreshing a token does not restart it; linking the account again does.
  max-link-age: P30D

# OAuth app registration is a property of the provider instance it belongs to, nested under that provider's own
# providers.<name>.oauth block below — not a separate map keyed by the same name. An operator running two separate
# GitHub OAuth apps at once (one for github.com/GHEC, a second for a GHEC-with-data-residency *.ghe.com tenant, or a
# self-managed GHES host) already needs two separate providers: entries for routing; each just carries its own
# oauth: block alongside its uri. The OAuth authorize/token/user-API host is always derived from that same provider
# instance's own `uri` — github.com/GHEC, *.ghe.com, and self-managed GHES are each detected automatically, so
# there's nothing to set for that under oauth: specifically. GHES's `api-uri` (the general provider setting, not
# scm-oauth-specific) is also auto-derived and only needs setting explicitly when the API isn't reachable on the
# standard host/port (e.g. local dev).
providers:
  github:
    enabled: true # github.com/GHEC
    oauth:
      enabled: true
      client-id: Iv1.abc123
      client-secret-path: /run/secrets/fogwall-github-oauth-secret

  github-ghe:
    enabled: true
    type: github
    uri: https://my-tenant.ghe.com
    oauth:
      enabled: true
      client-id: Iv1.def456
      client-secret-path: /run/secrets/fogwall-github-ghe-oauth-secret

  gitlab:
    enabled: true # gitlab.com
    oauth:
      enabled: true
      client-id: abc123
      client-secret-path: /run/secrets/fogwall-gitlab-oauth-secret

  forgejo:
    enabled: true
    type: forgejo
    uri: https://forgejo.example.internal # self-hosted
    oauth:
      enabled: true
      client-id: abc123
      client-secret-path: /run/secrets/fogwall-forgejo-oauth-secret
```

## SCM OAuth properties

| Property                                     | Type    | Default      | Description                                                                                                                                            |
| -------------------------------------------- | ------- | ------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `identity-mode`                              | string  | `permissive` | `permissive` or `strict` — see above.                                                                                                                  |
| `token-encryption-key-path`                  | string  | _(none)_     | Path to the base64-encoded 32-byte AES-256-GCM key file. Auto-generated under `./.data/` for local dev if unset and some provider has `oauth.enabled`. |
| `max-link-age`                               | string  | _(none)_     | ISO-8601 duration after which a linked account must be linked again, counted from when the user authorized it. See the administrator guide.            |
| `providers.<name>.oauth.enabled`             | boolean | `false`      | Whether "Link via OAuth" is offered for this provider.                                                                                                 |
| `providers.<name>.oauth.client-id`           | string  | `""`         | OAuth app/client ID.                                                                                                                                   |
| `providers.<name>.oauth.client-secret-path`  | string  | `""`         | Path to a file holding the OAuth app/client secret.                                                                                                    |
| `providers.<name>.oauth.brokered-push`       | boolean | `false`      | Forward server-mode pushes made with a fogwall-issued git credential with the pusher's linked OAuth token. Requires `oauth.enabled`. See below.        |
| `providers.<name>.oauth.deferred-forwarding` | boolean | `false`      | Acknowledge those pushes once received and forward them after approval. Requires `oauth.brokered-push`. Dashboard only. See below.                     |

**Registering a GitHub App:** account permissions needed are exactly **Email addresses (read-only)** and **Git SSH keys
(read-only)** — no others, and no private key (a GitHub App's private key is for app/installation-level auth, which this
user-to-server linking flow never uses). Callback URL:
`https://<server.service-url>/api/scm-oauth/<provider-name>/callback`, where `<provider-name>` is the top-level
`providers:` key (e.g. `github`), not the provider type.

**Registering a Forgejo/Gitea OAuth application:** self-service OAuth2 application registration under the instance's own
Settings → Applications page (works the same way on Codeberg, self-hosted Forgejo/Gitea, and org-owned applications),
requesting the `read:user` scope. Callback URL is the same shape as above.

## Brokered pushes

With `providers.<name>.oauth.brokered-push: true`, a developer can push to that provider's server-mode remote with a git
credential fogwall issues from their profile, instead of a credential of their own. fogwall authenticates the
credential, runs its usual checks, and forwards the push with the OAuth token the developer linked for that provider.
The developer then needs no write-scoped credential for the provider at all.

```yaml
providers:
  github:
    oauth:
      enabled: true
      client-id: Iv1.abc123
      client-secret-path: /run/secrets/fogwall-github-oauth-secret
      brokered-push: true

auth:
  git-credentials:
    # How long a fogwall-issued git credential works after it is issued or rotated, as an ISO-8601 duration. Unset
    # (default) for no limit. Lowering it also retires existing credentials older than the new limit.
    max-lifetime: P90D
```

- Server mode over HTTP only. The transparent proxy and SSH always use the client's own credential.
- Linking requests the provider's repository write scope while this is on: `repo` on GitHub, `write_repository` on
  GitLab, `write:repository` on Forgejo/Gitea. An account linked before the setting was turned on keeps the scopes it
  was linked with. Its pushes are refused, and it cannot be issued a credential, until the developer links it again. A
  GitHub App's tokens carry no scopes, so they are not checked; the app's own permissions apply.
- Pushes made with the developer's own credential are forwarded with that credential, as before.
- A fogwall credential presented for a provider without `brokered-push`, on a transparent-proxy remote, or to the SCM
  API proxy is refused, and never sent upstream.
- The push record names the credential that authenticated the push and the linked account it was forwarded with.

## Deferred forwarding

With `providers.<name>.oauth.deferred-forwarding: true`, a push made with a fogwall-issued git credential does not wait
for review. fogwall runs its checks, stores the push, and reports it to the client as pushed and queued. Once the push
is approved, fogwall forwards it with the developer's linked OAuth token.

```yaml
providers:
  github:
    oauth:
      enabled: true
      client-id: Iv1.abc123
      client-secret-path: /run/secrets/fogwall-github-oauth-secret
      brokered-push: true
      deferred-forwarding: true
```

- Requires `oauth.brokered-push` on the same provider and a non-zero `server.max-push-bytes`; startup fails otherwise.
- The dashboard distribution only. The standalone server refuses the setting at startup.
- Server mode over HTTP only. Pushes over SSH, pushes made with the developer's own credential, and transparent-proxy
  pushes keep the synchronous flow.
- Every such push is parked, including one that is then self-certified.
- The stored push is kept in the database until the push is forwarded, rejected or cancelled. A push whose forward fails
  ends in `ERROR` and keeps its stored push for `server.pending-push-expiry-days`, so it can be forwarded again. A push
  that is never reviewed is cancelled after the same period.
