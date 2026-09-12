# Configuration Reference

fogwall uses layered YAML configuration merged at startup. A base file ships with the jar; additional profile files and
environment variable overrides are applied on top in a defined order.

> A section introducing a new config surface is tagged with the release it first shipped in, e.g.
> `_Available since v1.3.0._`, right under the heading. Untagged sections predate this convention — it isn't backfilled
> retroactively, only applied going forward from the section's introduction.

## Contents

- [Configuration files and profiles](files-and-profiles.md) — load order, profiles, and the bundled profile files
- [Environment variable overrides](environment-variables.md) — mapping a YAML key to a `FOGWALL_*` variable
- [Server settings](server.md) — ports, service URL, timeouts, and session persistence
- [Local mirror cache](mirror-cache.md) — the local clone fogwall keeps of each upstream repo
- [TLS](tls.md) — the HTTPS listener and trusting a custom upstream CA
- [Outbound proxy](outbound-proxy.md) — reaching upstreams through a corporate HTTP proxy
- [Database](database.md) — JDBC and MongoDB backends, pool tuning, connection strings
- [Authentication](authentication.md) — local, LDAP, Active Directory and OIDC sign-in, plus role mappings
- [Providers](providers.md) — declaring the upstream SCM hosts fogwall proxies for
- [SCM OAuth](scm-oauth.md) — linking a fogwall account to an SCM identity
- [SCM API](scm-api.md) — proxying PR/MR traffic from `gh`, `glab`, `tea` and `fj`
- [SSH transport](ssh-transport.md) — serving `git-receive-pack`/`git-upload-pack` over SSH
- [Commit validation](commit-validation.md) — author email policy and required commit trailers
- [Diff scan](diff-scan.md) — blocking literals and patterns in added lines
- [Secret scanning](secret-scanning.md) — gitleaks configuration and binary resolution
- [Binary blob detection](binary-blob-detection.md) — size and MIME-type limits on added blobs
- [Content-pattern scanning](content-pattern-scanning.md) — bundled recognizers for structured sensitive data
- [Hot reload](hot-reload.md) — which sections reload without a restart, and how to trigger one
- [Commit attribution policy](commit-attribution.md) — requiring commits to be attributed to the pushing user
- [URL rules](url-rules.md) — matching pushes by repo URL to decide what applies
- [Permissions](permissions.md) — granting users PUSH, APPROVE, PROPOSE and friends
- [Groups](groups.md) — naming a set of users once and reusing it
- [Attestations](attestations.md) — the questions a reviewer answers before approving
- [Running and logging](running-and-logging.md) — starting the process, log levels, and git client output
- [Observability](observability.md) — OpenTelemetry traces and metrics over OTLP, and trace/span log correlation
