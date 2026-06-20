#!/usr/bin/env python3
"""
Performance benchmark for a git proxy.

Usage:
    python3 perf/bench.py fogwall                           # sequential (default)
    python3 perf/bench.py fogwall --concurrent              # concurrent
    CONCURRENCY=20 TOTAL_OPS=100 python3 perf/bench.py fogwall --concurrent
    RUNS=20 WARMUP=3 python3 perf/bench.py git-proxy        # sequential with tuning
"""

import argparse
import json
import os
import shutil
import subprocess
import statistics
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

SCRIPT_DIR = Path(__file__).parent
ENV_FILE = SCRIPT_DIR / ".env"


def load_env():
    env = {}
    for line in ENV_FILE.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        key, _, val = line.partition("=")
        env[key.strip()] = val.strip()
    return env


def run_git(*args, cwd=None, check=True):
    result = subprocess.run(
        ["git"] + list(args),
        cwd=cwd,
        capture_output=True,
        env={**os.environ, "GIT_TERMINAL_PROMPT": "0"},
        timeout=120,
    )
    if check and result.returncode != 0:
        raise RuntimeError(
            f"git {' '.join(args[:3])} failed (rc={result.returncode}): "
            f"{result.stderr.decode(errors='replace')[:200]}"
        )
    return result


def time_op(fn):
    start = time.perf_counter()
    try:
        fn()
        elapsed = time.perf_counter() - start
        return elapsed, True, None
    except Exception as e:
        elapsed = time.perf_counter() - start
        return elapsed, False, str(e)


def print_stats(label, ok_times, total, wall_elapsed):
    ok_count = len(ok_times)
    fail_count = total - ok_count

    if ok_count == 0:
        print(f"  {label:30s}  ALL {total} OPERATIONS FAILED")
        return

    ok_times.sort()
    avg = statistics.mean(ok_times)
    stddev = statistics.stdev(ok_times) if ok_count > 1 else 0
    p50 = ok_times[int(ok_count * 0.5)]
    p95 = ok_times[int(ok_count * 0.95)]
    throughput = ok_count / wall_elapsed if wall_elapsed > 0 else 0

    print(
        f"  {label:30s}  "
        f"avg={avg*1000:7.0f}ms ± {stddev*1000:.0f}ms  "
        f"p50={p50*1000:7.0f}ms  p95={p95*1000:7.0f}ms  "
        f"min={ok_times[0]*1000:7.0f}ms  max={ok_times[-1]*1000:7.0f}ms  "
        f"ok={ok_count}/{total}"
        + (f"  throughput={throughput:.1f} ops/s" if total > 1 else "")
    )

    return {
        "label": label,
        "ok": ok_count,
        "fail": fail_count,
        "wall_ms": round(wall_elapsed * 1000),
        "avg_ms": round(avg * 1000),
        "stddev_ms": round(stddev * 1000),
        "p50_ms": round(p50 * 1000),
        "p95_ms": round(p95 * 1000),
        "min_ms": round(ok_times[0] * 1000),
        "max_ms": round(ok_times[-1] * 1000),
        "throughput": round(throughput, 1),
    }


def run_sequential(label, total, warmup, op_fn):
    # Warmup runs (not counted)
    for i in range(warmup):
        try:
            op_fn(i)
        except Exception:
            pass

    results = []
    wall_start = time.perf_counter()
    for i in range(total):
        elapsed, ok, err = time_op(lambda i=i: op_fn(i))
        results.append((elapsed, ok, err))
    wall_elapsed = time.perf_counter() - wall_start

    ok_times = [r[0] for r in results if r[1]]
    fail_errors = [r[2] for r in results if not r[1]]
    stats = print_stats(label, ok_times, total, wall_elapsed)
    if fail_errors:
        for e in fail_errors[:3]:
            print(f"    error: {e[:120]}")
    return stats


def run_concurrent(label, total, concurrency, op_fn):
    results = []
    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        wall_start = time.perf_counter()
        futures = {pool.submit(time_op, lambda i=i: op_fn(i)): i for i in range(total)}
        for future in as_completed(futures):
            elapsed, ok, err = future.result()
            results.append((elapsed, ok, err))
        wall_elapsed = time.perf_counter() - wall_start

    ok_times = [r[0] for r in results if r[1]]
    fail_errors = [r[2] for r in results if not r[1]]
    stats = print_stats(label, ok_times, total, wall_elapsed)
    if fail_errors:
        for e in fail_errors[:3]:
            print(f"    error: {e[:120]}")
    return stats


