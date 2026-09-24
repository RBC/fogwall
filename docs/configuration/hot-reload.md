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

| Section            | YAML key            | What changes take effect                                                       |
| ------------------ | ------------------- | ------------------------------------------------------------------------------ |
| `commit`           | `commit:`           | Author/committer email matchers, message block, commit attribution policy mode |
| `diff-scan`        | `diff-scan:`        | Diff content block matchers                                                    |
| `secret-scan`      | `secret-scan:`      | All gitleaks settings including `inline-config`                                |
| `binary-blob`      | `binary-blob:`      | Blob size limit and denied MIME types                                          |
| `scm-api`          | `scm-api:`          | `scm-api.block` matchers only; the rest of `scm-api` requires a restart        |
| `content-patterns` | `content-patterns:` | Enabled bundles and which content each scans                                   |
| `rules`            | `rules:`            | URL access control allow/deny rules                                            |
| `permissions`      | `permissions:`      | Config-sourced user→repo permission grants                                     |
| `attestations`     | `attestations:`     | Dashboard approval form questions                                              |

Every other section — providers, server, database, users, `scm-oauth` and the rest — requires a restart. A reload
document may still contain them; they are ignored, with a warning naming them.

## Partial reload files

A reload applies only the sections the file declares. A section the file omits keeps its current live value — it is not
reset to a default. So a reload file can carry just what it means to change:

```yaml
secret-scan:
  enabled: false
```

This file reloads secret scanning and leaves rules, commit checks and everything else as they are.

Within a declared section, the reload document is merged onto the configuration the server started with, by the same
rule as [the file layers](files-and-profiles.md#how-layers-merge): a key the document sets replaces the startup value, a
key it leaves out keeps it, and a list it sets replaces the startup list entirely.

```yaml
commit:
  message:
    block:
      - "(?i)do not merge"
```

This file replaces `commit.message.block` and leaves the startup `commit.committer`, `commit.author` and
`commit.attribution-policy` in effect.

Each reload is applied to the startup configuration, not to the previous reload. Removing a key from a section the file
still declares returns that key to its startup value; removing the whole section from the file leaves its last reloaded
value in effect until the next restart.

A value in the reload document takes priority over an environment variable setting the same key. The server logs a
warning naming each such key.

The file must still be structurally valid on its own: keys are checked against the config schema, and cross-references
(a `permissions:`/`rules:` entry naming a provider) are validated before anything is applied — a bad reference refuses
the whole reload, not just the section it appears in.

## Manual trigger

```
POST /api/config/reload                         # reload every section the source declares
POST /api/config/reload?section=commit          # commit rules only
POST /api/config/reload?section=diff-scan       # diff scan only
POST /api/config/reload?section=secret-scan     # gitleaks config only
POST /api/config/reload?section=binary-blob     # binary blob detection only
POST /api/config/reload?section=scm-api         # scm-api.block only
POST /api/config/reload?section=content-patterns # content pattern bundles only
POST /api/config/reload?section=rules           # URL rules only
POST /api/config/reload?section=permissions     # permissions only
POST /api/config/reload?section=attestations    # attestation questions only
```

The dashboard admin panel also provides a section dropdown for manual triggers.
