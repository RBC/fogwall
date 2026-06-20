#!/usr/bin/env bash
# Concurrency benchmark for a single git proxy.
# Fires N parallel git operations and measures throughput + per-op latency.
#
# Usage:
#   bash perf/bench-concurrent.sh fogwall       # benchmark fogwall
#   bash perf/bench-concurrent.sh git-proxy     # benchmark finos/git-proxy
#
# Tuning:
#   CONCURRENCY=10 TOTAL_OPS=30 bash perf/bench-concurrent.sh fogwall
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/.env"

PROXY="${1:?Usage: bench-concurrent.sh <fogwall|git-proxy>}"
CONCURRENCY="${CONCURRENCY:-5}"
TOTAL_OPS="${TOTAL_OPS:-20}"
RESULTS_DIR="${SCRIPT_DIR}/results/${PROXY}"
mkdir -p "${RESULTS_DIR}"

# Use Gitea API token as password — fogwall needs it for SCM identity resolution
GIT_PASSWORD="${TOKEN:-${TEST_PASSWORD}}"
CREDS="${TEST_USER}:${GIT_PASSWORD}"
DIRECT="http://${TEST_USER}:${TEST_PASSWORD}@localhost:3000/${ORG}/${REPO}.git"

case "${PROXY}" in
    fogwall)
        GITEA_HOST=$(echo "${GITEA_URL}" | sed 's|.*://||;s|:.*||')
        PROXY_URL="http://${CREDS}@localhost:8080/proxy/${GITEA_HOST}/${ORG}/${REPO}.git"
        ;;
    git-proxy)
        GITEA_HOST_PORT=$(echo "${GITEA_URL}" | sed 's|.*://||')
        PROXY_URL="http://${CREDS}@localhost:8000/${GITEA_HOST_PORT}/${ORG}/${REPO}.git"
        ;;
    *)
        echo "ERROR: Unknown proxy '${PROXY}'." >&2; exit 1 ;;
esac

export GIT_TERMINAL_PROMPT=0

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

run_parallel() {
    local label="$1" total="$2" concurrency="$3"
    shift 3

    local timings_file op_script
    timings_file=$(mktemp)
    op_script=$(mktemp)

    cat > "${op_script}" <<WRAPPER
#!/usr/bin/env bash
set -euo pipefail
N=\$1
op_start=\$(perl -MTime::HiRes=time -e 'printf "%.3f\n", time')
$@ 2>/dev/null
rc=\$?
op_end=\$(perl -MTime::HiRes=time -e 'printf "%.3f\n", time')
op_ms=\$(perl -e "printf('%.0f', (\$op_end - \$op_start) * 1000)")
echo "\${op_ms} \${rc}" >> ${timings_file}
WRAPPER
    chmod +x "${op_script}"

    local start_wall
    start_wall=$(perl -MTime::HiRes=time -e 'printf "%.3f\n", time')

    seq 1 "${total}" | xargs -P "${concurrency}" -I{} bash "${op_script}" {}

    local end_wall
    end_wall=$(perl -MTime::HiRes=time -e 'printf "%.3f\n", time')
    local wall_ms
    wall_ms=$(perl -e "printf('%.0f', ($end_wall - $start_wall) * 1000)")

    # Compute stats
    local total_ops ok_ops fail_ops min max sum avg p50 p95
    total_ops=$(wc -l < "${timings_file}" | tr -d ' ')
    ok_ops=$(awk '$2==0' "${timings_file}" | wc -l | tr -d ' ')
    fail_ops=$((total_ops - ok_ops))

    if [ "${ok_ops}" -eq 0 ]; then
        printf "  %-30s  ALL %d OPERATIONS FAILED (check /tmp/perf-push-err-*.log)\n" "${label}" "${total_ops}"
        rm -f "${timings_file}" "${op_script}"
        return
    fi

    min=$(awk '$2==0 {print $1}' "${timings_file}" | sort -n | head -1)
    max=$(awk '$2==0 {print $1}' "${timings_file}" | sort -n | tail -1)
    sum=$(awk '$2==0 {s+=$1} END {print s}' "${timings_file}")
    avg=$((sum / ok_ops))
    p50=$(awk '$2==0 {print $1}' "${timings_file}" | sort -n | awk -v n="${ok_ops}" 'NR==int(n*0.5)+1{print}')
    p95=$(awk '$2==0 {print $1}' "${timings_file}" | sort -n | awk -v n="${ok_ops}" 'NR==int(n*0.95)+1{print}')

    local throughput
    throughput=$(perl -e "printf('%.1f', $ok_ops / ($wall_ms / 1000))")

    printf "  %-30s  wall=%5dms  avg=%5dms  p50=%5dms  p95=%5dms  min=%5dms  max=%5dms  ok=%d/%d  throughput=%s ops/s\n" \
        "${label}" "${wall_ms}" "${avg}" "${p50}" "${p95}" "${min}" "${max}" "${ok_ops}" "${total_ops}" "${throughput}"

    rm -f "${timings_file}" "${op_script}"
}

echo "Proxy:       ${PROXY}"
echo "Concurrency: ${CONCURRENCY}"
echo "Total ops:   ${TOTAL_OPS}"
echo ""

# ---------------------------------------------------------------------------
# Concurrent clone
# ---------------------------------------------------------------------------
echo "=== Concurrent clone (${TOTAL_OPS} ops, ${CONCURRENCY} parallel) ==="

run_parallel "direct (gitea)" "${TOTAL_OPS}" "${CONCURRENCY}" \
    "git clone --quiet ${DIRECT} /tmp/perf-cc-direct-\$N 2>/dev/null; rm -rf /tmp/perf-cc-direct-\$N"