def main():
    parser = argparse.ArgumentParser(description="Git proxy performance benchmark")
    parser.add_argument("proxy", choices=["fogwall", "git-proxy"])
    parser.add_argument("--concurrent", action="store_true", help="Run concurrent benchmark")
    args = parser.parse_args()

    proxy_name = args.proxy
    concurrent = args.concurrent
    env = load_env()

    runs = int(os.environ.get("RUNS", "10" if not concurrent else "20"))
    warmup = int(os.environ.get("WARMUP", "2"))
    total_ops = int(os.environ.get("TOTAL_OPS", str(runs)))
    concurrency = int(os.environ.get("CONCURRENCY", "5"))

    creds = f"{env['TEST_USER']}:{env.get('TOKEN', env['TEST_PASSWORD'])}"
    direct_url = f"http://{env['TEST_USER']}:{env['TEST_PASSWORD']}@localhost:3000/{env['ORG']}/{env['REPO']}.git"

    gitea_host = env["GITEA_URL"].split("://")[1].split(":")[0]
    if proxy_name == "fogwall":
        proxy_url = f"http://{creds}@localhost:8080/proxy/{gitea_host}/{env['ORG']}/{env['REPO']}.git"
    else:
        gitea_host_port = env["GITEA_URL"].split("://")[1]
        proxy_url = f"http://{creds}@localhost:8000/{gitea_host_port}/{env['ORG']}/{env['REPO']}.git"

    mode = "concurrent" if concurrent else "sequential"
    print(f"Proxy:       {proxy_name}")
    print(f"Mode:        {mode}")
    if concurrent:
        print(f"Concurrency: {concurrency}")
        print(f"Total ops:   {total_ops}")
    else:
        print(f"Runs:        {runs} (warmup: {warmup})")
    print()

    run = lambda label, total, fn: (
        run_concurrent(label, total, concurrency, fn)
        if concurrent
        else run_sequential(label, total, warmup, fn)
    )

    all_results = {}
    pid = os.getpid()

    # ── Clone ─────────────────────────────────────────────────────────────
    n = total_ops if concurrent else runs
    print(f"=== Clone ({n} {'ops' if concurrent else 'runs'}) ===")

    def clone_op(url, prefix):
        def op(i):
            dest = f"/tmp/perf-bench-clone-{prefix}-{pid}-{i}"
            try:
                run_git("clone", "--quiet", url, dest)
            finally:
                shutil.rmtree(dest, ignore_errors=True)
        return op

    all_results["clone_direct"] = run("direct (gitea)", n, clone_op(direct_url, "direct"))
    all_results["clone_proxy"] = run(proxy_name, n, clone_op(proxy_url, "proxy"))
    print()

    # ── Fetch ─────────────────────────────────────────────────────────────
    print(f"=== Fetch ({n} {'ops' if concurrent else 'runs'}) ===")

    slot_count = concurrency if concurrent else 1
    fetch_dirs = {"direct": [], "proxy": []}
    for i in range(slot_count):
        for label, url in [("direct", direct_url), ("proxy", proxy_url)]:
            d = f"/tmp/perf-bench-fetch-{label}-{pid}-{i}"
            shutil.rmtree(d, ignore_errors=True)
            run_git("clone", "--quiet", url, d)
            fetch_dirs[label].append(d)

    def fetch_op(dirs):
        def op(i):
            d = dirs[i % len(dirs)]
            run_git("-C", d, "fetch", "--quiet")
        return op

    all_results["fetch_direct"] = run("direct (gitea)", n, fetch_op(fetch_dirs["direct"]))
    all_results["fetch_proxy"] = run(proxy_name, n, fetch_op(fetch_dirs["proxy"]))

    for dirs in fetch_dirs.values():
        for d in dirs:
            shutil.rmtree(d, ignore_errors=True)
    print()

    # ── Push ──────────────────────────────────────────────────────────────
    print(f"=== Push ({n} {'ops' if concurrent else 'runs'}) ===")

    print(f"  Setting up {n} push working copies...")
    push_dirs = []
    for i in range(n):
        branch = f"bench-push-{pid}-{i}"
        setup_dir = f"/tmp/perf-bench-push-setup-{pid}-{i}"
        work_dir = f"/tmp/perf-bench-push-work-{pid}-{i}"

        shutil.rmtree(setup_dir, ignore_errors=True)
        shutil.rmtree(work_dir, ignore_errors=True)
        run_git("clone", "--quiet", direct_url, setup_dir)
        run_git("-C", setup_dir, "config", "user.email", env.get("TEST_EMAIL", "benchuser@example.com"))
        run_git("-C", setup_dir, "config", "user.name", env["TEST_USER"])
        run_git("-C", setup_dir, "checkout", "-b", branch, "--quiet")
        Path(f"{setup_dir}/init-{i}.txt").write_text(f"init {i}")
        run_git("-C", setup_dir, "add", ".")
        run_git("-C", setup_dir, "commit", "--quiet", "-m", f"init {branch}")
        run_git("-C", setup_dir, "push", "--set-upstream", "origin", branch, "--quiet")
        shutil.rmtree(setup_dir)

        run_git("clone", "--quiet", proxy_url, work_dir)
        run_git("-C", work_dir, "config", "user.email", env.get("TEST_EMAIL", "benchuser@example.com"))
        run_git("-C", work_dir, "config", "user.name", env["TEST_USER"])
        run_git("-C", work_dir, "fetch", "--quiet")
        run_git("-C", work_dir, "checkout", branch, "--quiet")
        push_dirs.append(work_dir)

    print(f"  {n} working copies ready.")

    def push_op(i):
        d = push_dirs[i]
        fname = f"bench-{time.monotonic_ns()}-{i}.txt"
        Path(f"{d}/{fname}").write_bytes(os.urandom(256))
        run_git("-C", d, "add", ".")
        run_git("-C", d, "commit", "--quiet", "-m", f"bench {i}")
        run_git("-C", d, "push", "--quiet")

    all_results["push_proxy"] = run(proxy_name, n, push_op)

    for d in push_dirs:
        shutil.rmtree(d, ignore_errors=True)
    print()

    # ── Save results ──────────────────────────────────────────────────────
    results_dir = SCRIPT_DIR / "results" / proxy_name
    results_dir.mkdir(parents=True, exist_ok=True)
    out_file = results_dir / f"{mode}.json"
    (out_file).write_text(json.dumps(all_results, indent=2))
    print(f"=== Results saved to {out_file} ===")


if __name__ == "__main__":
    main()
