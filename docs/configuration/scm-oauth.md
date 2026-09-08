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

| Property                                    | Type    | Default      | Description                                                                                                      |
| ------------------------------------------- | ------- | ------------ | ---------------------------------------------------------------------------------------------------------------- |
| `identity-mode`                             | string  | `permissive` | `permissive` or `strict` — see above.                                                                            |
| `token-encryption-key-path`                 | string  | _(none)_     | Path to the base64-encoded 32-byte AES-256-GCM key file. Auto-generated under `./.data/` for local dev if unset. |
| `providers.<name>.oauth.enabled`            | boolean | `false`      | Whether "Link via OAuth" is offered for this provider.                                                           |
| `providers.<name>.oauth.client-id`          | string  | `""`         | OAuth app/client ID.                                                                                             |
| `providers.<name>.oauth.client-secret-path` | string  | `""`         | Path to a file holding the OAuth app/client secret.                                                              |

**Registering a GitHub App:** account permissions needed are exactly **Email addresses (read-only)** and **Git SSH keys
(read-only)** — no others, and no private key (a GitHub App's private key is for app/installation-level auth, which this
user-to-server linking flow never uses). Callback URL:
`https://<server.service-url>/api/scm-oauth/<provider-name>/callback`, where `<provider-name>` is the top-level
`providers:` key (e.g. `github`), not the provider type.

**Registering a Forgejo/Gitea OAuth application:** self-service OAuth2 application registration under the instance's own
Settings → Applications page (works the same way on Codeberg, self-hosted Forgejo/Gitea, and org-owned applications),
requesting the `read:user` scope. Callback URL is the same shape as above.
