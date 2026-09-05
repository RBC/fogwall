---
paths:
  - "**/*Settings.java"
  - "**/src/main/resources/**/*.yml"
  - "**/src/main/resources/**/*.yaml"
  - "docker/**"
  - "docs/CONFIGURATION.md"
---

# Configuration conventions

- **YAML config is user-facing.** Its audience is admins, operators, policy authors, and developers (including plugin
  and extension authors). Tuning knobs for JGit, Jetty, and other internals that only matter in specific deployment
  scenarios do not go through the YAML/Gestalt loader; expose them as documented environment variables an operator
  _could_ set but isn't expected to know about (precedent: the server-tuned JGit pack-window cache).
- **`*Settings` POJOs default fully inert** — `enabled = false`, empty lists, no limits. Real defaults live only in the
  shipped YAML files (`SecretScanSettings` is the precedent).
- **One global config key before per-provider or per-entity variants**, until someone actually asks for the granularity.
- **Renaming or removing a key needs a deprecation path** — accept old and new for at least one minor release. See
  "Backwards compatibility" in CLAUDE.md.
- Every new or changed key gets documented in [docs/CONFIGURATION.md](../../docs/CONFIGURATION.md) in the same PR.
