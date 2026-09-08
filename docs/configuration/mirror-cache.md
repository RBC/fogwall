# Local mirror cache

To inspect push content, fogwall keeps a local bare mirror of each upstream repository. How much history that mirror
holds is a per-deployment tradeoff: a full mirror is the most correct basis for reachability checks, but a first clone
of a large repository (e.g. one with a large binary history) can be slow enough to exceed HTTP connection timeouts; a
shallow mirror clones cheaply but truncates history. The two modes are configured separately and only their **defaults**
differ — **server mode defaults to full history, transparent proxy to a shallow clone.** Either mode accepts either
knob: server mode is used against large repositories too, so shallow cloning is fully supported there — set
`cache.server.shallow-since` or `cache.server.clone-depth` to enable it.

```yaml
cache:
  proxy:
    shallow-since: 90d # keep history back ~90 days (preferred)
    # clone-depth: 100 # or a fixed commit depth; used only when shallow-since is unset
  server:
    clone-depth: 0 # 0 = full history (the default)
```

| Key                          | Default (proxy / server) | Meaning                                                                                                    |
| ---------------------------- | ------------------------ | ---------------------------------------------------------------------------------------------------------- |
| `cache.<mode>.shallow-since` | unset                    | Time-based boundary — `90d`, `12h`, `30m`, or an ISO-8601 duration (`PT48H`). Takes precedence over depth. |
| `cache.<mode>.clone-depth`   | `100` / `0`              | Commit depth for the shallow clone; `0` means full history. Used only when `shallow-since` is unset.       |

Prefer `shallow-since`: a commit _depth_ is a graph-distance bound, not a function of age, so which commits fall outside
`depth=100` is unpredictable for a given repository — whereas "keep 90 days" is something an operator can reason about
and write against a retention requirement.

A shallow default is a clone-cost optimisation, not a correctness ceiling: checks that would get a wrong answer from a
truncated mirror (reachability / hidden-commit detection) deepen it to full history on demand before deciding, so a
release tag on a commit that predates the shallow boundary is still validated correctly.
