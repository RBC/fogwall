#!/usr/bin/env bash
# Sets up the SSH SCM identity enricher test scenario:
#
#   dev (fogwall user)
#     SSH key:      test/gitea/test_ed25519_2  ← this key connects to fogwall
#     SCM identity: gitea-ssh → test-user      ← enricher will look up test-user's Gitea keys
#
#   test-user (Gitea user)
#     SSH key:      test/gitea/test_ed25519    ← the ORIGINAL key (not the one we're connecting with)
#
#   otheruser (Gitea user)
#     SSH key:      test/gitea/test_ed25519_2  ← the key we ARE connecting with, but on a DIFFERENT Gitea user
#
# Expected result: enricher fetches test-user's Gitea keys, finds test_ed25519 fingerprint, does NOT match
# test_ed25519_2 fingerprint → enrichment fails → push record has empty scmUsername.
#
# This proves the enricher only resolves against SCM identities linked to the proxy user, not against
# any Gitea user who happens to have a matching key.
#
# Prerequisites:
#   docker/gitea-setup.sh and docker/gitea-ssh-setup.sh must have run first.
#
# Usage:
#   bash docker/gitea-ssh-enricher-test.sh
set -euo pipefail

GITEA_URL="${GITEA_URL:-http://localhost:3000}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
TOKENS_FILE="${REPO_ROOT}/test/gitea/tokens.env"
KEY2_PATH="${REPO_ROOT}/test/gitea/test_ed25519_2"
SSH_CONFIG="${REPO_ROOT}/test/gitea/ssh_config"

if [[ ! -f "${TOKENS_FILE}" ]]; then
    echo "ERROR: ${TOKENS_FILE} not found — run docker/gitea-setup.sh first" >&2
    exit 1
fi
# shellcheck source=/dev/null
source "${TOKENS_FILE}"

if [[ -z "${GITEA_TESTUSER_TOKEN:-}" ]]; then
    echo "ERROR: GITEA_TESTUSER_TOKEN not set in tokens.env — run docker/gitea-setup.sh" >&2
    exit 1
fi

# ---------------------------------------------------------------------------
# Generate a 2nd test keypair
# ---------------------------------------------------------------------------

if [[ -f "${KEY2_PATH}" ]]; then
    echo "==> test_ed25519_2 already exists, skipping key generation"
else
    echo "==> Generating 2nd test keypair: ${KEY2_PATH}"
    ssh-keygen -t ed25519 -N "" -C "enricher-test@local" -f "${KEY2_PATH}" >/dev/null
fi
PUB_KEY2="$(cat "${KEY2_PATH}.pub")"

# ---------------------------------------------------------------------------
# Create 'otheruser' in Gitea (owner of the 2nd key on the SCM side)
# ---------------------------------------------------------------------------

echo "==> Creating Gitea user 'otheruser'..."
docker compose -f "${REPO_ROOT}/docker/docker-compose.yml" exec gitea \
    /sbin/su-exec git gitea admin user create \
    --username otheruser \
    --password "Other1234!" \
    --email "otheruser@example.com" \
    --must-change-password=false 2>&1 || echo "    (already exists, continuing)"

# Generate an API token for otheruser so we can register the SSH key
OTHERUSER_TOKEN=$(docker compose -f "${REPO_ROOT}/docker/docker-compose.yml" exec gitea \
    /sbin/su-exec git gitea admin user generate-access-token \
    --username otheruser \
    --token-name "enricher-test" \
    --scopes "read:user,write:user" 2>&1 | grep -oE '[0-9a-f]{40}' | head -1 || true)

if [[ -z "${OTHERUSER_TOKEN}" ]]; then
    echo "    (token may already exist — continuing)"
else
    echo "    Token generated for otheruser"
fi

# ---------------------------------------------------------------------------
# Register the 2nd key with 'otheruser' on Gitea
# ---------------------------------------------------------------------------

echo "==> Registering test_ed25519_2 with Gitea 'otheruser'..."

# Try with generated token first; fall back to admin basic auth
if [[ -n "${OTHERUSER_TOKEN:-}" ]]; then
    AUTH_HEADER="Authorization: token ${OTHERUSER_TOKEN}"
    AUTH_URL="${GITEA_URL}/api/v1/user/keys"
else
    AUTH_HEADER="Authorization: Basic $(echo -n "otheruser:Other1234!" | base64)"
    AUTH_URL="${GITEA_URL}/api/v1/user/keys"
fi

http_code=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "${AUTH_HEADER}" \
    -H "Content-Type: application/json" \
    -X POST "${AUTH_URL}" \
    -d "{\"key\": \"${PUB_KEY2}\", \"title\": \"enricher-test\", \"read_only\": false}")

if [[ "${http_code}" == "201" ]]; then
    echo "    Registered test_ed25519_2 with otheruser"
elif [[ "${http_code}" == "422" ]]; then
    echo "    Already registered, skipping"
else
    echo "WARNING: Could not register key with otheruser (HTTP ${http_code}) — manual registration may be needed" >&2
fi

# Update the SSH config to use the 2nd key
cat > "${SSH_CONFIG}" <<EOF
# Local SSH config for fogwall SSH enricher test.
# Do not commit — this file is gitignored.
# Use via: GIT_SSH_COMMAND="ssh -F ${SSH_CONFIG}" git push ...
Host localhost
    ForwardAgent yes
    AddKeysToAgent yes
    IdentityFile ${KEY2_PATH}
    StrictHostKeyChecking no
    UserKnownHostsFile /dev/null
EOF

echo ""
echo "==> Done. Enricher test setup complete."
echo ""
echo "    test_ed25519_2 fingerprint: $(ssh-keygen -l -f "${KEY2_PATH}.pub" | awk '{print $2}')"
echo ""
echo "    What this tests:"
echo "      • dev (fogwall user) connects with test_ed25519_2"
echo "      • fogwall SCM identity: gitea-ssh → test-user"
echo "      • Enricher fetches test-user's Gitea keys (test_ed25519) — no match"
echo "      • otheruser HAS test_ed25519_2 on Gitea — but enricher ignores them"
echo "      • Push succeeds; push record has empty scmUsername"
echo ""
echo "    Next steps:"
echo "      1. Add test_ed25519_2 to dev user's ssh-keys in fogwall-local.yml:"
echo "            ssh-keys:"
echo "              - public-key: \"${PUB_KEY2}\""
echo "      2. Restart fogwall"
echo "      3. Push:"
echo "            source test/gitea/env.sh"
echo "            GIT_SSH_COMMAND=\"ssh -F ${SSH_CONFIG}\" git push fogwall-ssh main"
echo "      4. Check logs for:"
echo "            SSH fingerprint cache miss for gitea-ssh/test-user"
echo "            [then empty scmUsername in the push record]"
