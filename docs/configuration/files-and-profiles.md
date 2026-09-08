# Configuration files and profiles

## Load order (lowest → highest priority)

| Layer | Source                              | When loaded                                          |
| ----- | ----------------------------------- | ---------------------------------------------------- |
| 1     | `fogwall.yml`                       | Always — base defaults bundled in the jar            |
| 2     | `fogwall-{profile}.yml`             | For each profile listed in `FOGWALL_CONFIG_PROFILES` |
| 3     | Environment variables (`FOGWALL_*`) | Always — highest priority                            |

## `FOGWALL_CONFIG_PROFILES`

Set this environment variable to a comma-separated list of profile names. For each name, fogwall looks for
`fogwall-{name}.yml` on the classpath (including any files mounted into `/app/conf/` in Docker). Unknown or missing
profile files are silently skipped.

```bash
# Local development — loads fogwall-local.yml
FOGWALL_CONFIG_PROFILES=local

# Docker with LDAP auth — loads fogwall-docker-default.yml then fogwall-ldap.yml
FOGWALL_CONFIG_PROFILES=docker-default,ldap

# Docker with OIDC auth and PostgreSQL
FOGWALL_CONFIG_PROFILES=docker-default,oidc
# (postgres settings come from FOGWALL_DATABASE_* env vars, no profile file needed)
```

Later profiles take priority over earlier ones. All profiles take priority over `fogwall.yml`. Environment variables
override everything.

## Bundled profiles

| Profile name     | File                         | Purpose                                                   |
| ---------------- | ---------------------------- | --------------------------------------------------------- |
| `local`          | `fogwall-local.yml`          | Local development: dev users, Vite CORS, test allow rules |
| `docker-default` | `fogwall-docker-default.yml` | Docker base: admin user, Gitea provider, validation rules |
| `ldap`           | `fogwall-ldap.yml`           | LDAP authentication config (used with `docker-default`)   |
| `oidc`           | `fogwall-oidc.yml`           | OIDC authentication config (used with `docker-default`)   |

> When running via `./gradlew run`, `FOGWALL_CONFIG_PROFILES=local` is set automatically. In Docker, set it explicitly
> via the Compose file or your deployment config.

## Docker Compose

The Docker Compose setup uses overlay files to compose the stack. See
[docker/docker-compose.ldap.yml](https://github.com/RBC/fogwall/blob/main/docker/docker-compose.ldap.yml) and
[docker/docker-compose.oidc.yml](https://github.com/RBC/fogwall/blob/main/docker/docker-compose.oidc.yml) for examples
of how profiles are combined.

```bash
# Default (local auth, h2 database)
docker compose up -d

# LDAP auth
docker compose -f docker/docker-compose.yml -f docker/docker-compose.ldap.yml up -d

# OIDC auth + PostgreSQL
docker compose --profile postgres \
  -f docker/docker-compose.yml -f docker/docker-compose.oidc.yml -f docker/docker-compose.postgres.yml up -d
```
