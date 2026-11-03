# Secret scanning

Secret scanning via gitleaks (<https://github.com/gitleaks/gitleaks>). Applied once per push. The JAR ships with a
bundled gitleaks binary so scanning works out of the box.

Binary resolution order (first match wins):

1. `scanner-path` — explicit path, bypasses everything else
2. `version` + `auto-install: true` — downloads and caches that version on startup
3. Bundled JAR binary (default version, always present)
4. System `PATH`

```yaml
secret-scan:
  enabled: false
  # version: 8.22.0
  # auto-install: true
  # install-dir: ~/.cache/fogwall/gitleaks
  # scanner-path: /usr/local/bin/gitleaks

  # External TOML rules file. Ignored when inline-config is set.
  # config-file: /app/conf/.gitleaks.toml

  # Inline TOML config — takes precedence over config-file. Hot-reloadable via
  # POST /api/config/reload?section=secret-scan. Content must be valid gitleaks TOML:
  # inline-config: |
  #   title = "my-org"
  #   [extend]
  #   useDefault = true
  #   [[rules]]
  #   id = "my-org-api-key"
  #   regex = '''MY_ORG_[A-Z0-9]{32}'''

  # Also scan commit messages and annotated tag messages, with the same rules.
  # include-metadata: false

  # timeout-seconds: 30
```

## Commit and tag messages

gitleaks scans patch content only. With `include-metadata: true`, fogwall also scans the message of every commit in the
push and the message of a pushed annotated tag, in one extra gitleaks run per push, in both proxy modes and over SSH. A
finding names the message it was found in (`message of commit abc1234`, `message of tag v1.2.0`) instead of a file and
line, and blocks the push like any other secret. The secret is redacted from the stored push record.

The key defaults to `false`. To preview what it would block, run the same rules over existing history:

```shell
git log --all --format=%B | gitleaks detect --pipe --config /app/conf/.gitleaks.toml
```
