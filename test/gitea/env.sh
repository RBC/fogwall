#!/usr/bin/env bash
# Gitea-local overrides for smoke test scripts.
# Source before running any test script to target the local Compose stack
# instead of the default GitHub remote:
#
#   source test/gitea/env.sh
#   bash test/push-fail-secrets.sh
#   bash test/proxy-fail-secrets.sh
#
# Uses test-user's Gitea token as the git HTTP password so that the proxy's
# identity resolution can call the Gitea API with the same credentials.
# Tokens are written by docker/gitea-setup.sh to test/gitea/tokens.env.

source "$(dirname "${BASH_SOURCE[0]}")/tokens.env"

export GIT_USERNAME="me"
export GIT_PASSWORD="${GITEA_TESTUSER_TOKEN}"
export GIT_REPO="${GITEA_HOST:-localhost:3000}/test-owner/test-repo.git"
export GIT_AUTHOR_NAME="test-user"
export GIT_EMAIL="testuser@example.com"
