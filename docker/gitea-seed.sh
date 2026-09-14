#!/bin/sh
# Seeds the Docker Compose Gitea with the users, organisations and repositories the smoke suite expects.
#
# Run as the `gitea-seed` service on every `up`, after Gitea reports healthy. Every step is idempotent: a fresh
# volume gets the full fixture, an existing one is left as it is, and the script exits 0 either way so a re-run is
# never a failure.
#
# Two tools, both already in the Gitea image. Users come from the `gitea` CLI, which writes to the database
# directly. Organisations and repositories have no CLI equivalent and come from the REST API.
#
# Only what the suite uses: two accounts, one organisation, two repositories. The wider fixture docker/gitea-setup.sh
# creates — five users across two orgs and six repositories, for a LITERAL/GLOB/REGEX permission matrix — belongs to
# the layer that tests permission logic, which is PermissionE2ETest. Compose shows the gate is wired, once.
#
# Tests issue their own Gitea tokens from the passwords below — Gitea allows that with plain basic auth, unlike GitHub.
# The one token minted here is for fogwall itself: Forgejo/Gitea cannot list a user's SSH keys anonymously, so the SSH
# transport's identity verification needs a provider api-token. It is written where the ssh overlay hands it to fogwall.
set -eu

GITEA_URL="http://gitea:3000"

# Owns the orgs and repos. Deliberately NOT mapped to a fogwall user, so pushing as the admin exercises the
# unlinked-identity path.
ADMIN_USER="fogwalladmin"
ADMIN_PASSWORD="Admin1234!"
ADMIN_EMAIL="fogwalladmin@example.com"

# Each maps to a fogwall user of the same name in fogwall-docker-default.yml.
USER_PASSWORD="Test1234!"

ORG="test-owner"

log() { echo "[gitea-seed] $*"; }

create_user() {
    username="$1"
    email="$2"
    shift 2
    if gitea admin user create --username "$username" --password "$USER_PASSWORD" --email "$email" \
        --must-change-password=false "$@" >/dev/null 2>&1; then
        log "created user $username"
    else
        log "user $username already present"
    fi
}

# Calls the API as the admin, treating "already exists" (422) as success.
api() {
    method="$1"
    path="$2"
    body="$3"
    status=$(curl -s -o /dev/null -w '%{http_code}' -u "${ADMIN_USER}:${ADMIN_PASSWORD}" \
        -H 'Content-Type: application/json' -X "$method" -d "$body" "${GITEA_URL}/api/v1/${path}")
    case "$status" in
        2*) log "${method} ${path} ok" ;;
        422) : ;;
        *) log "WARNING: ${path} returned ${status}" ;;
    esac
}

log "seeding ${GITEA_URL}"

# The admin first — every API call below authenticates as them.
if gitea admin user create --username "$ADMIN_USER" --password "$ADMIN_PASSWORD" --email "$ADMIN_EMAIL" \
    --admin --must-change-password=false >/dev/null 2>&1; then
    log "created admin $ADMIN_USER"
else
    log "admin $ADMIN_USER already present"
fi

create_user "test-user" "testuser@example.com"
# No config maps this account to a fogwall user. The provisioning case links it at runtime instead, which is how a
# deployment behind an external IdP works: proxy_users is empty until someone logs in or is provisioned.
create_user "idp-user" "idpuser@example.com"

api POST "orgs" "{\"username\":\"${ORG}\",\"visibility\":\"public\"}"

# auto_init gives each repository an initial commit on main, so a clone has something to branch from.
#   test-repo    — test-user holds a LITERAL grant on it, so pushes and pull requests are allowed
#   test-repo-2  — no grant, which is what the permission refusal is demonstrated against
for repo in test-repo test-repo-2; do
    api POST "orgs/${ORG}/repos" "{\"name\":\"${repo}\",\"auto_init\":true,\"default_branch\":\"main\"}"
done

api PUT "repos/${ORG}/test-repo/collaborators/idp-user" '{"permission":"write"}'

# test-user pushes to /test-owner/test-repo, which means write access on the Gitea side as well as a fogwall grant.
api PUT "repos/${ORG}/test-repo/collaborators/test-user" '{"permission":"write"}'

# The compose SSH smoke test's throwaway public key (test/ssh/compose_ed25519.pub), registered for test-user so the
# agent fogwall forwards authenticates the upstream push, and so the SSH identity enricher finds it under test-user's
# Gitea login. Harmless when the ssh profile is not active. Public key only — the private half lives in the repo for
# the test alone.
SSH_KEY="ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIFZpJaD4A4w0l7PY02oqUWViTg5gLhD2+i2pY47ajwO0 fogwall-compose-test-key (throwaway; local Gitea only)"
api POST "admin/users/test-user/keys" "{\"title\":\"compose-smoke\",\"key\":\"${SSH_KEY}\",\"read_only\":false}"

# A provider api-token so fogwall's SSH identity verification can list test-user's keys (Forgejo/Gitea require auth for
# that). Written to the config volume, which the ssh overlay mounts into fogwall. Persisted with Gitea, so a re-run
# reuses it rather than minting a duplicate (the token name is single-use).
TOKEN_FILE="/etc/gitea/fogwall-ssh-token"
if [ ! -s "$TOKEN_FILE" ]; then
    if TOKEN=$(gitea admin user generate-access-token --username "$ADMIN_USER" --token-name fogwall-ssh-verify \
        --scopes read:user --raw 2>/dev/null) && [ -n "$TOKEN" ]; then
        printf '%s' "$TOKEN" >"$TOKEN_FILE"
        log "minted SSH verification token"
    else
        log "WARNING: could not mint SSH verification token"
    fi
else
    log "SSH verification token already present"
fi

log "done"
