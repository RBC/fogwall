# Performance benchmark — 2026-06-20 (MacBook Pro, corporate)

## Environment

- **Machine:** MacBook Pro, Apple M1 Pro, 32 GB RAM, macOS 26.4.1, corporate endpoint security active
- **Java:** Temurin 25.0.3+9.0 LTS (via mise)
- **Node.js:** v24.15.0
- **Docker:** Desktop 4.12.0 (85629), engine 20.10.17
- **Upstream:** Gitea 1.25 (Docker, localhost:3000, sqlite)
- **fogwall:** v1.1.0, transparent proxy mode, h2-mem DB, auto-approve, identity verification off
- **finos/git-proxy:** v2.0.0, fs sink, local code modifications required (see notes)
- **Tool:** Python 3, `concurrent.futures` + `time.perf_counter`
- **Test repo:** 50 files, ~500KB total

## Sequential (10 runs, 2 warmup)

### Clone

| Proxy | avg ± σ | p50 | p95 | min | max |
|-------|---------|-----|-----|-----|-----|
| direct (gitea) | 787ms ± 80ms | 771ms | 936ms | 717ms | 936ms |
| **fogwall** | **798ms ± 71ms** | **823ms** | **919ms** | **711ms** | **919ms** |
| direct (gitea) | 793ms ± 96ms | 798ms | 1023ms | 696ms | 1023ms |
| **git-proxy** | **795ms ± 59ms** | **790ms** | **901ms** | **706ms** | **901ms** |

Both proxies within noise of direct. Negligible clone overhead.

### Fetch

| Proxy | avg ± σ | p50 | p95 | min | max |
|-------|---------|-----|-----|-----|-----|
| direct (gitea) | 267ms ± 42ms | 265ms | 362ms | 222ms | 362ms |
| **fogwall** | **296ms ± 39ms** | **290ms** | **364ms** | **236ms** | **364ms** |
| direct (gitea) | 259ms ± 46ms | 253ms | 336ms | 196ms | 336ms |
| **git-proxy** | **297ms ± 54ms** | **275ms** | **408ms** | **259ms** | **408ms** |

Both proxies add ~30ms fetch overhead. Comparable.

### Push

| Proxy | avg ± σ | p50 | p95 | min | max | throughput |
|-------|---------|-----|-----|-----|-----|------------|
| **fogwall** | **1200ms ± 92ms** | **1163ms** | **1379ms** | **1103ms** | **1379ms** | **0.8 ops/s** |
| **git-proxy** | **1597ms ± 120ms** | **1602ms** | **1830ms** | **1447ms** | **1830ms** | **0.6 ops/s** |

fogwall is **1.33x faster** sequentially (1200ms vs 1597ms avg). The difference
is cached clone + incremental fetch (fogwall) vs fresh clone per push (git-proxy).

## Concurrent (50 ops, 10 parallel)

### Clone

| Proxy | avg ± σ | p50 | p95 | max | throughput | success |
|-------|---------|-----|-----|-----|------------|---------|
| **fogwall** | **2078ms ± 445ms** | **2111ms** | **2799ms** | **2930ms** | **4.5 ops/s** | **50/50** |
| **git-proxy** | **2155ms ± 391ms** | **2184ms** | **2694ms** | **3008ms** | **4.5 ops/s** | **50/50** |

Comparable. Bottleneck is Gitea, not the proxy.

### Fetch

| Proxy | avg ± σ | p50 | p95 | max | throughput | success |
|-------|---------|-----|-----|-----|------------|---------|
| **fogwall** | **763ms ± 179ms** | **726ms** | **1140ms** | **1349ms** | **12.5 ops/s** | **50/50** |
| **git-proxy** | **765ms ± 149ms** | **750ms** | **1004ms** | **1155ms** | **12.5 ops/s** | **50/50** |

Identical throughput.

### Push — the headline

| Proxy | avg ± σ | p50 | p95 | max | throughput | success |
|-------|---------|-----|-----|-----|------------|---------|
| **fogwall** | **2743ms ± 930ms** | **2447ms** | **4636ms** | **4693ms** | **3.5 ops/s** | **50/50** |
| **git-proxy** | **8307ms ± 1322ms** | **8652ms** | **9858ms** | **11548ms** | **1.1 ops/s** | **50/50** |

**fogwall is 3.2x faster on concurrent push throughput** (3.5 vs 1.1 ops/s).
Average latency 67% lower (2.7s vs 8.3s). p95 53% lower (4.6s vs 9.9s).

## Why pushes diverge under concurrency

Both proxies need a local clone for commit inspection and diff generation.
The difference is in how that clone is managed:

- **fogwall** maintains a persistent `LocalRepositoryCache` — one cached bare
  clone per repo, shared across all requests. Subsequent pushes do an
  incremental `git fetch` (with a 5s cooldown to avoid redundant fetches).
  Under concurrency, 10 parallel pushes share one clone.

- **finos/git-proxy** does a fresh `git clone` into a temp directory on every
  push, runs `git receive-pack` for inspection, then deletes the clone. Under
  concurrency, 10 parallel pushes each clone the full repo independently.

## Modifications required for finos/git-proxy

1. Hardcoded `https://` in proxy routes — changed to `http://` for local Gitea
2. `parseAction` hardcodes `https:/` scheme — changed to `http:/`
3. `blockForAuth` always blocks pushes — changed to log-only (no auto-approve config)
4. `checkUserPushPermission` — forced `isUserAllowed = true` (no user registration flow)

None of these changes were needed for fogwall.

## Bugs found

### finos/git-proxy
- Pack parser fails on 0-entry packs (creating a branch at an existing commit)

### fogwall
- `scm_token_cache` concurrent INSERT race — fixed with MERGE + DuplicateKeyException catch
