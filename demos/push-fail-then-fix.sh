#!/usr/bin/env bash
# Demo: Store-and-forward push with bad message, then fix and repush
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/env.sh"
resolve_pat ~/.github-pat
GIT_REPO=${GIT_REPO:-"github.com/coopernetes/test-repo.git"}

PUSH_URL="http://${GIT_USERNAME}:${GIT_PASSWORD}@localhost:8080/push/${GIT_REPO}"
TEST_BRANCH="test/push-fix-msg-$(date +%s)"
REPO_DIR=$(mktemp -d "${TMPDIR:-/tmp}/push-fix-msg-XXXX")

cleanup() {
    git -C "${REPO_DIR}" remote set-url origin "http://${GIT_USERNAME}:${GIT_PASSWORD}@${GIT_REPO}" 2>/dev/null || true
    git -C "${REPO_DIR}" push origin --delete "${TEST_BRANCH}" 2>/dev/null || true
    safe_rm_rf "${REPO_DIR}"
}
trap cleanup EXIT

xdotool type --delay 50 "#→ Cloning repository (${PUSH_URL//${GIT_PASSWORD}/***}) via fogwall..."
echo ""
git clone "${PUSH_URL}" "${REPO_DIR}"
sleep 1

cd "${REPO_DIR}"
xdotool type --delay 50 "#→ Creating feature branch..."
echo ""
git checkout -b "${TEST_BRANCH}"
sleep 1

git config user.name "${GIT_AUTHOR_NAME}"
git config user.email "${GIT_EMAIL}"

xdotool type --delay 50 "#→ Making a commit with INVALID message (WIP flag)..."
echo ""
echo "wip work - $(date)" >> test-file.txt
git add test-file.txt
git commit -m "WIP: still working on this"
sleep 2

xdotool type --delay 50 "#→ Attempting push (will REJECT invalid message)..."
echo ""
git push origin "${TEST_BRANCH}" 2>&1 || true
sleep 5
clear

xdotool type --delay 50 "#→ Fixing the commit message..."
echo ""
git commit --amend -m "feat: complete implementation"
sleep 1

xdotool type --delay 50 "#→ Re-pushing with valid message..."
echo ""
git push origin "${TEST_BRANCH}" --force-with-lease
sleep 5

