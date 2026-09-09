# Providers

Providers define the upstream Git hosting services the proxy routes to.

```yaml
providers:
  # Reserved names — provider type and default URI are built in
  github:
    enabled: true # → github.com
  gitlab:
    enabled: true # → gitlab.com
  bitbucket:
    enabled: true # → bitbucket.org
  codeberg:
    enabled: true # → codeberg.org
  gitea:
    enabled: true # → gitea.com

  # Custom-named providers — 'type' and 'uri' are both required
  my-internal-server:
    enabled: true
    type: github # uses GitHubProvider (identity resolution, GHES API path logic, etc.)
    uri: https://github.corp.example.com

  my-forgejo:
    enabled: true
    type: forgejo # ForgejoProvider; uri is required (forgejo has no canonical public host)
    uri: https://forge.internal.example.com

  acme-bitbucket:
    enabled: true
    type: bitbucket
    uri: https://bitbucket.acme.com
```

## Provider properties

| Property                   | Type    | Default                | Description                                                                                                                                                                                                                                                                                                                                |
| -------------------------- | ------- | ---------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `enabled`                  | boolean | `true`                 | Whether the provider is active                                                                                                                                                                                                                                                                                                             |
| `servlet-path`             | string  | `""`                   | Additional URL prefix for this provider                                                                                                                                                                                                                                                                                                    |
| `uri`                      | string  | _(built-in default)_   | Upstream base URI. Required for custom-named providers; omit for built-ins.                                                                                                                                                                                                                                                                |
| `type`                     | string  | _(from name)_          | Provider implementation: `github`, `gitlab`, `bitbucket`, `codeberg`, `forgejo`, `gitea`. Required for any name that is not one of the five reserved names.                                                                                                                                                                                |
| `api-uri`                  | string  | _(derived from `uri`)_ | HTTP base URI for provider REST API calls (identity resolution, SSH key lookup). Only needed when the HTTP API port can't be derived from `uri` — e.g. a self-hosted instance where the HTTP API runs on a non-standard port.                                                                                                              |
| `api-token`                | string  | _(none)_               | PAT used when the provider's SSH key listing API requires authentication (Forgejo/GitLab with `REQUIRE_SIGNIN_VIEW=true`). GitHub's equivalent endpoint is public and needs no token.                                                                                                                                                      |
| `ssh`                      | block   | _(disabled)_           | SSH transport for this provider — the same entry serves both HTTP and SSH. See [Serving a provider over SSH](ssh-transport.md#serving-a-provider-over-ssh).                                                                                                                                                                                |
| `blocked-info-refs-status` | int     | `403`                  | HTTP status returned when a blocked `/info/refs` discovery request is denied. `403` is unambiguous; `404` obscures whether the repo exists (security by obscurity).                                                                                                                                                                        |
| `serve-fetch`              | boolean | _(inherits global)_    | Per-provider override for whether server mode serves clone/fetch from the local mirror (both transports). Omit to inherit the global [`server.serve-fetch`](server.md); set `true`/`false` to override for this provider only.                                                                                                             |
| `issues-enabled`           | boolean | `false`                | Whether the dashboard issue form may create, edit, comment on and close/reopen issues on this provider on a user's behalf. Off by default; needs no listener or port, but requires the user to have linked their account via OAuth. See [Filing issues from the dashboard](../admin/proposals.md#filing-issues-from-the-dashboard-no-cli). |

The five reserved names (`github`, `gitlab`, `bitbucket`, `codeberg`, `gitea`) carry a built-in default URI and provider
type. Any other name is opaque — the name is never parsed for type hints — so `type` and `uri` must both be set. The
typed provider supplies API URL logic, identity resolution, and (for Bitbucket) credential rewriting; `uri` overrides
only the upstream address.

## Bitbucket identity resolution

Bitbucket does not enforce the git push username — only the token is validated. To enable identity resolution (required
for push permission checks and commit identity verification), the proxy adopts the convention that the **HTTP Basic-auth
username in the remote URL must be the user's Bitbucket account email address**.

Configure the remote URL like this:

```bash
https://<email>:<api-token>@bitbucket.org/<workspace>/<repo>.git
```

The proxy calls `GET /2.0/user` using those credentials to look up the user's Bitbucket `username` (the auto-generated
URL-safe identifier, e.g. `a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6`). It then rewrites the outbound credentials to
`username:token` before forwarding the push to Bitbucket — this is necessary because Bitbucket's git endpoint only
accepts the internal username, not an email address.

**Required API token scopes:** `read:user:bitbucket` and `write:repository:bitbucket`.

<!-- prettier-ignore-start -->
> [!TIP]
> **M&A / private server use case:** This same mechanism works for self-hosted Bitbucket Data Center instances. Set `uri` to your internal Bitbucket URL and the proxy will route and rewrite credentials accordingly, making it straightforward to gate pushes to acquired-company repositories during an integration period.
<!-- prettier-ignore-end -->
