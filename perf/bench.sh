#!/usr/bin/env bash
# Performance benchmark for a single git proxy against a direct Gitea baseline.
# Requires: hyperfine (brew install hyperfine), git
#
# Usage:
#   bash perf/bench.sh fogwall     # benchmark fogwall
#   bash perf/bench.sh git-proxy   # benchmark finos/git-proxy
#
# Results are saved to perf/results/{proxy-name}/ as markdown and JSON.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ ! -f "${SCRIPT_DIR}/.env" ]; then
    echo "ERROR: Run perf/setup.sh first" >&2
    exit 1
fi
source "${SCRIPT_DIR}/.env"

if ! command -v hyperfine &>/dev/null; then
    echo "ERROR: hyperfine not found. Install with: brew install hyperfine" >&2
    exit 1
fi

PROXY="${1:?Usage: bench.sh <fogwall|git-proxy>}"
RUNS="${RUNS:-10}"
WARMUP="${WARMUP:-2}"
RESULTS_DIR="${SCRIPT_DIR}/results/${PROXY}"
mkdir -p "${RESULTS_DIR}"

GIT_PASSWORD="${TOKEN:-${TEST_PASSWORD}}"
CREDS="${TEST_USER}:${GIT_PASSWORD}"
DIRECT="http://${TEST_USER}:${TEST_PASSWORD}@localhost:3000/${ORG}/${REPO}.git"

# Build the proxy clone URL based on which proxy we're benchmarking
case "${PROXY}" in
    fogwall)
        GITEA_HOST=$(echo "${GITEA_URL}" | sed 's|.*://||;s|:.*||')
        PROXY_URL="http://${CREDS}@localhost:8080/proxy/${GITEA_HOST}/${ORG}/${REPO}.git"
        ;;
    git-proxy)
        # finos/git-proxy routes by upstream origin in the path: /localhost:3000/org/repo.git
        GITEA_HOST_PORT=$(echo "${GITEA_URL}" | sed 's|.*://||')
        PROXY_URL="http://${CREDS}@localhost:8000/${GITEA_HOST_PORT}/${ORG}/${REPO}.git"
        ;;
    *)
        echo "ERROR: Unknown proxy '${PROXY}'. Use 'fogwall' or 'git-proxy'." >&2
        exit 1
        ;;
esac

echo "Proxy:    ${PROXY}"
echo "Runs:     ${RUNS} (warmup: ${WARMUP})"
echo "Direct:   ${DIRECT//${CREDS}/<creds>}"
echo "Proxy:    ${PROXY_URL//${CREDS}/<creds>}"

export GIT_TERMINAL_PROMPT=0

# ---------------------------------------------------------------------------
# Clone benchmark
# ---------------------------------------------------------------------------
echo ""
echo "=== Clone benchmark ==="
echo ""

hyperfine \
    --warmup "${WARMUP}" \
    --runs "${RUNS}" \
    --prepare "rm -rf /tmp/perf-clone-*" \
    --export-json "${RESULTS_DIR}/clone.json" \
    --export-markdown "${RESULTS_DIR}/clone.md" \
    -n "direct (gitea)" \
        "git clone --quiet ${DIRECT} /tmp/perf-clone-direct 2>/dev/null" \
    -n "${PROXY}" \
        "git clone --quiet ${PROXY_URL} /tmp/perf-clone-proxy 2>/dev/null"

# ---------------------------------------------------------------------------
# Fetch benchmark
# ---------------------------------------------------------------------------
echo ""
echo "=== Fetch benchmark ==="
echo ""

rm -rf /tmp/perf-fetch-direct /tmp/perf-fetch-proxy
git clone --quiet "${DIRECT}" /tmp/perf-fetch-direct 2>/dev/null
git clone --quiet "${PROXY_URL}" /tmp/perf-fetch-proxy 2>/dev/null

hyperfine \
    --warmup "${WARMUP}" \
    --runs "${RUNS}" \
    --export-json "${RESULTS_DIR}/fetch.json" \
    --export-markdown "${RESULTS_DIR}/fetch.md" \
    -n "direct (gitea)" \
        "git -C /tmp/perf-fetch-direct fetch --quiet 2>/dev/null" \
    -n "${PROXY}" \
        "git -C /tmp/perf-fetch-proxy fetch --quiet 2>/dev/null"

# ---------------------------------------------------------------------------
# Rapid fetch (cache behavior — 50 runs, no warmup)
# ---------------------------------------------------------------------------
echo ""
echo "=== Rapid fetch benchmark (50 runs, cache behavior) ==="
echo ""

hyperfine \
    --warmup 0 \
    --runs 50 \
    --export-json "${RESULTS_DIR}/fetch-rapid.json" \
    --export-markdown "${RESULTS_DIR}/fetch-rapid.md" \
    -n "direct (gitea)" \
        "git -C /tmp/perf-fetch-direct fetch --quiet 2>/dev/null" \
    -n "${PROXY}" \
        "git -C /tmp/perf-fetch-proxy fetch --quiet 2>/dev/null"

# ---------------------------------------------------------------------------
# Push benchmark
# ---------------------------------------------------------------------------
echo ""
echo "=== Push benchmark ==="
echo ""

rm -rf /tmp/perf-push-direct /tmp/perf-push-proxy
git clone --quiet "${DIRECT}" /tmp/perf-push-direct 2>/dev/null
git clone --quiet "${PROXY_URL}" /tmp/perf-push-proxy 2>/dev/null

for d in /tmp/perf-push-direct /tmp/perf-push-proxy; do
    git -C "$d" config user.email "${TEST_EMAIL:-benchuser@example.com}"
    git -C "$d" config user.name "${TEST_USER}"
    git -C "$d" checkout -b "bench-$(basename $d)-$$" --quiet 2>/dev/null
    git -C "$d" push --set-upstream origin "bench-$(basename $d)-$$" --quiet 2>/dev/null
done

for label in direct proxy; do
    cat > "/tmp/perf-push-${label}.sh" <<SCRIPT
#!/usr/bin/env bash
set -euo pipefail
DIR="/tmp/perf-push-${label}"
ID=\$(date +%s%N)
dd if=/dev/urandom bs=256 count=1 2>/dev/null | base64 > "\${DIR}/bench-\${ID}.txt"
git -C "\${DIR}" add . >/dev/null
git -C "\${DIR}" commit --quiet -m "bench \${ID}" >/dev/null
git -C "\${DIR}" push --quiet 2>/dev/null
SCRIPT
    chmod +x "/tmp/perf-push-${label}.sh"
done

hyperfine \
    --warmup "${WARMUP}" \
    --runs "${RUNS}" \
    --export-json "${RESULTS_DIR}/push.json" \
    --export-markdown "${RESULTS_DIR}/push.md" \
    -n "direct (gitea)" \
        "bash /tmp/perf-push-direct.sh" \
    -n "${PROXY}" \
        "bash /tmp/perf-push-proxy.sh"

# ---------------------------------------------------------------------------
# Cleanup
# ---------------------------------------------------------------------------
rm -rf /tmp/perf-clone-* /tmp/perf-fetch-* /tmp/perf-push-*
rm -f /tmp/perf-push-direct.sh /tmp/perf-push-proxy.sh

echo ""
echo "=== Results saved to ${RESULTS_DIR}/ ==="
ls -1 "${RESULTS_DIR}/"
