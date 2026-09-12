# Architecture

fogwall is a Git push proxy that sits between developers and upstream Git hosting providers (GitHub, GitLab, Bitbucket,
Forgejo, etc.). Every push travels through a validation and approval pipeline before reaching the upstream remote.
Fetch/clone traffic is audited but not blocked.

If you're familiar with [finos/git-proxy](https://github.com/finos/git-proxy), the Java rewrite shares the same
conceptual model: an ordered chain of steps that inspect and act on each push, a push store for audit and approval
state, and pluggable providers for different Git hosts. The main structural difference is that fogwall offers two
distinct proxy modes with different tradeoffs.

## Contents

- [Project structure](project-structure.md) — the Gradle modules and how they depend on each other
- [Two proxy modes](proxy-modes.md) — server mode, transparent proxy, and the SCM API listeners
- [Request flow](request-flow.md) — what happens to a push, step by step, in each mode
- [Validation pipeline](validation-pipeline.md) — the ordered chain of checks a push runs through
- [Core abstractions](core-abstractions.md) — provider, push store, approval gateway, user store
- [Deployment modes](deployment-modes.md) — proxy only, proxy plus dashboard, and Docker
- [Advanced use cases](advanced-use-cases.md) — private-to-private proxying and credential rewriting
- [What this architecture enables](what-this-enables.md) — the use cases server mode's full pack ownership opens up
