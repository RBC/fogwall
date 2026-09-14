#!/usr/bin/env bash
# Environment defaults and credential resolution for test scripts

export GIT_USERNAME=${GIT_USERNAME:-"me"}
export FOGWALL_API_KEY=${FOGWALL_API_KEY:-"change-me-in-production"}
# Used by identity smoke tests to set git config user.name/email.
# Override to match your own registered proxy user email.
export GIT_AUTHOR_NAME=${GIT_AUTHOR_NAME:-"Thomas Cooper"}
export GIT_EMAIL=${GIT_EMAIL:-"thomas-cooper@example.com"}

# System temp directory (macOS uses $TMPDIR e.g. /var/folders/.../T/, Linux defaults to /tmp)
_SYS_TMPDIR="${TMPDIR:-/tmp}"
_SYS_TMPDIR="${_SYS_TMPDIR%/}"   # strip trailing slash

# safe_rm_rf() — delete a directory only if it is under the system temp dir
safe_rm_rf() {
    local dir="$1"
    if [[ -z "${dir}" || "${dir}" != "${_SYS_TMPDIR}"/* ]]; then
        echo "ERROR: safe_rm_rf: refusing to delete '${dir}' (not under ${_SYS_TMPDIR})" >&2
        return 1
    fi
    rm -rf "${dir}"
}

# resolve_pat() — set GIT_PASSWORD from the environment, or from the file GIT_PASSWORD_FILE names
resolve_pat() {
    GIT_PASSWORD="${GIT_PASSWORD:-}"
    local pat_file="${GIT_PASSWORD_FILE:-}"
    if [ -z "${GIT_PASSWORD}" ] && [ -n "${pat_file}" ] && [ -f "${pat_file}" ]; then
        GIT_PASSWORD="$(cat "${pat_file}")"
    fi
    if [ -z "${GIT_PASSWORD}" ]; then
        echo "ERROR: set GIT_PASSWORD, or GIT_PASSWORD_FILE to a file holding the token" >&2
        exit 1
    fi
    export GIT_PASSWORD
}
