#!/usr/bin/env bash
# Gitea-local credentials for the dev stack — source this to export everything needed to talk to the container
# fogwall knows as `gitea-local`:
#
#   source test/gitea/env.sh
#
# The `gitea` provider is gitea.com, where the tea and fj dialects are verified — configure that one with
# GITEA_TOKEN/GITEA_OWNER/GITEA_REPO of your own.
#
# The accounts, orgs and repos come from docker/gitea-setup.sh, which also writes the tokens this sources. The
# other providers are your own accounts and are configured by hand — see test/README.md.
#
# test-user's Gitea token doubles as the git HTTP password, so the proxy's identity resolution can call the Gitea
# API with the same credentials the push arrived with.

source "$(dirname "${BASH_SOURCE[0]}")/tokens.env"

export GITEA_LOCAL_TOKEN="${GITEA_TESTUSER_TOKEN}"
export GITEA_LOCAL_OWNER="test-owner"
export GITEA_LOCAL_REPO="test-repo"
# The container as fogwall addresses it. Inside compose that is gitea:3000; from the host, localhost:3000.
export GITEA_LOCAL_HOST="${GITEA_LOCAL_HOST:-localhost:3000}"

# test-user's registered identity, so pushes resolve to a mapped proxy user rather than an unknown one.
export GIT_AUTHOR_NAME="test-user"
export GIT_AUTHOR_EMAIL="testuser@example.com"