run_parallel "${PROXY}" "${TOTAL_OPS}" "${CONCURRENCY}" \
    "git clone --quiet ${PROXY_URL} /tmp/perf-cc-proxy-\$N 2>/dev/null; rm -rf /tmp/perf-cc-proxy-\$N"

echo ""

# ---------------------------------------------------------------------------
# Concurrent fetch
# ---------------------------------------------------------------------------
echo "=== Concurrent fetch (${TOTAL_OPS} ops, ${CONCURRENCY} parallel) ==="

# Pre-clone one copy per concurrent slot
for i in $(seq 1 "${CONCURRENCY}"); do
    rm -rf "/tmp/perf-cf-direct-${i}" "/tmp/perf-cf-proxy-${i}"
    git clone --quiet "${DIRECT}" "/tmp/perf-cf-direct-${i}" 2>/dev/null
    git clone --quiet "${PROXY_URL}" "/tmp/perf-cf-proxy-${i}" 2>/dev/null
done

run_parallel "direct (gitea)" "${TOTAL_OPS}" "${CONCURRENCY}" \
    'SLOT=$(( ($N - 1) % '"${CONCURRENCY}"' + 1 )); git -C /tmp/perf-cf-direct-${SLOT} fetch --quiet'

run_parallel "${PROXY}" "${TOTAL_OPS}" "${CONCURRENCY}" \
    'SLOT=$(( ($N - 1) % '"${CONCURRENCY}"' + 1 )); git -C /tmp/perf-cf-proxy-${SLOT} fetch --quiet'

echo ""

# ---------------------------------------------------------------------------
# Concurrent push
# ---------------------------------------------------------------------------
echo "=== Concurrent push (${TOTAL_OPS} ops, ${CONCURRENCY} parallel) ==="

# Pre-clone via proxy but create branches via direct Gitea (finos/git-proxy
# can't handle a push with 0 pack entries, which is what git sends when
# creating a branch at an existing commit).
echo "  Setting up ${CONCURRENCY} push slots..."
for i in $(seq 1 "${CONCURRENCY}"); do
    rm -rf "/tmp/perf-cp-proxy-${i}" "/tmp/perf-cp-setup-${i}"
    BRANCH="bench-cp-${i}-$$"
    # Clone and create branch with initial commit via direct Gitea
    if ! git clone --quiet "${DIRECT}" "/tmp/perf-cp-setup-${i}" 2>/dev/null; then
        echo "  ERROR: failed to clone direct for slot ${i}" >&2; continue
    fi
    git -C "/tmp/perf-cp-setup-${i}" config user.email "${TEST_EMAIL:-benchuser@example.com}"
    git -C "/tmp/perf-cp-setup-${i}" config user.name "${TEST_USER}"
    git -C "/tmp/perf-cp-setup-${i}" checkout -b "${BRANCH}" --quiet
    echo "init" > "/tmp/perf-cp-setup-${i}/bench-init.txt"
    git -C "/tmp/perf-cp-setup-${i}" add . >/dev/null
    git -C "/tmp/perf-cp-setup-${i}" commit --quiet -m "init bench branch"
    if ! git -C "/tmp/perf-cp-setup-${i}" push --set-upstream origin "${BRANCH}" --quiet 2>&1; then
        echo "  ERROR: failed to push setup branch for slot ${i}" >&2
    fi
    rm -rf "/tmp/perf-cp-setup-${i}"
    # Clone via proxy and point at the branch
    if ! git clone --quiet "${PROXY_URL}" "/tmp/perf-cp-proxy-${i}" 2>/dev/null; then
        echo "  ERROR: failed to clone proxy for slot ${i}" >&2; continue
    fi
    git -C "/tmp/perf-cp-proxy-${i}" config user.email "${TEST_EMAIL:-benchuser@example.com}"
    git -C "/tmp/perf-cp-proxy-${i}" config user.name "${TEST_USER}"
    git -C "/tmp/perf-cp-proxy-${i}" fetch --quiet 2>/dev/null
    git -C "/tmp/perf-cp-proxy-${i}" checkout "${BRANCH}" --quiet
    echo "  Slot ${i}/${CONCURRENCY} ready (${BRANCH})"
done
echo "  Push setup complete."

# Each op gets its own working copy to avoid git index races under concurrency.
# Clone from the pre-setup slot repos (which already have the branch checked out).
echo "  Cloning ${TOTAL_OPS} push working copies..."
for i in $(seq 1 "${TOTAL_OPS}"); do
    SLOT=$(( (i - 1) % CONCURRENCY + 1 ))
    cp -R "/tmp/perf-cp-proxy-${SLOT}" "/tmp/perf-cp-op-${i}"
done
echo "  Ready."

run_parallel "${PROXY}" "${TOTAL_OPS}" "${CONCURRENCY}" \
    'DIR=/tmp/perf-cp-op-${N}; ID=$(date +%s%N)${N}; dd if=/dev/urandom bs=256 count=1 2>/dev/null | base64 > ${DIR}/bench-${ID}.txt; git -C ${DIR} add . >/dev/null; git -C ${DIR} commit --quiet -m "bench ${ID}" >/dev/null; git -C ${DIR} push --quiet 2>/tmp/perf-push-err-${N}.log; exit $?'

echo ""

# ---------------------------------------------------------------------------
# Cleanup
# ---------------------------------------------------------------------------
rm -rf /tmp/perf-cc-* /tmp/perf-cf-* /tmp/perf-cp-* /tmp/perf-push-err-*

echo "=== Done ==="
