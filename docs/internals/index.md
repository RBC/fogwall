# Internals

Working notes for contributors. These are not user or operator documentation — they record git and provider behaviour
that fogwall has to accommodate, and the reasoning behind how the code accommodates it. Read them when changing a
filter, a hook, or the SCM API proxy; skip them otherwise.

For how the system is put together, see [Architecture](../architecture/index.md).

## Contents

- [Git internals](git-internals.md) — git and JGit behaviour that constrains how filters and hooks are written
- [JGit infrastructure](jgit-infrastructure.md) — how fogwall drives JGit's server-side APIs to implement server mode
- [SCM API proxy](scm-api-proxy.md) — the wire formats each SCM CLI uses, captured from live traffic
