# Hot reload

Selected config sections can be reloaded at runtime without restarting the server. Two reload sources are supported:

- **File watch** — monitors a local YAML file; triggers automatically on modification
- **Git source** — periodically pulls a git repository and reads a YAML overlay from it

```yaml
reload:
  file:
    enabled: false
    path: /app/conf/fogwall-local.yml # watched for modifications
  git:
    enabled: false
    url: https://github.com/myorg/config.git
    branch: main
    file-path: fogwall.yml
    interval-seconds: 300 # 0 = manual trigger only
```

### Git source authentication

For private repositories, set these environment variables — no config file changes needed:

```
FOGWALL_RELOAD_GIT_AUTH_USERNAME=<username or token placeholder>
FOGWALL_RELOAD_GIT_AUTH_PASSWORD=<personal access token or password>
```

Both variables must be set together; if only one is present a warning is logged and the clone/pull proceeds without
credentials. For token-only auth (GitHub, GitLab, Gitea PATs) the username can be any non-empty string — `git` or
`x-token` are common placeholders.

## Reloadable sections

| Section        | YAML key        | What changes take effect                                                |
| -------------- | --------------- | ----------------------------------------------------------------------- |
| `commit`       | `commit:`       | Author email rules, message block lists, commit attribution policy mode |
| `diff-scan`    | `diff-scan:`    | Diff content block literals and patterns                                |
| `secret-scan`  | `secret-scan:`  | All gitleaks settings including `inline-config`                         |
| `binary-blob`  | `binary-blob:`  | Blob size limit and denied MIME types                                   |
| `rules`        | `rules:`        | URL access control allow/deny rules                                     |
| `permissions`  | `permissions:`  | Config-sourced user→repo permission grants                              |
| `attestations` | `attestations:` | Dashboard approval form questions                                       |

Provider, server, database and `scm-oauth` sections always require a restart — they describe how the deployment is set
up rather than policy that changes over time.

## Manual trigger

```
POST /api/config/reload                         # reload all sections
POST /api/config/reload?section=commit          # commit rules only
POST /api/config/reload?section=diff-scan       # diff scan only
POST /api/config/reload?section=secret-scan     # gitleaks config only
POST /api/config/reload?section=binary-blob     # binary blob detection only
POST /api/config/reload?section=rules           # URL rules only
POST /api/config/reload?section=permissions     # permissions only
POST /api/config/reload?section=attestations    # attestation questions only
```

The dashboard admin panel also provides a section dropdown for manual triggers.
