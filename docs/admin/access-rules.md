# Access rules

```yaml
rules:
  allow:
    - enabled: true
      order: 110
      operation: [FETCH, PUSH]
      providers: [github/github.com]
      slugs:
        - /myorg/repo-one
        - /myorg/repo-two

  deny:
    - enabled: true
      order: 100 # deny rules with lower order numbers take precedence
      operation: [PUSH]
      slugs:
        - /myorg/archived-repo
```

Rules are evaluated in `order` number order (lower = earlier). Deny rules override allow rules at the same order number.
The proxy is **default-deny**: if no allow rule matches, the request is rejected.

`operation` scopes a rule to `PUSH`, `FETCH`, or both. A repo can be open for fetch but restricted for push.

## Disabling fetch serving entirely

Access rules gate _which upstreams_ are reachable for `FETCH`. A separate, coarser switch controls whether server mode
serves clone/fetch from its local mirror **at all**:

```yaml
server:
  serve-fetch: false # global default; push-only gateway, no local mirror served

providers:
  github:
    serve-fetch: true # optional per-provider override of the global default
```

Serving fetches is the default and the right one for most deployments — a developer whose remote is the fogwall URL
expects `git pull` to work against it, and taking that away breaks the single-remote workflow. Turn it off when:

- fogwall is a push-validation gateway that is not meant to be a read path for anything;
- the mirror holds repositories you would rather not serve from fogwall's disk at all, regardless of who asks;
- you want the reachable surface as small as the use case requires.

When disabled, the `git-upload-pack` capability is simply not mounted (HTTP) and is refused on the SSH transport; a
fetch is refused with a `403` and a message git prints — `remote: fetches are not served through this gateway` — rather
than a `404` that reads as a missing repository. Push (`receive-pack`) is unaffected, and the switch applies to **both**
server mode transports so neither can serve a fetch the other refuses.

This is deliberately not a per-user read-permission model: for a public upstream there is no credential to authorize,
and for a private one the fetch already carries the caller's own upstream credentials, which answers the question
authoritatively. Use access rules to gate _which_ repos are reachable, and `serve-fetch` to decide whether fogwall
serves fetches at all. Transparent proxy mode forwards to upstream rather than serving a local mirror, so it is
unaffected by this setting.

## Fetch activity

fogwall counts its decisions on clones and fetches rather than recording each one. Every decision increments a counter
in memory, keyed by the hour, repository, transport (HTTP or SSH), proxy mode, result, refusal reason, and the access
rule that matched. Counts are written to the `fetch_activity` table every `server.fetch-activity.flush-interval-seconds`
and kept for `retention-days`; see [Server settings](../configuration/server.md). A clone never waits on the database.

The counts drive the fetch and blocked numbers on the Repos page, where each repository's numbers open its hourly rows.
They are also served at `GET /api/fetches`, filterable by result, transport, provider, owner, repository, and search.

A refusal carries one of these reasons:

| Reason                  | Refused because                                                      |
| ----------------------- | -------------------------------------------------------------------- |
| `NOT_IN_ALLOW_LIST`     | No allow rule matched the repository.                                |
| `DENY_RULE`             | A deny rule matched; the row names the rule.                         |
| `FETCH_DISABLED`        | `serve-fetch` is off for the provider.                               |
| `CREDENTIAL_REFUSED`    | A fogwall credential was invalid, expired, revoked, or not accepted. |
| `LINKED_TOKEN_UNUSABLE` | A fogwall credential was valid, but its linked SCM account was not.  |
| `SSH_AGENT_MISSING`     | An SSH client connected without agent forwarding.                    |

No caller identity is kept: fetch access never depends on one. Up to one flush interval of counts is lost if fogwall
stops abruptly or the database refuses a write. Past `max-keys` distinct combinations between writes, further fetches
are counted in a row per provider and outcome that names no repository, and fogwall logs a warning. An allowed fetch
logs at `DEBUG`; a refused one logs at `INFO`. The `fogwall.fetch.decisions` metric carries the same decisions without
the repository; see [Observability](../configuration/observability.md).

Before 1.5.0, fogwall wrote one `fetch_records` row per request. That table (or MongoDB collection) is no longer written
or read, and is left in place for you to drop.

## Dry-run testing rules and permissions

_Available since v1.3.0._

Before rolling out a new access rule or permission grant, verify the outcome against the live configuration without
waiting for a real push:

```
POST /api/repos/rules/test
{ "provider": "github/github.com", "owner": "myorg", "name": "myrepo", "operation": "PUSH" }
→ { "decision": "ALLOW", "matchedRuleId": 110, "steps": [...] }

POST /api/users/{username}/permissions/test
{ "provider": "github/github.com", "path": "/myorg/myrepo", "grant": "PUSH" }
→ { "allowed": true, "source": "GROUP", "groupName": "platform-team" }
```

Both endpoints are read-only evaluations against whatever rules, permissions, and groups are currently loaded — no push
is created. `source` on the permission check distinguishes a direct per-user grant (`DIRECT`) from one inherited via a
[permission group](repo-permissions.md#permission-groups) (`GROUP`). These endpoints are dashboard-only
(`fogwall-dashboard`); the standalone server has no REST API.
