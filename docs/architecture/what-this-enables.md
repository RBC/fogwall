# What this architecture enables

The transparent proxy mode replicates what finos/git-proxy does today: intercept, inspect, and forward. The server mode
— where the proxy owns the full pack lifecycle via JGit — opens up use cases that are not possible with a pass-through
HTTP proxy:

- **Deferred forwarding** — the developer's push is received and acknowledged immediately. The pack is stored locally
  while an approval process runs (hours, days); forwarding happens asynchronously once approved. This eliminates the
  problem of holding a git client session open during a long review window. Note: the current implementation forwards
  within the same session using the client's in-memory credentials (see
  [Credential flow](../internals/jgit-infrastructure.md#credential-flow)); true async deferred forwarding would require
  a separate credential design and is tracked as a backlog item.

- **Multi-upstream push** — a single received pack can be forwarded to more than one upstream remote, keeping shared
  repositories (CI workflows, shared libraries) in sync across separate Git hosts without requiring the developer to
  push to each one individually.

- **Upstream buffering** — when an upstream SCM is slow or unavailable, the proxy can hold received packs and retry with
  backoff rather than failing the developer's push immediately.

- **Checkpoint resumption** — because each validation step is persisted as a `PushStep`, a re-push of the same commits
  can skip steps that already passed. This matters most when the chain includes expensive external calls (secret
  scanning, external policy engines) — the developer gets credit for work already done rather than waiting through the
  full chain again.

- **Streaming LLM analysis** — the sideband channel in server mode can stream an LLM's advisory review of the diff back
  to the developer's terminal in real time, giving immediate feedback alongside the existing rule-based checks.

These are tracked as individual issues in the backlog; the architecture is designed to support them incrementally.
