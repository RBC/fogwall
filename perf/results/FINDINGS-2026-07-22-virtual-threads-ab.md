# Virtual-thread dispatch A/B — 2026-07-22 (Linux desktop)

A/B comparison of PR #453 (bounded virtual-thread request dispatch, part of #452) using the same build in both
configurations — the `server.max-concurrent-requests: 0` escape hatch restores the pre-change platform-pool
behavior, so the only variable is the dispatch model.

## Environment

- **Machine:** 13th Gen Intel i7-1370P (20 threads), 30 GB RAM, Fedora Linux (kernel 7.0.13)
- **Java:** Temurin 25.0.2 LTS (via mise)
- **Container runtime:** Podman 5.8.3
- **Upstream:** Gitea 1.25 rootless (perf/docker-compose.yml, sqlite, localhost)
- **fogwall:** branch `perf/452-virtual-threads` (commit 5f8fd4b9), store-and-forward mode, h2-mem, auto-approve
- **Control:** `FOGWALL_SERVER__MAX_CONCURRENT_REQUESTS=0` (platform `QueuedThreadPool` only, 200 max — pre-#453 behavior)
- **Variant:** default `max-concurrent-requests: 512` (bounded `VirtualThreadPool` dispatch)
- Confirmed via startup log ("Virtual-thread dispatch disabled/enabled") and `fogwall-server-virtual*` thread
  names in request logs.

## Push — the operation that matters

| Scenario                    | Config      | avg ± σ            | p50     | p95     | throughput    | success |
| --------------------------- | ----------- | ------------------ | ------- | ------- | ------------- | ------- |
| Sequential (10 runs)        | platform    | 880ms ± 92ms       | 870ms   | 1015ms  | 1.1 ops/s     | 10/10   |
| Sequential (10 runs)        | **virtual** | **611ms ± 11ms**   | 612ms   | 632ms   | 1.6 ops/s     | 10/10   |
| Concurrent 10×50            | platform    | 1040ms ± 225ms     | 983ms   | 1712ms  | 8.9 ops/s     | 50/50   |
| Concurrent 10×50            | **virtual** | **1026ms ± 167ms** | 977ms   | 1477ms  | 9.1 ops/s     | 50/50   |
| Concurrent 50×150           | platform    | 12187ms ± 3516ms   | 13160ms | 16154ms | 3.7 ops/s     | 150/150 |
| Concurrent 50×150           | **virtual** | **7218ms ± 4065ms**| 5891ms  | 14416ms | **5.8 ops/s** | 150/150 |

Clone and fetch were at parity or slightly better in the virtual configuration across all scenarios (both are
pass-through-dominated; differences are within run-to-run noise).

## Reading

- **Concurrency 10:** parity (8.9 → 9.1 ops/s). Expected — 10 concurrent pushes nowhere near saturates a
  200-thread pool, so the dispatch model doesn't matter. This is the no-regression check.
- **Concurrency 50:** **+57% throughput (3.7 → 5.8 ops/s), p50 latency −55% (13.2s → 5.9s).** Each git push is
  multiple HTTP requests, and `BlockingContentHandler` adds a nested dispatch per POST, so 50 concurrent pushes
  drive well over 100 concurrent tasks into the pool — the platform config queues behind pool saturation, the
  virtual config doesn't.
- **Sequential:** 880 → 611ms avg. Treat with care (single run each; Gitea warm-state differed between passes),
  but the much tighter σ (±11ms vs ±92ms) suggests less scheduler jitter, not just noise.
- The remaining latency at concurrency 50 in *both* configs (multi-second averages) is per-repo contention —
  every push targets the same `bench-repo` and serializes on the shared mirror (issue #452 section 3), which
  dispatch cannot fix.
- **Not measured here:** the primary motivation for #453 — pending-approval pushes parking without exhausting
  the pool — needs the approval-flood scenario (#452 benchmark additions). This harness runs auto-approve.

## Harness note

`perf/fogwall-perf.yml` had drifted from the current config schema (`permissions[].operations` → `grant`,
`rules.allow[].operations` → `operation`) and failed startup validation; fixed in the same commit as this file.
