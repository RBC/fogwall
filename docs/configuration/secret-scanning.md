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

  # timeout-seconds: 30
```
