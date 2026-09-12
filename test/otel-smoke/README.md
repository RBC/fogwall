# SCM API OTel smoke scripts

Drive real proposal traffic through the compose stack's SCM API listeners to exercise the OpenTelemetry instrumentation
— per-request `scmapi <provider>` spans, the `fogwall.scmapi.*` metrics, and `trace_id`/ `span_id` log correlation.

One script per CLI, each walking a PR/MR through its dialect's mutations:

| Script       | CLI    | Provider      | Listener         | Token env var  |
| ------------ | ------ | ------------- | ---------------- | -------------- |
| `gh-pr.sh`   | `gh`   | github.com    | `localhost:8481` | `GH_PAT`       |
| `glab-mr.sh` | `glab` | gitlab.com    | `localhost:8482` | `GLAB_PAT`     |
| `fj-pr.sh`   | `fj`   | codeberg.org  | `localhost:8483` | `CODEBERG_PAT` |
| `tea-pr.sh`  | `tea`  | gitea (local) | `localhost:8484` | `GITEA_PAT`    |

## 1. Bring up the stack with the proposals + otel overlays

The listeners serve TLS (the `gh`/`glab` CLIs require HTTPS to a custom host), so generate the cert first:

```
./test/make-certs.sh    # writes docker/tls/ (gitignored)
docker compose -f docker/docker-compose.yml -f docker/docker-compose.otel.yml \
               -f docker/docker-compose.proposals.yml --profile otel up -d --build
```

The driver scripts trust the generated CA via `SSL_CERT_FILE=docker/tls/ca.crt`. A cert swap after startup needs
`docker compose ... restart fogwall`, not just `up -d`.

## 2. Provision yourself — the operator step

The committed config declares **no** proposal identities or grants: you add them to the DB at runtime through the API,
exactly as an operator onboards a user in an org. Using the stack's admin API key (`FOGWALL_API_KEY`, default
`change-me-in-production`), for your SCM login:

```
API=http://localhost:8080 ; KEY=change-me-in-production ; LOGIN=<your-scm-login>
curl -s -X POST "$API/api/users/provision" -H "X-Api-Key: $KEY" \
     -H 'content-type: application/json' -d "{\"username\":\"$LOGIN\"}"
for p in github gitlab codeberg gitea; do
  curl -s -X POST "$API/api/users/$LOGIN/identities" -H "X-Api-Key: $KEY" \
       -H 'content-type: application/json' -d "{\"provider\":\"$p\",\"scmUsername\":\"$LOGIN\"}"
  for g in PROPOSE MERGE; do
    curl -s -X POST "$API/api/users/$LOGIN/permissions" -H "X-Api-Key: $KEY" \
         -H 'content-type: application/json' \
         -d "{\"provider\":\"$p\",\"target\":\"OWNER\",\"value\":\"$LOGIN\",\"matchType\":\"LITERAL\",\"grant\":\"$g\"}"
  done
done
```

For the **local gitea** listener, the target repos/users come from `docker/gitea-setup.sh` (the same accounts and repos
the normal dev stack uses); provision that gitea login the same way.

## 3. Run the drivers

```
GH_PAT=…                              ./test/otel-smoke/gh-pr.sh      # OWNER/REPO default coopernetes/test-repo
REPO=test-repo-gitlab   GLAB_PAT=…    ./test/otel-smoke/glab-mr.sh
REPO=test-repo-codeberg CODEBERG_PAT=… ./test/otel-smoke/fj-pr.sh
GITEA_PAT=…                           ./test/otel-smoke/tea-pr.sh
```

`OWNER`/`REPO` default to `coopernetes`/`test-repo`; override per run since repo names differ across hosts. Each script
pushes two throwaway head branches straight to the SCM (not through fogwall — `require-validated-head` is off), then
opens/edits/closes/merges through the proposals listener.

Watch results: `docker compose logs -f otel-collector`, Jaeger <http://localhost:16686>, Prometheus
<http://localhost:9090> (`fogwall_scmapi_actions`, `fogwall_scmapi_duration`).

## Caveats

- These mutate **real** repos: they push branches, open PRs/MRs, and merge one into `main`. Use a repo you don't mind
  churning.
- The token is embedded in the phase-1 clone URL (throwaway working dir) but redacted from the trace output. Fine
  locally; don't reuse for anything else.
- `fj`/`tea` merge/comment lines are commented out — their subcommand syntax varies by version; verify against your CLI
  before enabling.
- A blocked literal (`internal.corp.example.com`) is included once per script to produce an `outcome=REJECTED` alongside
  the `FORWARDED` ones.
