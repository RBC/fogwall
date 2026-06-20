#!/usr/bin/env bash
# One-time setup: create Gitea admin, org, repo, and test user for benchmarking.
# Requires Gitea running via: docker compose -f perf/docker-compose.yml up -d gitea
#
# Usage:
#   bash perf/setup.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

GITEA_URL="http://localhost:3000"
ADMIN_USER="perfadmin"
ADMIN_PASSWORD="Admin1234!"
ADMIN_EMAIL="perfadmin@example.com"
TEST_USER="benchuser"
TEST_PASSWORD="Bench1234!"
TEST_EMAIL="benchuser@example.com"
ORG="perf"
REPO="bench-repo"

if [ -n "${COMPOSE:-}" ]; then
    :
elif command -v docker &>/dev/null && docker info &>/dev/null 2>&1; then
    COMPOSE="docker compose"
elif command -v podman &>/dev/null && podman info &>/dev/null 2>&1; then
    COMPOSE="podman compose"
else
    echo "No working container runtime found (tried docker, podman)" >&2
    exit 1
fi
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.yml"

echo "==> Waiting for Gitea..."
until curl -sf "${GITEA_URL}/api/healthz" -o /dev/null 2>&1; do sleep 2; done
echo "    Gitea ready."

create_user() {
    local username="$1" password="$2" email="$3" admin_flag="${4:-}"
    $COMPOSE -f "${COMPOSE_FILE}" exec gitea gitea admin user create \
        ${admin_flag} --username "${username}" --password "${password}" \
        --email "${email}" --must-change-password=false 2>&1 || echo "    (exists)"
}

generate_token() {
    local username="$1" token_name="$2"
    $COMPOSE -f "${COMPOSE_FILE}" exec gitea gitea admin user generate-access-token \
        --username "${username}" --token-name "${token_name}" \
        --scopes "read:user,write:repository" 2>&1 | grep -oE '[0-9a-f]{40}' | head -1
}

gitea_api() {
    curl -sf -u "${ADMIN_USER}:${ADMIN_PASSWORD}" \
        -H "Content-Type: application/json" \
        "${GITEA_URL}/api/v1/$@"
}

echo "==> Creating admin user..."
create_user "${ADMIN_USER}" "${ADMIN_PASSWORD}" "${ADMIN_EMAIL}" "--admin"

echo "==> Creating test user..."
create_user "${TEST_USER}" "${TEST_PASSWORD}" "${TEST_EMAIL}"

echo "==> Generating token..."
TOKEN=$(generate_token "${TEST_USER}" "perf-bench") || true

echo "==> Creating org and repo..."
gitea_api orgs -X POST -d "{\"username\":\"${ORG}\",\"visibility\":\"public\"}" > /dev/null 2>&1 || true
gitea_api "orgs/${ORG}/repos" -X POST \
    -d "{\"name\":\"${REPO}\",\"private\":false,\"auto_init\":true,\"default_branch\":\"main\"}" > /dev/null 2>&1 || true
gitea_api "repos/${ORG}/${REPO}/collaborators/${TEST_USER}" -X PUT -d '{"permission":"write"}' > /dev/null 2>&1 || true

echo "==> Seeding repo with test data..."
TMPDIR=$(mktemp -d)
git clone --quiet "http://${TEST_USER}:${TEST_PASSWORD}@localhost:3000/${ORG}/${REPO}.git" "${TMPDIR}/repo" 2>/dev/null
cd "${TMPDIR}/repo"
git config user.email "${TEST_EMAIL}"
git config user.name "${TEST_USER}"

if [ ! -f "file-1.txt" ]; then
    for i in $(seq 1 50); do
        dd if=/dev/urandom bs=1024 count=10 2>/dev/null | base64 > "file-${i}.txt"
    done
    git add .
    git commit -m "seed: 50 files for benchmark" --quiet
    git push --quiet 2>/dev/null
    echo "    Seeded 50 files (~500KB total)"
else
    echo "    Already seeded, skipping"
fi
cd - > /dev/null
rm -rf "${TMPDIR}"

ENV_FILE="${SCRIPT_DIR}/.env"
cat > "${ENV_FILE}" <<EOF
GITEA_URL=${GITEA_URL}
TEST_USER=${TEST_USER}
TEST_PASSWORD=${TEST_PASSWORD}
TEST_EMAIL=${TEST_EMAIL}
TOKEN=${TOKEN}
ORG=${ORG}
REPO=${REPO}
EOF

echo ""
echo "==> Setup complete!"
echo "    Env written to: ${ENV_FILE}"
echo ""
echo "    Direct Gitea:  git clone http://${TEST_USER}:<password>@localhost:3000/${ORG}/${REPO}.git"
echo ""
echo "    Run benchmarks: python3 perf/bench.py <fogwall|git-proxy>"
