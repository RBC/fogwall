# Authentication

The dashboard supports four authentication providers, selected via `auth.provider`.

```yaml
auth:
  provider: local # local | ldap | ad | oidc (default: local)

  # Maximum idle time before a session expires and the user must re-authenticate.
  # Default: 86400 (24 hours). Tighten to 28800 (8 hours) or less for compliance environments.
  session-timeout-seconds: 86400
```

## Local (default)

Usernames and BCrypt password hashes are defined directly in the `users:` block. See
[Provisioning users](../admin/user-accounts.md#how-users-are-provisioned-per-backend) in the administrator guide.

## LDAP

Authenticates users against a generic LDAP directory using a bind operation.

```yaml
auth:
  provider: ldap
  ldap:
    # LDAP server URL including base DN.
    url: ldap://ldap.example.com:389/dc=example,dc=com

    # User DN pattern — {0} is substituted with the login username.
    user-dn-patterns: cn={0},ou=users

    # Optional bind credentials for group search / attribute lookup.
    bind-dn: cn=admin,dc=example,dc=com
    bind-password: secret

    # Base DN (relative to url base) to search for group membership.
    # When set, group names are mapped to roles via auth.role-mappings below.
    group-search-base: ou=groups

    # LDAP filter for group membership. {0} = user full DN, {1} = username.
    group-search-filter: "(member={0})"

  # Map fogwall role names to lists of LDAP group CNs.
  # When a user is a member of any listed group, the role is granted.
  role-mappings:
    ADMIN:
      - git-admins
      - security-team
```

## Active Directory

Authenticates users against an on-premises Active Directory domain using UPN bind (`user@domain.com`). Unlike the
generic LDAP provider, no `user-dn-patterns` is required — Spring Security constructs the UPN automatically from the
`domain` and the submitted username.

```yaml
auth:
  provider: ad
  ad:
    # AD domain name — used to form user@domain UPN for bind.
    domain: corp.example.com

    # Domain controller URL. When omitted, Spring Security resolves the DC via DNS SRV records.
    url: ldap://dc.corp.example.com:389

    # Optional: base DN for group search. When set, group membership is used for role mapping.
    group-search-base: DC=corp,DC=example,DC=com

    # LDAP filter for group membership. {0} = user full DN.
    group-search-filter: "(member={0})"

  role-mappings:
    ADMIN:
      - CN=git-admins,OU=Groups,DC=corp,DC=example,DC=com
```

<!-- prettier-ignore-start -->
> [!TIP]
> The AD provider understands Active Directory error sub-codes on bind failure 49 (expired passwords, locked accounts, etc.) and maps them to specific Spring Security exceptions.
<!-- prettier-ignore-end -->

## OIDC

Authenticates users via OpenID Connect authorization code flow (Keycloak, Okta, Entra ID, Dex, etc.).

```yaml
auth:
  provider: oidc
  oidc:
    # OIDC issuer URI — Spring Security fetches {issuerUri}/.well-known/openid-configuration at startup.
    issuer-uri: https://accounts.example.com

    client-id: fogwall-client
    client-secret: fogwall-secret

    # Optional: path to a PKCS#8 PEM RSA private key for private_key_jwt client auth.
    # When set, client-secret is not required.
    # private-key-path: /run/secrets/fogwall-oidc-private-key.pem

    # Optional: path to the X.509 certificate (PEM) matching private-key-path.
    # Required for Entra ID — Entra matches registered certificates by x5t thumbprint, not kid.
    # Without this, every token exchange fails with AADSTS700027.
    # Generate: openssl req -new -x509 -key private.pem -out cert.pem -days 365
    # cert-path: /run/secrets/fogwall-oidc-cert.pem

    # Optional: explicit kid to embed in the private_key_jwt assertion header.
    # Use this for providers that match the assertion against a registered JWKS by kid
    # (Keycloak, Okta, Auth0, Dex). Without it, a random UUID kid is generated on each
    # restart, which breaks authentication with those providers.
    # Find the kid by inspecting your provider's JWKS endpoint and matching it to the
    # public key you registered.
    # Not needed when cert-path is set (Entra ID uses x5t instead of kid).
    # key-id: my-registered-kid

    # Claim used as the principal name (the fogwall username). Defaults to "sub" — the only
    # claim the OIDC spec guarantees in every ID token. Claims like preferred_username and
    # email are voluntary: point this at one only if your IdP actually sends it, otherwise
    # login fails with a clear "claim not present" error.
    # user-name-attribute: email

    # Read all claims from the ID token and never call the UserInfo endpoint. Needed when
    # user-name-attribute names a claim your IdP's UserInfo response omits (see the Entra ID
    # section below); harmless otherwise.
    # skip-user-info: true

    # Endpoint overrides. OIDC discovery ALWAYS runs at startup against issuer-uri; these
    # replace individual discovered endpoints for split-egress setups (e.g. an internal JWKS
    # mirror). They have no effect on token validation.
    # authorization-uri: ...
    # token-uri: ...
    # user-info-uri: ...
    # jwk-set-uri: ...

  # OIDC claim containing the user's group memberships. Defaults to "groups",
  # which is standard for Keycloak, Okta, and most Entra ID configurations.
  groups-claim: groups

  # Map fogwall role names to lists of OIDC group values from the claim above.
  role-mappings:
    ADMIN:
      - git-admins
```

### Entra ID (Azure AD)

Entra ID works with standard OIDC discovery and full stock token validation — no bypass and no endpoint overrides. What
it does need is two settings that account for its unusual (but spec-conformant — all profile/email claims are voluntary,
only `sub` is guaranteed) **claims** behaviour:

- **`user-name-attribute: email`** — with the `email` optional claim added to the app registration (checklist below).
  Don't skip this one: the default (`sub`) logs in without any error, but Entra's `sub` is an opaque generated string,
  so every downstream record — permissions, push history, the admin UI — ends up keyed to an unreadable identifier.
  Nothing breaks loudly; it is just miserable to operate.
- **`skip-user-info: true`** (recommended). Entra's UserInfo endpoint is Microsoft Graph, which returns HTTP 200 with a
  minimal claim set and has historically broken Spring's principal construction when `user-name-attribute` names a claim
  its response lacks. Skipping UserInfo reads all claims from the ID token and drops the runtime dependency on Graph
  (and its `User.Read` permission) entirely.

```yaml
auth:
  provider: oidc
  oidc:
    # The /v2.0 suffix matters: it selects the v2 endpoints, and ID-token format follows the
    # endpoint — v2 tokens carry iss=https://login.microsoftonline.com/{tenant-id}/v2.0, which
    # matches discovery and validates cleanly.
    issuer-uri: https://login.microsoftonline.com/{tenant-id}/v2.0
    client-id: <app-registration-client-id>
    client-secret: <client-secret>
    skip-user-info: true
    user-name-attribute: email

  # Requires "Group claims" to be enabled in the app registration (Token configuration → Groups claim).
  # Group values will be object IDs (GUIDs) unless "Group names" is selected in the manifest.
  groups-claim: groups

  role-mappings:
    ADMIN:
      - <object-id-of-admin-group>
```

<!-- prettier-ignore-start -->
> [!IMPORTANT]
> **App registration checklist:**
> 1. Platform: Web — redirect URI `https://<your-host>/login/oauth2/code/fogwall`
> 2. API permissions: `openid`, `profile`, `email` (delegated)
> 3. Token configuration → add Groups claim → select "Security groups"
> 4. Token configuration → add optional claim → ID token → `email` (feeds fogwall's locked-email provisioning)
<!-- prettier-ignore-end -->

**Legacy v1-token tenants.** An app registration reached through the v1 endpoints issues v1-format tokens with
`iss=https://sts.windows.net/{tenant-id}/`, which fails validation against the v2 discovery issuer. The fix is to use
the v2 issuer URI above. If your tenant genuinely cannot, point `issuer-uri` directly at
`https://sts.windows.net/{tenant-id}/` — it serves a self-consistent discovery document — and set `user-name-attribute`
to a claim v1-format tokens actually carry (check a decoded token; `upn` is the usual candidate).

To tell which case you are in, decode an ID token from a real login and check its `ver` claim (`"1.0"` or `"2.0"`) —
that is authoritative. Don't infer it from the app manifest: Microsoft documents `requestedAccessTokenVersion` as
applying to access tokens issued to your app when it acts as an API, and in deployments verified so far a tenant with
that setting unset still issued v2 ID tokens through the `/v2.0` endpoints — but tenant configurations vary, so check
the token.

## Role mappings

`auth.role-mappings` applies to LDAP, AD, and OIDC. Keys are role names (without the `ROLE_` prefix); values are lists
of group names or claim values from the IdP.

| Role    | Dashboard access                                                               |
| ------- | ------------------------------------------------------------------------------ |
| `USER`  | View and act on pushes awaiting approval                                       |
| `ADMIN` | All USER permissions + create/delete users, reset passwords, manage identities |

When `role-mappings` is empty, the operator has not configured group-based access control: `ROLE_USER` is granted to
every authenticated user (open mode). When `role-mappings` is non-empty, access is **deny-by-default** — a user whose
IdP groups don't match any mapping authenticates successfully against the directory/IdP but is refused access by
fogwall.

```yaml
auth:
  role-mappings:
    ADMIN:
      - git-admins
  # Deny-by-default is the correct posture for regulated environments and is the default.
  # Set to false to treat the IdP purely as an authentication mechanism (SSO convenience): any
  # user who authenticates successfully is granted ROLE_USER even if no group mapping matches.
  # role-mappings (if present) then only grant additional roles on top. No-op when role-mappings
  # is empty, since open mode is already the behaviour in that case.
  require-role-mapping: true
```
