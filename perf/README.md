# Performance benchmarks

Side-by-side comparison of **fogwall** vs **finos/git-proxy** using a shared
local Gitea backend. Both proxies are configured with minimal validation rules
and local-only storage (h2 / filesystem) so the numbers reflect proxy overhead,
not database or network latency.

## Prerequisites

- Docker (for Gitea only — pre-built image, no build needed)
- Python 3 (standard library only, no pip packages)
- Java 25+ (for fogwall)
- Node.js 18+ (for finos/git-proxy)
- git

## Quick start

```bash
# 1. Start services locally
bash perf/start-local.sh

# 2. One-time setup: create test org, repo, user, seed data
bash perf/setup.sh --local

# 3. Run sequential benchmarks
python3 perf/bench.py fogwall
python3 perf/bench.py git-proxy

# 4. Run concurrent benchmarks
CONCURRENCY=20 TOTAL_OPS=100 python3 perf/bench.py fogwall --concurrent
CONCURRENCY=20 TOTAL_OPS=100 python3 perf/bench.py git-proxy --concurrent

# 5. Stop services
bash perf/stop-local.sh
```

## Docker Compose (fully isolated)

```bash
docker compose -f perf/docker-compose.yml up -d --build
bash perf/setup.sh
python3 perf/bench.py fogwall
docker compose -f perf/docker-compose.yml down -v
```

## Tuning

```bash
# Sequential: more runs, more warmup
RUNS=20 WARMUP=5 python3 perf/bench.py fogwall

# Concurrent: scale up
CONCURRENCY=50 TOTAL_OPS=200 python3 perf/bench.py fogwall --concurrent
```

## What's tested

| Scenario | Description |
|----------|-------------|
| Clone | Full `git clone` through proxy vs direct Gitea |
| Fetch | `git fetch` on pre-cloned repo |
| Push | Commit + push through proxy (transparent mode) |

### Push architecture difference

Both proxies transparently forward pushes to upstream, but inspect the pack
data differently:

- **fogwall** maintains a persistent cached clone per repo
  (`LocalRepositoryCache`) that is reused across requests. On each push, it
  does an incremental fetch (with a 5s cooldown to avoid redundant fetches),
  inspects commits from the cache, and forwards the original request to
  upstream.

- **finos/git-proxy** does a fresh `git clone` of the upstream repo into a
  temp directory on every push, runs `git receive-pack` into it for
  inspection, then deletes the clone and forwards the original request. Every
  push pays the full clone cost.

fogwall also supports a separate **store-and-forward mode** (not benchmarked
here) where pushes are received into a persistent local bare repo and forwarded
on behalf of the user — enabling deferred forwarding and approval gates.

## Results

See [results/FINDINGS.md](results/FINDINGS.md) for detailed benchmark results.

## Teardown

```bash
bash perf/stop-local.sh
# or for Docker Compose:
docker compose -f perf/docker-compose.yml down -v
```
