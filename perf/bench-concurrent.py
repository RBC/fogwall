#!/usr/bin/env python3
"""
Concurrency benchmark for a git proxy.

Usage:
    python3 perf/bench-concurrent.py fogwall
    python3 perf/bench-concurrent.py git-proxy
    CONCURRENCY=20 TOTAL_OPS=100 python3 perf/bench-concurrent.py fogwall
"""

import os
import sys
import json
import shutil
import subprocess
import tempfile
import time
import statistics
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
        timeout=60,
    )
    if check and result.returncode != 0:
        raise RuntimeError(
            f"git {' '.join(args)} failed (rc={result.returncode}): "
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


def run_concurrent(label, total, concurrency, op_fn):
    results = []
    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        wall_start = time.perf_counter()
        futures = {pool.submit(time_op, lambda i=i: op_fn(i)): i for i in range(total)}
        for future in as_completed(futures):
            elapsed, ok, err = future.result()
            results.append((elapsed, ok, err))
        wall_elapsed = time.perf_counter() - wall_start

    ok_times = sorted([r[0] for r in results if r[1]])
    fail_count = sum(1 for r in results if not r[1])
    ok_count = len(ok_times)

    if ok_count == 0:
        errors = [r[2] for r in results if not r[1]][:3]
        print(f"  {label:30s}  ALL {total} OPERATIONS FAILED")
        for e in errors:
            print(f"    error: {e[:120]}")
        return {"label": label, "ok": 0, "fail": total, "errors": errors}

    avg = statistics.mean(ok_times)
    p50 = ok_times[int(ok_count * 0.5)]
    p95 = ok_times[int(ok_count * 0.95)]
    throughput = ok_count / wall_elapsed

    print(
        f"  {label:30s}  wall={wall_elapsed*1000:7.0f}ms  "
        f"avg={avg*1000:7.0f}ms  p50={p50*1000:7.0f}ms  p95={p95*1000:7.0f}ms  "
        f"min={ok_times[0]*1000:7.0f}ms  max={ok_times[-1]*1000:7.0f}ms  "
        f"ok={ok_count}/{total}  throughput={throughput:.1f} ops/s"
    )
    if fail_count > 0:
        errors = [r[2] for r in results if not r[1]][:3]
        for e in errors:
            print(f"    error: {e[:120]}")

    return {
        "label": label,
        "ok": ok_count,
        "fail": fail_count,
        "wall_ms": round(wall_elapsed * 1000),
        "avg_ms": round(avg * 1000),
        "p50_ms": round(p50 * 1000),
        "p95_ms": round(p95 * 1000),
        "min_ms": round(ok_times[0] * 1000),
        "max_ms": round(ok_times[-1] * 1000),
        "throughput": round(throughput, 1),
    }


def main():
    if len(sys.argv) < 2:
        print("Usage: bench-concurrent.py <fogwall|git-proxy>", file=sys.stderr)
        sys.exit(1)

    proxy_name = sys.argv[1]
    env = load_env()
    concurrency = int(os.environ.get("CONCURRENCY", "5"))
    total_ops = int(os.environ.get("TOTAL_OPS", "20"))

    creds = f"{env['TEST_USER']}:{env.get('TOKEN', env['TEST_PASSWORD'])}"
    direct_url = f"http://{env['TEST_USER']}:{env['TEST_PASSWORD']}@localhost:3000/{env['ORG']}/{env['REPO']}.git"

    gitea_host = env["GITEA_URL"].split("://")[1].split(":")[0]
    if proxy_name == "fogwall":
        proxy_url = f"http://{creds}@localhost:8080/proxy/{gitea_host}/{env['ORG']}/{env['REPO']}.git"
    elif proxy_name == "git-proxy":
        gitea_host_port = env["GITEA_URL"].split("://")[1]
        proxy_url = f"http://{creds}@localhost:8000/{gitea_host_port}/{env['ORG']}/{env['REPO']}.git"
    else:
        print(f"Unknown proxy: {proxy_name}", file=sys.stderr)
        sys.exit(1)

    print(f"Proxy:       {proxy_name}")
    print(f"Concurrency: {concurrency}")
    print(f"Total ops:   {total_ops}")
    print()

    all_results = {}

    # ── Clone benchmark ───────────────────────────────────────────────────
    print(f"=== Concurrent clone ({total_ops} ops, {concurrency} parallel) ===")

    def clone_op(url, prefix):
        def op(i):
            dest = f"/tmp/perf-cc-{prefix}-{os.getpid()}-{i}"
            try:
                run_git("clone", "--quiet", url, dest)
            finally:
                shutil.rmtree(dest, ignore_errors=True)
        return op

    all_results["clone_direct"] = run_concurrent(
        "direct (gitea)", total_ops, concurrency, clone_op(direct_url, "direct")
    )
    all_results["clone_proxy"] = run_concurrent(
        proxy_name, total_ops, concurrency, clone_op(proxy_url, "proxy")
    )
    print()

    # ── Fetch benchmark ───────────────────────────────────────────────────
    print(f"=== Concurrent fetch ({total_ops} ops, {concurrency} parallel) ===")

    fetch_dirs = {"direct": [], "proxy": []}
    for i in range(concurrency):
        for label, url in [("direct", direct_url), ("proxy", proxy_url)]:
            d = f"/tmp/perf-cf-{label}-{os.getpid()}-{i}"
            shutil.rmtree(d, ignore_errors=True)
            run_git("clone", "--quiet", url, d)
            fetch_dirs[label].append(d)

    def fetch_op(dirs):
        def op(i):
            d = dirs[i % len(dirs)]
            run_git("-C", d, "fetch", "--quiet")
        return op

    all_results["fetch_direct"] = run_concurrent(
        "direct (gitea)", total_ops, concurrency, fetch_op(fetch_dirs["direct"])
    )
    all_results["fetch_proxy"] = run_concurrent(
        proxy_name, total_ops, concurrency, fetch_op(fetch_dirs["proxy"])
    )

    for dirs in fetch_dirs.values():
        for d in dirs:
            shutil.rmtree(d, ignore_errors=True)
    print()

    # ── Push benchmark ────────────────────────────────────────────────────
    print(f"=== Concurrent push ({total_ops} ops, {concurrency} parallel) ===")

    # Setup: create branches via direct Gitea, clone working copies via proxy
    print(f"  Setting up {total_ops} push working copies...")
    push_dirs = []
    pid = os.getpid()
    for i in range(total_ops):
        branch = f"bench-push-{pid}-{i}"
        setup_dir = f"/tmp/perf-cp-setup-{pid}-{i}"
        work_dir = f"/tmp/perf-cp-work-{pid}-{i}"

        # Create branch with initial commit via direct Gitea
        shutil.rmtree(setup_dir, ignore_errors=True)
        run_git("clone", "--quiet", direct_url, setup_dir)
        run_git("-C", setup_dir, "config", "user.email", env.get("TEST_EMAIL", "benchuser@example.com"))
        run_git("-C", setup_dir, "config", "user.name", env["TEST_USER"])
        run_git("-C", setup_dir, "checkout", "-b", branch, "--quiet")
        Path(f"{setup_dir}/init-{i}.txt").write_text(f"init {i}")
        run_git("-C", setup_dir, "add", ".")
        run_git("-C", setup_dir, "commit", "--quiet", "-m", f"init {branch}")
        run_git("-C", setup_dir, "push", "--set-upstream", "origin", branch, "--quiet")
        shutil.rmtree(setup_dir)

        # Clone via proxy, checkout branch
        shutil.rmtree(work_dir, ignore_errors=True)
        run_git("clone", "--quiet", proxy_url, work_dir)
        run_git("-C", work_dir, "config", "user.email", env.get("TEST_EMAIL", "benchuser@example.com"))
        run_git("-C", work_dir, "config", "user.name", env["TEST_USER"])
        run_git("-C", work_dir, "fetch", "--quiet")
        run_git("-C", work_dir, "checkout", branch, "--quiet")
        push_dirs.append(work_dir)

    print(f"  {total_ops} working copies ready.")

    def push_op(i):
        d = push_dirs[i]
        fname = f"bench-{time.monotonic_ns()}-{i}.txt"
        Path(f"{d}/{fname}").write_bytes(os.urandom(256))
        run_git("-C", d, "add", ".")
        run_git("-C", d, "commit", "--quiet", "-m", f"bench {i}")
        run_git("-C", d, "push", "--quiet")

    all_results["push_proxy"] = run_concurrent(
        proxy_name, total_ops, concurrency, push_op
    )

    for d in push_dirs:
        shutil.rmtree(d, ignore_errors=True)
    print()

    # ── Save results ──────────────────────────────────────────────────────
    results_dir = SCRIPT_DIR / "results" / proxy_name
    results_dir.mkdir(parents=True, exist_ok=True)
    (results_dir / "concurrent.json").write_text(json.dumps(all_results, indent=2))
    print(f"=== Results saved to {results_dir}/concurrent.json ===")


if __name__ == "__main__":
    main()
