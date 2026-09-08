# Diff scan

Push-level check applied once per push against the aggregate diff (all commits combined). Only added lines (`+`) are
scanned — deletions and context lines are ignored.

```yaml
diff-scan:
  block:
    literals:
      - "internal.corp.example.com"
    patterns:
      - '(?i)https?://[a-z0-9.-]*\.corp\.example\.com\b'
```
