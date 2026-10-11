# Secrets

_Available since v1.5.0._

Each sensitive key takes either its value (`<key>`) or the path to a file holding it (`<key>-path`). Prefer the file
form: a mounted secret file stays out of the process environment, pod specs and `docker inspect`, and is not inherited
by child processes.

```yaml
database:
  password-path: /run/secrets/fogwall-db-password
providers:
  github:
    api-token-path: /run/secrets/fogwall-github-token
```

| Value key                              | File key                                    |
| -------------------------------------- | ------------------------------------------- |
| `database.password`                    | `database.password-path`                    |
| `server.redis.password`                | `server.redis.password-path`                |
| `server.tls.keystore.password`         | `server.tls.keystore.password-path`         |
| `server.outbound-proxy.auth.password`  | `server.outbound-proxy.auth.password-path`  |
| `providers.<name>.api-token`           | `providers.<name>.api-token-path`           |
| `providers.<name>.oauth.client-secret` | `providers.<name>.oauth.client-secret-path` |
| `auth.oidc.client-secret`              | `auth.oidc.client-secret-path`              |
| `auth.ldap.bind-password`              | `auth.ldap.bind-password-path`              |
| `auth.ad.bind-password`                | `auth.ad.bind-password-path`                |
| `scm-oauth.token-encryption-key`       | `scm-oauth.token-encryption-key-path`       |

- Either key can be set in YAML or as an [environment variable](environment-variables.md), e.g.
  `FOGWALL_DATABASE__PASSWORD_PATH`.
- Setting both keys of a pair fails startup.
- A file is read once at startup; surrounding whitespace, such as a trailing newline, is ignored. A file that cannot be
  read fails startup. Changing a secret takes a restart; [hot reload](hot-reload.md) does not apply these keys.
- `scm-oauth.token-encryption-key` and its file accept 32 raw bytes or the base64 encoding of 32 bytes (44 characters).
  A 32-byte file is read as raw bytes, as is. A configured key in neither format fails startup.

## Provenance

At startup fogwall logs which form supplied each configured secret, never its value: `file`, `environment`, or a literal
in a YAML file. A YAML literal is logged at `WARN`. A value set through a `${...}` substitution in YAML is reported as
`environment`.

## Requiring file sourcing

```yaml
secrets:
  require-file-sourcing: true
```

| Property                        | Type    | Default | Description                                                                                         |
| ------------------------------- | ------- | ------- | --------------------------------------------------------------------------------------------------- |
| `secrets.require-file-sourcing` | boolean | `false` | Refuse to start when any secret in the table above is set by its value key instead of its file key. |
