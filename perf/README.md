# Performance benchmarks

Side-by-side comparison of **fogwall** vs **finos/git-proxy** using a shared
local Gitea backend. Both proxies are configured with minimal validation rules
and local-only storage (h2 / filesystem) so the numbers reflect proxy overhead,
not database or network latency.

## Prerequisites

- Docker (with Compose v2)
- [hyperfine](https://github.com/sharkdp/hyperfine) — `brew install hyperfine`
- git

## Quick start

```bash
# 1. Build and start all services (Gitea + fogwall + finos/git-proxy)
docker compose -f perf/docker-compose.yml up -d --build

# 2. One-time setup: create test org, repo, user, seed data
bash perf/setup.sh

# 3. Run benchmarks
bash perf/bench.sh

# 4. View results
cat perf/results/clone.md
cat perf/results/fetch.md
cat perf/results/push.md
```

## What's tested

| Scenario | fogwall | finos/git-proxy |
|----------|---------|-----------------|
| `clone`  | transparent proxy (Jetty ProxyServlet) | transparent proxy (express-http-proxy) |
| `fetch`  | transparent proxy | transparent proxy |
| `push`   | transparent proxy (servlet filter chain inspects pack, then forwards) | transparent proxy (clones upstream into temp dir for inspection, deletes clone, then forwards original request) |

Each scenario also includes a **direct Gitea** baseline (no proxy) for clone
and fetch to show raw overhead.

A **rapid fetch** scenario (50 runs, no warmup) tests cache behavior — how
efficiently each proxy serves repeated fetches.

### Push inspection strategies

Both proxies transparently forward the push to upstream, but inspect the pack
data differently:

- **fogwall** parses the pack inline in the servlet filter chain (no local
  clone needed for transparent mode). The filter chain reads the pack stream,
  runs validation, and forwards to upstream in a single pass.

- **finos/git-proxy** clones the upstream repo into a temp directory, runs
  `git receive-pack` locally to unpack and inspect, deletes the clone, then
  forwards the original HTTP request to upstream. The extra clone+receive-pack
  adds overhead that should show up in the push benchmark.

fogwall also supports a separate **store-and-forward mode** (not benchmarked
here) where pushes are received into a persistent local bare repo and forwarded
on behalf of the user — enabling deferred forwarding and approval gates.

## Tuning

```bash
RUNS=20 WARMUP=3 bash perf/bench.sh
```

## Teardown

```bash
docker compose -f perf/docker-compose.yml down -v
```
