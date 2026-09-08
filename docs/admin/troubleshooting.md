# Common operational problems

## Push is rejected with "repository not permitted"

Check both layers:

1. Is the repo in `rules.allow`? Verify the slug matches exactly (including leading `/`).
2. Does the user have a `permissions` entry for this provider + path with `grant: PUSH`?

## Push hangs waiting for approval indefinitely

The server is in `ui` mode and no reviewer has approved the push. Either:

- Any authenticated user (other than the pusher) can open the push record and approve it in the dashboard.
- Or grant the pusher `SELF_CERTIFY` permission so they can approve their own clean pushes.

If `require-review-permission: true` is set, only users with an explicit `REVIEW` permission entry for the repository
can approve.

## Push blocked: identity not linked

The proxy cannot match the token to a registered proxy user. This check is always enforced — `attribution-policy` mode
does not affect it. Check:

1. Does the user's profile have an `scm-identities` entry for the correct provider?
2. Does the token have the required API scope to call `GET /user`?

## SSH push rejected: SSH key not linked to any SCM identity

The fingerprint of the connecting SSH key was not found among the keys registered on the upstream SCM for the linked SCM
identity. Possible causes:

1. The key is in fogwall (`ssh-keys`) but not on the upstream SCM account. Have the user add it in their SCM account
   settings.
2. The linked `scm-identities` entry refers to the wrong provider or username. The provider must be the SSH provider
   name (e.g. `gitea-ssh`), not the HTTP provider name (`gitea`).
3. The `api-token` is missing or has expired, causing the key lookup to return an empty list. Check the server log for
   `Failed to fetch SSH keys for ... user '...'` and renew the token.

## SSH push rejected: SSH identity verification not supported by provider

The provider used for this push does not implement SSH fingerprint lookup (e.g. `type: generic`), **and** the connecting
key is not one that OAuth linking imported. The SSH path is fail-closed — pushes are blocked unless the connecting key
can be tied to an SCM account one way or the other. Either switch to a supported provider type (`forgejo`, `gitlab`, or
`github`), or have the user link their account so their keys are imported with it. Opt-in fail-open behaviour for
unsupported providers is planned as a follow-up feature.

## SSH push rejected in strict mode for a key the user has linked

A public SSH key can only be registered to one fogwall user. If someone else's account already holds the fingerprint —
anyone can paste any public key into their own profile — the import at link time skips that key and logs:

```
Skipping SSH key from '<provider>' for user '<user>': fingerprint already registered to a different proxy user ('<owner>')
```

The link itself succeeds, so nothing tells the user their key was left out. In `scm-oauth.identity-mode: strict` the
result is a refused SSH push for the key's actual owner, since only imported keys resolve an identity there. Remove the
key from the account holding it — Users → the other account → SSH Keys — and have the owner unlink and link again to
re-import.

## Push blocked or warned: commit email mismatch

One or more commit author/committer emails are not registered to the authenticated user. This is controlled by
`attribution-policy`:

- In `strict` mode the push is blocked. Check that the user's email list includes the address they commit with
  (`git config user.email`), and that the commits were not authored by someone else.
- In `warn` mode the push goes through but the mismatch is logged and visible in the push record. Switch to `strict`
  once you are confident emails are populated for all users.

## SSH push still blocked after adding a key to the SCM account

The SSH fingerprint enricher caches results per `(provider, scm-login)` with a 7-day TTL. If a user registers a new SSH
key on their SCM account after the cache was last populated, the new fingerprint will not be visible until the entry
expires or the server restarts. To force an immediate re-fetch without a restart, the operator can reload config (if
live reload is configured) or restart the server. Future pushes from that user will populate a fresh cache entry.

The same applies when a key is removed from the SCM account — the old fingerprint remains cached until TTL expiry.

## OIDC login fails / redirect loop

