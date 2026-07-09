#!/usr/bin/env bash
# Golden-path store-and-forward push to local Gitea via fogwall.
# Requires: docker compose stack up + docker/gitea-setup.sh already run.
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/env.sh"
source "$(dirname "${BASH_SOURCE[0]}")/gitea/tokens.env"

GITEA_HOST="${GITEA_HOST:-localhost:3000}"
GIT_USERNAME="${GIT_USERNAME:-me}"
GIT_PASSWORD="${GITEA_TESTUSER_TOKEN}"
PUSH_URL="http://${GIT_USERNAME}:${GIT_PASSWORD}@localhost:8080/push/${GITEA_HOST}/test-owner/test-repo.git"
TEST_BRANCH="test/gitea-pass-$(date +%s)"
REPO_DIR=$(mktemp -d "${_SYS_TMPDIR}/gitea-push-pass-XXXX")

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

echo "pass - $(date)" >> test-file.txt
git add test-file.txt
git commit -m "feat: golden-path gitea store-and-forward test"
git push origin "${TEST_BRANCH}"

echo "PASSED"
