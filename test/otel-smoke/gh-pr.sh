#!/usr/bin/env bash
# GitHub PR mutations through the fogwall SCM API listener (:8481) → GraphQL against github.com.
# Needs: GH_PAT = a github classic PAT with 'repo' scope. Opens/edits/merges REAL PRs on $OWNER/$REPO.
set -uo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_common.sh"
: "${GH_PAT:?set GH_PAT to a github classic PAT (repo scope)}"
require_cert
HOST="localhost:8481"
R="$HOST/$SLUG"

make_head_branches "https://$OWNER:$GH_PAT@github.com/$SLUG.git" gh

export SSL_CERT_FILE="$CERT" GH_HOST="$HOST" GH_ENTERPRISE_TOKEN="$GH_PAT" \
  GH_CONFIG_DIR="$WORK/gh" GH_PROMPT_DISABLED=1 GH_NO_UPDATE_NOTIFIER=1

# gh must run from the checked-out head branch or it prompts "where should we push the branch?"
git switch -q "$BR_LC"

echo "Creating pull request..."
PR="$(gh pr create -R "$R" --base main --head "$BR_LC" --title "OTel smoke PR" --body "created via fogwall" | num)"
require_id "$PR"
echo "opened PR #$PR"

echo "Editing title + body..."
gh pr edit "$PR" -R "$R" --title "OTel smoke PR (edited)" --body "edited via fogwall"

echo "Commenting..."
gh pr comment "$PR" -R "$R" --body "comment via fogwall"

echo "Assigning..."
gh pr edit "$PR" -R "$R" --add-assignee "$OWNER"

echo "Adding + removing a label..."
gh pr edit "$PR" -R "$R" --add-label bug
gh pr edit "$PR" -R "$R" --remove-label bug

echo "Closing..."
gh pr close "$PR" -R "$R"

echo "Creating a second PR to merge..."
git switch -q "$BR_MG"
PR2="$(gh pr create -R "$R" --base main --head "$BR_MG" --title "OTel smoke merge PR" --body "will merge via fogwall" | num)"
require_id "$PR2"

echo "Editing it with a blocked term (expect a rejection)..."
gh pr edit "$PR2" -R "$R" --body "runbook: internal.corp.example.com"

echo "Merging PR #$PR2..."
gh pr merge "$PR2" -R "$R" --merge
echo "merged PR #$PR2"

echo "done — watch: docker compose logs -f otel-collector  |  Jaeger http://localhost:16686"