1. Enable the Spring Security debug profile (`docker/log4j2-debug.xml`) — see
   [Debug profiles](logging.md#debug-profiles-by-problem-area).
2. Check the redirect URI registered in the IdP matches `https://<your-host>/login/oauth2/code/fogwall` exactly.
3. For Entra ID: make sure `issuer-uri` ends in `/v2.0` and `skip-user-info: true` is set — see the
   [Entra ID section of the configuration reference](../configuration/authentication.md#entra-id-azure-ad).
   `jwk-set-uri` is **not** needed for Entra: it is a plain endpoint override and no longer changes how tokens are
   validated.

## OIDC login fails with "Claim '…' not present in the ID token"

The configured `auth.oidc.user-name-attribute` names a claim your IdP did not include. The OIDC spec guarantees only
`sub` in an ID token — `email` and the rest are voluntary, and IdPs differ in what they send (Entra ID, for example,
omits `email` unless the optional claim is added to the app registration). Either configure the IdP to include the
claim, or point `user-name-attribute` at one it actually sends. The warning in the application log lists the claims that
were present.

## Upgrading from a pre-1.2.0 deployment: OIDC redirect URI mismatch (AADSTS50011)

The project was renamed in 1.2.0, which changed the Spring Security OAuth2 registration ID from `gitproxy` to `fogwall`.
This shifts the callback URL that fogwall sends to the IdP in the authorization request:

| Version | Redirect URI sent to IdP                    |
| ------- | ------------------------------------------- |
| < 1.2.0 | `https://<host>/login/oauth2/code/gitproxy` |
| ≥ 1.2.0 | `https://<host>/login/oauth2/code/fogwall`  |

**Fix:** add the new URI to your IdP app registration alongside the existing one. In Entra ID: App registrations → your
app → Authentication → add `https://<host>/login/oauth2/code/fogwall` as a redirect URI. Both URIs can coexist — remove
the old one once all deployments are on 1.2.0+.

## Upgrading from a pre-1.4.0 deployment: `server.service-url` no longer includes `/dashboard`

Before 1.4.0, `server.service-url` was expected to already carry whatever path prefix your reverse proxy or load
balancer put the dashboard behind (typically `https://<host>/dashboard`), and fogwall concatenated routes directly onto
it. As of 1.4.0 (introduced alongside SCM OAuth account linking, #40, which needs to build a callback URL for a REST
endpoint that isn't under the dashboard's own path) `service-url` must be the bare origin instead — fogwall appends
`/dashboard`, `/api`, etc. itself.

**Fix:** if your existing `service-url` ends in `/dashboard` (or any other path), drop that suffix. This is not optional
to skip — leaving the old value in place means push-record links and the "identity not linked" hint in sideband messages
point at the wrong path (`.../dashboard/dashboard/push/<id>`, a 404), and OAuth linking's callback URL registered with
your GitHub App/GitLab OAuth app won't match what fogwall actually sends.

## Gitleaks produces no output / scan appears to be skipped

Check `logs/application.log` for lines containing `gitleaks`. The log will show which binary path was resolved and
whether the scan ran. If the binary cannot be executed (permission denied, noexec mount), the proxy falls back to
skipping the scan rather than failing the push — add `gitleaks` to PATH or set `scanner-path` explicitly.

## Push fails after approval with an upstream error (404, 403, etc.)

Once a push passes validation and is approved, the proxy forwards it to the upstream SCM transparently — no further
processing occurs. Any error from the upstream is passed straight back to the git client exactly as if the developer
were pushing directly.

Common upstream errors and their causes:

| Error                                    | Likely cause                                                                                                                                                                                                                |
| ---------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Repository not found` / 404             | The token does not have access to the repository. GitHub returns 404 (not 403) for both missing repos and insufficient permissions on private repos — this is intentional on GitHub's part to avoid leaking repo existence. |
| `403 Forbidden`                          | The token has repo access but lacks the required write scope (e.g. a fine-grained PAT missing `Contents: write`).                                                                                                           |
| `pre-receive hook declined`              | The upstream has its own server-side hooks that rejected the push. Nothing the proxy can do — the developer needs to resolve it upstream.                                                                                   |
| `remote: error: GH006: Protected branch` | The target branch has branch protection rules on the upstream. Again, upstream-side — not a proxy issue.                                                                                                                    |

These errors appear in the developer's terminal and in the push record in the dashboard. They are not logged as proxy
errors — from the proxy's perspective the forwarding succeeded.

**Diagnosing token scope issues:** if a push consistently fails with 404 or 403 immediately after approval, ask the
developer to test the same push directly (bypassing the proxy) with the same token. If it also fails direct, the problem
is the token — not the proxy.

## `Permission denied` on startup in a container

JGit failed to write to `$HOME` or `/tmp`. Verify:

```bash
docker exec <container> sh -c 'ls -la $HOME && touch $HOME/.probe && rm $HOME/.probe'
docker exec <container> sh -c 'touch /tmp/.probe && rm /tmp/.probe'
```

If either fails, see [JGit filesystem requirements](filesystem-requirements.md) above.
