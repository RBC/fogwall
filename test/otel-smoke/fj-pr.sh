#!/usr/bin/env bash
# Forgejo PR mutations through the fogwall proposals listener (:8483) → REST /api/v1 against codeberg.org.
# Needs: CODEBERG_PAT = a codeberg token with write:issue + write:repository. Opens/edits REAL PRs on $OWNER/$REPO.
set -uo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_common.sh"
: "${CODEBERG_PAT:?set CODEBERG_PAT to a codeberg token (write:issue, write:repository)}"
require_cert
HOST="https://localhost:8483"

make_head_branches "https://$OWNER:$CODEBERG_PAT@codeberg.org/$SLUG.git" fj

# fj infers the repo from origin for edit/close; point origin at the listener now the branch is upstream.
git remote set-url origin "$HOST/$SLUG.git"

export SSL_CERT_FILE="$CERT" XDG_CONFIG_HOME="$WORK/fj" XDG_DATA_HOME="$WORK/fj" # fj keeps logins under DATA

echo "Adding the fj token..."
printf '%s\n' "$CODEBERG_PAT" | fj auth add-token -H "$HOST"

echo "Creating pull request..."
PR="$(fj -H "$HOST" pr create "Proposed via fj" --body "created via fogwall" --head "$BR_LC" --base main --repo "$SLUG" | num)"
require_id "$PR"
echo "opened PR #$PR"

echo "Editing title..."
fj -H "$HOST" pr edit "$PR" title "Proposed via fj (edited)"

echo "Closing..."
fj -H "$HOST" pr close "$PR"

echo "Creating one with a blocked term (expect a rejection)..."
fj -H "$HOST" pr create "Runbook" --body "see internal.corp.example.com" --head "$BR_MG" --base main --repo "$SLUG"

# fj's merge/comment subcommand syntax varies by version; verify before enabling (needs the MERGE grant):
# fj -H "$HOST" pr merge "$PR2"

echo "done — watch: docker compose logs -f otel-collector  |  Jaeger http://localhost:16686"
