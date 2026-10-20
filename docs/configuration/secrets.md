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
| `auth.api-key`                         | `auth.api-key-path`                         |
| `reload.git.auth.password`             | `reload.git.auth.password-path`             |
| `scm-oauth.token-encryption-key`       | `scm-oauth.token-encryption-key-path`       |

- Either key can be set in YAML or as an [environment variable](environment-variables.md), e.g.
  `FOGWALL_DATABASE__PASSWORD_PATH`.
- Setting both keys of a pair fails startup.
- `FOGWALL_API_KEY` is a deprecated alias for `auth.api-key`, logged with a warning. Setting it alongside either
  `auth.api-key` key fails startup.
- `reload.git.auth.username` and the reload password are set together or not at all; one without the other fails
  startup.
- A file is read once at startup; surrounding whitespace, such as a trailing newline, is ignored. A file that cannot be
  read fails startup. Changing a secret takes a restart; [hot reload](hot-reload.md) does not apply these keys.
- A secret that is binary key material is given as standard base64 text, in either form. This applies to
  `scm-oauth.token-encryption-key`, which must decode to exactly 32 bytes (`openssl rand -base64 32`); any other length,
  or text that is not base64, fails startup.
- A Kubernetes Secret's `data` values are base64 only in storage: the mounted file holds the decoded value. For a base64
  key, the mounted file must hold the base64 text, so put it in `stringData` or encode it twice under `data`.

## Provenance

At startup fogwall logs which form supplied each configured secret, never its value: `file`, `environment`, or a literal
in a YAML file. A YAML literal is logged at `WARN`. A value set through a `${...}` substitution in YAML is reported as
`environment`.
