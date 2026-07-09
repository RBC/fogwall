#!/usr/bin/env bash
# Failure-path store-and-forward push to local Gitea via fogwall.
# Verifies secret scanning correctly rejects a commit containing an AWS key.
# Requires: docker compose stack up + docker/gitea-setup.sh already run.
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/env.sh"
source "$(dirname "${BASH_SOURCE[0]}")/gitea/tokens.env"

GITEA_HOST="${GITEA_HOST:-localhost:3000}"
GIT_USERNAME="${GIT_USERNAME:-me}"
GIT_PASSWORD="${GITEA_TESTUSER_TOKEN}"
PUSH_URL="http://${GIT_USERNAME}:${GIT_PASSWORD}@localhost:8080/push/${GITEA_HOST}/test-owner/test-repo.git"
TEST_BRANCH="test/gitea-fail-$(date +%s)"
REPO_DIR=$(mktemp -d "${_SYS_TMPDIR}/gitea-push-fail-XXXX")

cleanup() {
    git -C "${REPO_DIR}" remote set-url origin "http://${GIT_USERNAME}:${GIT_PASSWORD}@${GITEA_HOST}/test-owner/test-repo.git" 2>/dev/null || true
    git -C "${REPO_DIR}" push origin --delete "${TEST_BRANCH}" 2>/dev/null || true
    safe_rm_rf "${REPO_DIR}"
}
trap cleanup EXIT

git clone "${PUSH_URL}" "${REPO_DIR}"
cd "${REPO_DIR}"
git checkout -b "${TEST_BRANCH}"
git config user.name "test-user"
git config user.email "testuser@example.com"

cat > aws-credentials << 'EOF'
[default]
aws_access_key_id = AKIAYVP4CIPPH3TESTKEY
aws_secret_access_key = wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
EOF
git add aws-credentials
git commit -m "chore: add deployment credentials"

push_exit=0
git push origin "${TEST_BRANCH}" 2>&1 || push_exit=$?

if [[ ${push_exit} -ne 0 ]]; then
    echo "PASSED (push correctly rejected by secret scanning)"
else
    echo "FAILED (push should have been rejected)"
    exit 1
fi
