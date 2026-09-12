#!/usr/bin/env bash
# GitLab MR mutations through the fogwall proposals listener (:8482) → REST /api/v4 against gitlab.com.
# Needs: GLAB_PAT = a gitlab PAT with 'api' scope. Opens/edits/merges REAL MRs on $OWNER/$REPO.
set -uo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_common.sh"
: "${GLAB_PAT:?set GLAB_PAT to a gitlab PAT (api scope)}"
require_cert
HOST="localhost:8482"

make_head_branches "https://oauth2:$GLAB_PAT@gitlab.com/$SLUG.git" glab

# `glab mr create` insists on a git remote pointing at GITLAB_HOST; it is never used for git.
git remote add glab-proxy-do-not-use "https://$HOST/$SLUG.git"

export SSL_CERT_FILE="$CERT" GITLAB_HOST="$HOST"

echo "Logging glab in to the fogwall host..."
printf '%s\n' "$GLAB_PAT" | glab auth login --hostname "$HOST" --api-protocol https --git-protocol https --insecure-storage --stdin

echo "Creating merge request..."
MR="$(glab mr create -R "$SLUG" --source-branch "$BR_LC" --target-branch main --title "Proposed via glab" --description "created via fogwall" --no-editor --yes | num)"
require_id "$MR"
echo "opened MR !$MR"

echo "Updating title..."
glab mr update "$MR" -R "$SLUG" --title "Proposed via glab (edited)"

echo "Adding a note..."
glab mr note "$MR" -R "$SLUG" -m "comment via fogwall"

echo "Closing..."
glab mr close "$MR" -R "$SLUG"

echo "Creating a second MR to merge..."
MR2="$(glab mr create -R "$SLUG" --source-branch "$BR_MG" --target-branch main --title "Merge via glab" --description "will merge via fogwall" --no-editor --yes | num)"
require_id "$MR2"

echo "Updating it with a blocked term (expect a rejection)..."
glab mr update "$MR2" -R "$SLUG" --description "runbook: internal.corp.example.com"

echo "Merging MR !$MR2..."
glab mr merge "$MR2" -R "$SLUG" --yes
echo "merged MR !$MR2"

echo "done — watch: docker compose logs -f otel-collector  |  Jaeger http://localhost:16686"
