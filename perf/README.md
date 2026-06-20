# Performance benchmarks

Side-by-side comparison of **fogwall** vs **finos/git-proxy** using a shared
local Gitea backend. Both proxies are configured with minimal validation rules
and local-only storage (h2 / filesystem) so the numbers reflect proxy overhead,
not database or network latency.

## Prerequisites

- Docker with Compose (Docker Desktop or Podman)
- Python 3 (standard library only)
- Java 25+ (for fogwall)
- Node.js 22.13+ or 24+ (for finos/git-proxy)
- git

## Setup

```bash
# Start Gitea
docker compose -f perf/docker-compose.yml up -d gitea

# Create test user, org, repo, seed data
bash perf/setup.sh
```

## Running fogwall

fogwall loads `fogwall-{profile}.yml` from the classpath. Symlink the perf
config into the resources directory so Gradle picks it up:

```bash
ln -sf "$(pwd)/perf/fogwall-perf.yml" fogwall-server/src/main/resources/fogwall-perf.yml
FOGWALL_CONFIG_PROFILES=perf ./gradlew :fogwall-server:run
```

## Running finos/git-proxy

finos/git-proxy requires local code modifications to work with a plain HTTP
backend (hardcoded HTTPS, no auto-approve config). See the findings files
for the specific changes needed.

```bash
cd /path/to/finos-git-proxy
node dist/index.js --config /path/to/fogwall/perf/git-proxy-perf.json
```

Note: `git-proxy-perf.json` uses Docker networking hostnames (`gitea:3000`).
For local runs, create a copy with `localhost:3000` and `http://` scheme.

## Benchmarks

```bash
# Sequential (10 runs, 2 warmup)
python3 perf/bench.py fogwall
python3 perf/bench.py git-proxy

# Concurrent
CONCURRENCY=10 TOTAL_OPS=50 python3 perf/bench.py fogwall --concurrent
CONCURRENCY=10 TOTAL_OPS=50 python3 perf/bench.py git-proxy --concurrent

# Tuning
RUNS=20 WARMUP=5 python3 perf/bench.py fogwall
CONCURRENCY=50 TOTAL_OPS=200 python3 perf/bench.py fogwall --concurrent
```

Results are saved to `perf/results/<proxy>/sequential.json` and
`perf/results/<proxy>/concurrent.json`.

## What's tested

| Scenario | Description |
|----------|-------------|
| Clone | Full `git clone` through proxy vs direct Gitea |
| Fetch | `git fetch` on pre-cloned repo |
| Push | Commit + push through proxy (transparent mode) |

### Push architecture difference

Both proxies need a local clone for commit inspection and diff generation.
The difference is in how that clone is managed:

- **fogwall** maintains a persistent cached clone per repo
  (`LocalRepositoryCache`) that is reused across requests. Subsequent pushes
  do an incremental fetch (with a 5s cooldown). Under concurrency, all
  parallel pushes share one cached clone.

- **finos/git-proxy** does a fresh `git clone` into a temp directory on
  every push, runs `git receive-pack` for inspection, then deletes the clone.
  Every push pays the full clone cost.

## Results

See `results/FINDINGS-*.md` for detailed benchmark results per machine.

## Teardown

```bash
docker compose -f perf/docker-compose.yml down -v
```
