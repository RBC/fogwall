#!/usr/bin/env bash
# Deletes GHCR package versions whose tags are exclusively build-* (ephemeral per-commit
# images). Versions carrying any other tag (v*, edge, latest, etc.) are never touched.
#
# Runs via .github/workflows/cleanup-interim-images.yml (weekly + workflow_dispatch), using the
# default GITHUB_TOKEN with the packages:write job permission - confirmed working for org-owned
# packages once the /orgs/ (not /users/) endpoint is used. For local runs against an org-owned
# package, GitHub Apps cannot delete package versions at all (confirmed with GitHub support) and
# this org blocks classic PATs, so a token from an account GitHub does permit here is required -
# GITHUB_TOKEN only works from within an Actions run.
#
# Usage: GH_TOKEN=<token> ./scripts/cleanup-interim-images.sh [owner] [package ...]
# Defaults: owner/packages resolved from the current repo (dashboard + "-server" image).

set -euo pipefail

OWNER="${1:-$(gh repo view --json owner -q .owner.login)}"
shift || true

if [ "$#" -gt 0 ]; then
  PACKAGES=("$@")
else
  BASE_PACKAGE="$(gh repo view --json name -q .name)"
  PACKAGES=("${BASE_PACKAGE}" "${BASE_PACKAGE}-server")
fi

: "${GH_TOKEN:?GH_TOKEN must be set to a token with org package delete rights}"

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT
VERSIONS_FILE="${WORKDIR}/versions.json"
ERR_FILE="${WORKDIR}/err.txt"
DELETE_ERR_FILE="${WORKDIR}/delete_err.txt"

TOTAL_DELETED=0
TOTAL_KEPT=0

for PACKAGE in "${PACKAGES[@]}"; do
  echo "Fetching package versions for ${OWNER}/${PACKAGE}..."

  if ! gh api \
    -H "Accept: application/vnd.github+json" \
    "/orgs/${OWNER}/packages/container/${PACKAGE}/versions" \
    --paginate > "$VERSIONS_FILE" 2>"$ERR_FILE"; then
    echo "  could not list versions for ${PACKAGE}, skipping:"
    cat "$ERR_FILE"
    echo ""
    continue
  fi

  TOTAL=$(jq 'length' "$VERSIONS_FILE")
  echo "Found ${TOTAL} total package versions."
  echo ""

  DELETED=0
  KEPT=0

  while IFS= read -r version; do
    VERSION_ID=$(echo "$version" | jq -r '.id')
    TAGS=$(echo "$version" | jq -r '.metadata.container.tags // [] | .[]')

    # Skip untagged versions — never delete them (likely referenced by a manifest list)
    if [ -z "$TAGS" ]; then
      echo "KEEP  [${VERSION_ID}] (untagged)"
      KEPT=$((KEPT + 1))
      continue
    fi

    TAG_LIST=$(echo "$TAGS" | tr '\n' ' ' | sed 's/ $//')

    # Guard 1 (allow-list): keep if ANY tag does not match build-[0-9a-f]+ exactly.
    PROTECTED=false
    while IFS= read -r tag; do
      if [[ ! "$tag" =~ ^build-[0-9a-f]+$ ]]; then
        PROTECTED=true
        break
      fi
    done <<< "$TAGS"

    # Guard 2 (deny-list, independent of guard 1): keep if ANY tag looks like a real
    # release/promotion tag (semver, major, minor, latest, edge). This is deliberately a second,
    # separately-written check — a bug in guard 1's regex alone must not be able to delete a
    # production tag; both guards have to agree it's safe before anything is deleted.
    FORBIDDEN=false
    while IFS= read -r tag; do
      if [[ "$tag" =~ ^v?[0-9]+(\.[0-9]+){0,2}(-.+)?$ ]] || [ "$tag" = "latest" ] || [ "$tag" = "edge" ]; then
        FORBIDDEN=true
        break
      fi
    done <<< "$TAGS"

    if [ "$PROTECTED" = true ] || [ "$FORBIDDEN" = true ]; then
      echo "KEEP  [${VERSION_ID}] tags: ${TAG_LIST}"
      KEPT=$((KEPT + 1))
    else
      echo "DELETE [${VERSION_ID}] tags: ${TAG_LIST}"
      if gh api \
        --method DELETE \
        -H "Accept: application/vnd.github+json" \
        "/orgs/${OWNER}/packages/container/${PACKAGE}/versions/${VERSION_ID}" \
        >"$DELETE_ERR_FILE" 2>&1; then
        DELETED=$((DELETED + 1))
      elif grep -q '"status":"404"' "$DELETE_ERR_FILE"; then
        # Already gone (e.g. superseded by a concurrent release promotion) — not a failure.
        echo "  already deleted, skipping"
        DELETED=$((DELETED + 1))
      else
        echo "  delete failed:"
        cat "$DELETE_ERR_FILE"
        exit 1
      fi
    fi
  done < <(jq -c '.[]' "$VERSIONS_FILE")

  echo ""
  echo "${PACKAGE}: deleted ${DELETED}, kept ${KEPT}."
  echo ""
  TOTAL_DELETED=$((TOTAL_DELETED + DELETED))
  TOTAL_KEPT=$((TOTAL_KEPT + KEPT))
done

echo "Cleanup complete. Deleted ${TOTAL_DELETED}, kept ${TOTAL_KEPT} across all packages."
