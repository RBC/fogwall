# Performance benchmark findings — 2026-06-20

## Environment

- **Machine:** macOS (Apple Silicon, corporate laptop)
- **Upstream:** Gitea 1.25 (Docker, localhost:3000, sqlite)
- **fogwall:** v1.1.0, transparent proxy mode, h2-mem DB, auto-approve, identity verification off
- **finos/git-proxy:** v2.0.0, fs sink, local code modifications required (see below)
- **Tools:** hyperfine (sequential), custom Python harness (concurrent)
- **Test repo:** 50 files, ~500KB total

## Sequential benchmarks (hyperfine, 10 runs + 2 warmup)

### Clone

| Command | Mean ± σ | Range |
|---------|----------|-------|
| direct (gitea) | 279ms ± 21ms | 250–313ms |
| fogwall | 310ms ± 37ms | 257–374ms |

fogwall overhead: **~30ms** (1.11x direct)

### Fetch

| Command | Mean ± σ | Range |
|---------|----------|-------|
| direct (gitea) | 226ms ± 42ms | 193–332ms |
| fogwall | 237ms ± 35ms | 193–292ms |

fogwall overhead: **~11ms** (1.05x direct)

### Rapid fetch (50 runs, no warmup — cache behavior)

| Command | Mean ± σ | Range |
|---------|----------|-------|
| direct (gitea) | 240ms ± 39ms | 178–396ms |
| fogwall | 238ms ± 47ms | 174–420ms |

fogwall: **within noise of direct** (1.01x — effectively zero overhead)

## Concurrent benchmarks (100 ops, 20 parallel)

### Clone

| Proxy | avg | p50 | p95 | max | throughput | success |
|-------|-----|-----|-----|-----|------------|---------|
| direct (gitea) | 3236ms | 3150ms | 4818ms | 5760ms | 5.9 ops/s | 100/100 |
| **fogwall** | **3364ms** | **3282ms** | **5245ms** | **6397ms** | **5.6 ops/s** | **100/100** |
| direct (gitea) | 2854ms | 2760ms | 4521ms | 6515ms | 6.6 ops/s | 100/100 |
| **git-proxy** | **2970ms** | **2807ms** | **4788ms** | **8187ms** | **6.3 ops/s** | **100/100** |

Both proxies within noise of direct Gitea. Clone overhead is negligible for
both — the bottleneck is Gitea (sqlite) and disk I/O.

### Fetch

| Proxy | avg | p50 | p95 | max | throughput | success |
|-------|-----|-----|-----|-----|------------|---------|
| direct (gitea) | 1392ms | 1372ms | 2376ms | 2607ms | 13.6 ops/s | 100/100 |
| **fogwall** | **1418ms** | **1369ms** | **2374ms** | **4424ms** | **13.2 ops/s** | **100/100** |
| direct (gitea) | 1323ms | 1264ms | 2327ms | 2884ms | 14.1 ops/s | 100/100 |
| **git-proxy** | **1393ms** | **1326ms** | **2376ms** | **3779ms** | **13.5 ops/s** | **100/100** |

Both proxies within noise of direct Gitea. Fetch overhead is negligible.

### Push — the headline result

| Proxy | avg | p50 | p95 | max | throughput | success |
|-------|-----|-----|-----|-----|------------|---------|
| **fogwall** | **4933ms** | **4521ms** | **8000ms** | **11245ms** | **3.9 ops/s** | **100/100** |
| **git-proxy** | **12475ms** | **12850ms** | **15105ms** | **17500ms** | **1.5 ops/s** | **100/100** |

**fogwall is 2.6x faster on push throughput** (3.9 vs 1.5 ops/s), with 60%
lower average latency and 47% lower p95.

## Why pushes are different

Both proxies transparently forward pushes to the upstream, but they inspect
the pack data differently:

- **fogwall** maintains a persistent cached clone per repo
  (`LocalRepositoryCache`) that is reused across requests. On each push, it
  does an incremental fetch (with a 5s cooldown to avoid redundant fetches
  under concurrent load), inspects commits from the cache, and forwards the
  original request to upstream. The first push to a repo pays the clone cost;
  subsequent pushes only pay for incremental fetches.

- **finos/git-proxy** does a fresh `git clone` of the upstream repo into a
  temp directory on every push, runs `git receive-pack` into it for
  inspection, then deletes the clone and forwards the original request. Every
  push pays the full clone cost, which scales linearly with concurrency.

## Modifications required to benchmark finos/git-proxy

The following local code changes were needed to get finos/git-proxy v2.0.0
working with a plain HTTP Gitea backend:

1. **Hardcoded HTTPS** — `proxy/routes/index.ts` constructs `'https://' + origin`
   with no HTTP option. Changed to `'http://'`.
2. **parseAction scheme** — `proxy/processors/pre-processor/parseAction.ts`
   hardcodes `'https:/'`. Changed to `'http:/'`.
3. **Request path resolver** — `getRequestPathResolver('https://')` changed to
   `'http://'`.
4. **blockForAuth** — always blocks pushes with no auto-approve config option.
   Changed `setAsyncBlock()` to `log()` so pushes flow through.
5. **checkUserPushPermission** — hardcoded `isUserAllowed = true` after the DB
   lookup so the permission check runs but always passes.

None of these changes were needed for fogwall.

## Bugs found during benchmarking

### finos/git-proxy

- **Pack parser fails on 0-entry packs** — `parsePush` throws "No commit data
  found" when git sends a pack with 0 objects (valid operation: creating a
  branch at an existing commit). The `checkEmptyBranch` step then fails because
  the local clone directory doesn't exist.

### fogwall

- **Token cache race under concurrency** — concurrent pushes with the same
  token race on `INSERT INTO scm_token_cache`, causing
  `DuplicateKeyException`. Fixed by using `MERGE` with a `DuplicateKeyException`
  catch as fallback.

## Next steps

- [ ] Run in Docker Compose on personal machine for fully isolated comparison
- [ ] Test with TLS enabled (compare JDK SSL vs OpenSSL overhead)
- [ ] Increase test repo size (1MB, 10MB, 100MB) for pack transfer scaling
- [ ] Scale concurrency higher (50, 100 parallel)
- [ ] Benchmark fogwall store-and-forward mode separately
- [ ] File the pack parser bug on finos/git-proxy
