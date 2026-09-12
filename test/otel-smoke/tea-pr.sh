#!/usr/bin/env bash
# Gitea PR mutations through the fogwall proposals listener (:8484) → REST /api/v1 against gitea.com.
# Needs: GITEA_PAT = a gitea.com token with write:issue + write:repository. Opens/edits REAL PRs on $OWNER/$REPO.
set -uo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_common.sh"
: "${GITEA_PAT:?set GITEA_PAT to a gitea.com token (write:issue, write:repository)}"
require_cert
HOST="https://localhost:8484"

make_head_branches "https://$OWNER:$GITEA_PAT@gitea.com/$SLUG.git" tea

export SSL_CERT_FILE="$CERT" XDG_CONFIG_HOME="$WORK/tea"

echo "Logging tea in..."
GITEA_SERVER_TOKEN="$GITEA_PAT" tea login add --name fogwall --url "$HOST"
AT=(--login fogwall --repo "$SLUG")

echo "Creating pull request..."
PR="$(tea pr create "${AT[@]}" --head "$BR_LC" --base main --title "Proposed via tea" --description "created via fogwall" | num)"
require_id "$PR"
echo "opened PR #$PR"

echo "Editing title..."
tea pr edit "$PR" "${AT[@]}" --title "Proposed via tea (edited)"

echo "Closing..."
tea pr close "$PR" "${AT[@]}"

echo "Creating one with a blocked term (expect a rejection)..."
tea pr create "${AT[@]}" --head "$BR_MG" --base main --title "Runbook" --description "see internal.corp.example.com"

# tea pr merge exists in recent tea; verify before enabling (needs the MERGE grant):
# tea pr merge "$PR2" "${AT[@]}"

echo "done — watch: docker compose logs -f otel-collector  |  Jaeger http://localhost:16686"
