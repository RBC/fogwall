# Shared helpers for the SCM API OTel smoke scripts. Sourced by the per-CLI drivers, not run directly.
#
# The drivers push real proposal traffic against real repos (github.com / gitlab.com / codeberg.org /
# gitea.com) through the compose stack's proposals listeners, to exercise the OpenTelemetry instrumentation.
# Prereqs: the stack is up with the proposals + otel overlays and TLS, the certs exist (test/make-certs.sh),
# and the per-CLI PAT env var is set to a token whose login is provisioned in the DB (see README).

OWNER="${OWNER:-coopernetes}"
REPO="${REPO:-test-repo}"
SLUG="$OWNER/$REPO"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CERT="$ROOT/docker/tls/ca.crt" # the CA the CLIs trust; fogwall serves the leaf it signed
STAMP="$(date +%s)"

require_cert() {
  [ -f "$CERT" ] || {
    echo "missing $CERT — run test/make-certs.sh and enable TLS in docker/docker-compose.proposals.yml" >&2
    exit 1
  }
}

# fj prints its numbers wrapped in Unicode bidi-isolate chars (U+2068/U+2069); strip them.
strip_bidi() { sed $'s/⁨//g; s/⁩//g'; }

# Pull the PR/MR/issue number out of a CLI's stdout (gh/glab/tea print a URL; fj prints #N).
num() { strip_bidi | grep -oE '(pull|pulls|merge_requests|issues|work_items)/[0-9]+|#[0-9]+' | grep -oE '[0-9]+' | tail -1; }

# Abort clearly when a create step captured no id, instead of running the follow-up edit/close with an empty
# one (the create's own error is printed just above).
require_id() { [ -n "${1:-}" ] || { echo "  create failed — aborting" >&2; exit 1; } ; }

# Clone the real repo and push two throwaway head branches straight to the SCM (not through fogwall). Sets
# WORK, BR_LC (lifecycle) and BR_MG (merge). $1 is the authenticated https clone URL; $2 a short tag. The
# clone is run without echoing it, so the token in the URL never reaches the terminal.
make_head_branches() {
  local url="$1" tag="$2" br
  WORK="$(mktemp -d)"
  echo "Cloning $SLUG and pushing two head branches..."
  git clone -q "$url" "$WORK/repo"
  cd "$WORK/repo" || exit 1
  BR_LC="otel-${tag}-lifecycle-$STAMP"
  BR_MG="otel-${tag}-merge-$STAMP"
  for br in "$BR_LC" "$BR_MG"; do
    git switch -c "$br" origin/main -q
    printf 'otel smoke %s\n' "$br" > "otel-$br.md"
    git add "otel-$br.md"
    git commit -qm "otel smoke: $br"
    git push -q -u origin "$br"
    git switch -q -
  done
}
