#!/usr/bin/env bash
# Stop all locally-running perf services.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "==> Stopping services..."

if [ -f "${SCRIPT_DIR}/.fogwall.pid" ]; then
    PID=$(cat "${SCRIPT_DIR}/.fogwall.pid")
    kill "${PID}" 2>/dev/null && echo "    fogwall stopped (pid ${PID})" || echo "    fogwall already stopped"
    rm -f "${SCRIPT_DIR}/.fogwall.pid"
    cd "${REPO_ROOT}" && ./gradlew :fogwall-server:stop 2>/dev/null || true
fi

if [ -f "${SCRIPT_DIR}/.git-proxy.pid" ]; then
    PID=$(cat "${SCRIPT_DIR}/.git-proxy.pid")
    kill "${PID}" 2>/dev/null && echo "    finos/git-proxy stopped (pid ${PID})" || echo "    finos/git-proxy already stopped"
    rm -f "${SCRIPT_DIR}/.git-proxy.pid"
fi

docker rm -f perf-gitea 2>/dev/null && echo "    Gitea stopped" || echo "    Gitea already stopped"

rm -f "${SCRIPT_DIR}/.git-proxy-local.json"
rm -f "${REPO_ROOT}/fogwall-server/src/main/resources/fogwall-perf.yml"

echo "==> Done."
