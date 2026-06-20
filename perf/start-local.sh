#!/usr/bin/env bash
# Start all services locally for benchmarking (no Docker build required).
#
# Prerequisites:
#   - Docker (for Gitea only — pre-built image, no build)
#   - Java 21+ (for fogwall via Gradle)
#   - Node.js 18+ (for finos/git-proxy — optional, only if benchmarking both)
#
# Usage:
#   bash perf/start-local.sh
#   bash perf/setup.sh --local
#   bash perf/bench.sh
#   bash perf/stop-local.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
FINOS_DIR="${FINOS_GIT_PROXY_DIR:-/Users/544820491/code/opensource/finos-git-proxy}"

# ── Gitea ──────────────────────────────────────────────────────────────────
echo "==> Starting Gitea..."
docker rm -f perf-gitea 2>/dev/null || true
docker run -d \
    --name perf-gitea \
    -p 3000:3000 \
    -e GITEA__security__INSTALL_LOCK=true \
    -e GITEA__security__SECRET_KEY=perf-test-secret \
    -e GITEA__server__HTTP_PORT=3000 \
    -e GITEA__database__DB_TYPE=sqlite3 \
    -e GITEA__log__LEVEL=Warn \
    -e GITEA__repository__DEFAULT_BRANCH=main \
    docker.io/gitea/gitea:1.25-rootless

echo "    Waiting for Gitea to be healthy..."
until curl -sf http://localhost:3000/api/healthz -o /dev/null 2>&1; do sleep 2; done
echo "    Gitea ready at http://localhost:3000"

# ── fogwall ────────────────────────────────────────────────────────────────
echo "==> Starting fogwall..."

# The config loader reads fogwall-{profile}.yml from the classpath.
# Symlink the perf config into resources so Gradle's run task picks it up.
ln -sf "${SCRIPT_DIR}/fogwall-perf.yml" "${REPO_ROOT}/fogwall-server/src/main/resources/fogwall-perf.yml"

export FOGWALL_CONFIG_PROFILES=perf
# Override the Gitea URI to localhost (the YAML file says http://gitea:3000 for Docker networking)
export FOGWALL_PROVIDERS_GITEA_URI=http://localhost:3000

cd "${REPO_ROOT}"
./gradlew :fogwall-server:run &
FOGWALL_PID=$!
echo "${FOGWALL_PID}" > "${SCRIPT_DIR}/.fogwall.pid"

echo "    Waiting for fogwall to be healthy..."
until curl -sf http://localhost:8080/api/health -o /dev/null 2>&1; do sleep 2; done
echo "    fogwall ready at http://localhost:8080"

# ── finos/git-proxy (optional) ────────────────────────────────────────────
if [ -d "${FINOS_DIR}" ]; then
    echo "==> Starting finos/git-proxy from ${FINOS_DIR}..."

    # Generate a local config pointing at localhost Gitea
    cat > "${SCRIPT_DIR}/.git-proxy-local.json" <<'PERF_CONFIG'
{
  "cookieSecret": "perf-test-secret",
  "csrfProtection": false,
  "serverPort": 8000,
  "rateLimit": { "windowMs": 60000, "limit": 10000 },
  "tempPassword": { "sendEmail": false, "emailConfig": {} },
  "authorisedList": [
    {
      "project": "perf",
      "name": "bench-repo",
      "url": "http://localhost:3000/perf/bench-repo.git"
    }
  ],
  "sink": [{ "type": "fs", "enabled": true }],
  "authentication": [{ "type": "local", "enabled": true }],
  "commitConfig": {
    "author": { "email": { "local": { "block": "" }, "domain": { "allow": ".*" } } },
    "message": { "block": { "literals": [], "patterns": [] } },
    "diff": { "block": { "literals": [], "patterns": [], "providers": {} } }
  }
}
PERF_CONFIG

    cd "${FINOS_DIR}"
    GIT_PROXY_SERVER_PORT=8000 node dist/index.js --config "${SCRIPT_DIR}/.git-proxy-local.json" &
    GP_PID=$!
    echo "${GP_PID}" > "${SCRIPT_DIR}/.git-proxy.pid"

    echo "    Waiting for finos/git-proxy to be healthy..."
    for i in $(seq 1 30); do
        if curl -sf http://localhost:8000 -o /dev/null 2>&1; then break; fi
        sleep 2
    done
    echo "    finos/git-proxy ready at http://localhost:8000"
else
    echo "==> Skipping finos/git-proxy (set FINOS_GIT_PROXY_DIR to enable)"
fi

echo ""
echo "==> All services started!"
echo "    Next: bash perf/setup.sh --local"
echo "    Then: bash perf/bench.sh"
echo "    Stop: bash perf/stop-local.sh"
