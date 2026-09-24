# Configuration files and profiles

## Load order (lowest → highest priority)

| Layer | Source                              | When loaded                                          |
| ----- | ----------------------------------- | ---------------------------------------------------- |
| 1     | Bundled defaults                    | Always — inside the jar                              |
| 2     | `fogwall.yml`                       | When present on the classpath — no profile name      |
| 3     | `fogwall-{profile}.yml`             | For each profile listed in `FOGWALL_CONFIG_PROFILES` |
| 4     | Environment variables (`FOGWALL_*`) | Always                                               |
| 5     | [Hot reload](hot-reload.md)         | When a reload source is configured                   |

## How layers merge

A mapping merges key by key. A list or a scalar is replaced by the highest layer that sets it.

```yaml
# bundled defaults
providers:
  github:
    enabled: true
  gitlab:
    enabled: true
binary-blob:
  deny-mime-types:
    - application/pdf
    - application/zip

# fogwall-prod.yml
providers:
  gitlab:
    enabled: false
binary-blob:
  deny-mime-types:
    - application/x-msdownload
```

With these two layers, `providers` merges key by key: `github` stays enabled and `gitlab` is disabled. `deny-mime-types`
is a list, so the profile's list replaces the default one: only `application/x-msdownload` is denied, and
`application/pdf` and `application/zip` are not. Setting `deny-mime-types: []` would deny nothing.

If the profile had `deny-mime-types:` with no value, the key would count as unset and the default list would stay.

## `FOGWALL_CONFIG_PROFILES`

Set this environment variable to a comma-separated list of profile names. For each name, fogwall looks for
`fogwall-{name}.yml` on the classpath (including any files mounted into `/app/conf/` in Docker). A name with no matching
file fails startup, naming the file it wanted — fogwall does not start on base defaults with the profile missing.

```bash
# Local development — loads fogwall-local.yml
FOGWALL_CONFIG_PROFILES=local

# Docker with LDAP auth — loads fogwall-docker-default.yml then fogwall-ldap.yml
FOGWALL_CONFIG_PROFILES=docker-default,ldap

# Docker with OIDC auth and PostgreSQL
FOGWALL_CONFIG_PROFILES=docker-default,oidc
# (postgres settings come from FOGWALL_DATABASE_* env vars, no profile file needed)
```

Later profiles take priority over earlier ones, and all profiles over `fogwall.yml`.

**A profile name resolves to exactly one file.** fogwall takes the first `fogwall-{name}.yml` on the classpath; a second
file of the same name further along is not merged in, it is never read. Profiles meant to compose therefore need
distinct names — which is why the dashboard's local settings are `fogwall-dashboard.yml` rather than a second
`fogwall-local.yml`.

## Where profiles come from

Only the defaults are bundled in the jar; everything else layers on top of them. `fogwall.yml` and every profile are
files supplied from outside the jar, so nothing that configures a particular deployment — users, credentials, hosts — is
ever packaged into an artifact that ships somewhere else. To turn off something the defaults enable, set it off in your
own file (`enabled: false`) rather than leaving it out.

| Profile name     | File                         | Supplied by                                           |
| ---------------- | ---------------------------- | ----------------------------------------------------- |
| `local`          | `fogwall-local.yml`          | `config/` in a clone; the developer's own, gitignored |
| `dashboard`      | `fogwall-dashboard.yml`      | `config/` in a clone; what only the dashboard reads   |
| `docker-default` | `fogwall-docker-default.yml` | Mounted into `/app/conf/` by Docker Compose           |
| `ldap`           | `fogwall-ldap.yml`           | Mounted into `/app/conf/`, used with `docker-default` |
| `oidc`           | `fogwall-oidc.yml`           | Mounted into `/app/conf/`, used with `docker-default` |

In a container, mount `fogwall.yml` and each profile into `/app/conf/` — that directory is on the classpath ahead of the
application's own jars.

> When running via `./gradlew run`, `FOGWALL_CONFIG_PROFILES=local` is set automatically and `config/` is put on the
> classpath. In Docker, set it explicitly via the Compose file or your deployment config.

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
