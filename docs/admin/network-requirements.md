# Network requirements

fogwall opens outbound connections to upstream SCM providers (GitHub, GitLab, Bitbucket, Gitea) from the **server**, not
from the developer's workstation. Your network team needs to allow egress from the proxy host, not from individual
developer machines.

## Outbound connections the proxy makes

| Path                                       | Library                  | Destination               |
| ------------------------------------------ | ------------------------ | ------------------------- |
| Server mode upstream push (HTTPS)          | JGit Transport (HTTPS)   | SCM provider git endpoint |
| Server mode upstream push (SSH)            | JGit Transport (SSH)     | SCM provider SSH endpoint |
| Transparent proxy forwarding               | Jetty HttpClient (HTTPS) | SCM provider git endpoint |
| SCM identity resolution (PAT verification) | Apache HttpClient 5      | SCM provider REST API     |
| SSH fingerprint lookup                     | Apache HttpClient 5      | SCM provider REST API     |

All paths must be able to reach the upstream SCM provider. A common operational mistake is opening the firewall for one
path but not the others — pushes appear to succeed locally but fail when the proxy tries to verify the committer's
identity via the API.

## Corporate HTTP proxy

If outbound internet access requires routing through a corporate HTTP proxy, set the standard environment variables
before starting fogwall:

```bash
export HTTPS_PROXY=http://proxy.corp.example.com:8080
export HTTP_PROXY=http://proxy.corp.example.com:8080
export NO_PROXY=localhost,127.0.0.1,*.internal.example.com
```

fogwall reads these at startup and configures all three outbound paths accordingly. No YAML config is needed.

When the configured proxy requires authentication, set `server.outbound-proxy.auth` in YAML — see
[Outbound proxy](../configuration/outbound-proxy.md) in the configuration reference for Basic and Kerberos options. NTLM
is not supported as a scheme fogwall speaks directly: it's a deprecated protocol, and Jetty's HTTP client (used for
transparent-proxy forwarding) has no NTLM support at all. Kerberos/Negotiate is the modern successor in Active-Directory
environments and is supported natively across all three outbound paths.

## Connectivity diagnostics (dashboard)

The dashboard admin panel includes a **Provider Connectivity** section (`Admin → Provider Connectivity`) that runs
layered outbound checks against each configured provider. Use this to generate a sharable diagnostic report for your
network team without requiring them to access server logs.

**Baseline check** (all providers): for each provider runs in sequence and stops at the first failure:

1. **TCP** — opens a socket to `host:port` (5 s timeout). Classifies the outcome as REFUSED, TIMEOUT, or RESET so a
   firewall DROP vs REJECT is immediately distinguishable.
2. **TLS** — completes the TLS handshake and reports the negotiated protocol, cipher suite, and peer certificate CN.
   Detects MITM/SSL-inspection appliances that swap the upstream certificate.
3. **HTTP** — sends `GET /` and records the HTTP status code and response time.

**Targeted check** (single provider + optional repo path): runs the same three steps, then adds:

4. **Git probe** — sends `GET /info/refs?service=git-upload-pack` and `GET /info/refs?service=git-receive-pack` with a
   `User-Agent: git/2.x.x` header. Any HTTP response (200, 401, 403 …) means the request reached the upstream — git URL
   patterns and the git user-agent are not being filtered. A TIMEOUT or RESET after TCP/TLS passed indicates a DLP
   appliance blocking git-specific traffic specifically.

The targeted check returns a structured `steps` log in the API response (`GET /api/admin/connectivity?provider=<name>`)
that can be copied directly into a ticket for the network team.

## DLP appliances and non-GET blocking

Some enterprises deploy DLP (Data Loss Prevention) appliances that inspect or selectively block outbound HTTPS traffic.
A common policy blocks anything other than GET requests to `github.com` or similar SCM hosts — this will prevent fogwall
from forwarding pushes upstream even if the proxy can reach the host.

Symptoms: clones through the proxy succeed, but pushes fail at the upstream forwarding step with a 403 or a TCP reset.
The git probe in the targeted connectivity check will show this as a TIMEOUT or RESET on the `git-receive-pack` step
after TCP and TLS both pass.

**Resolution:** work with your network team to allowlist the proxy server's egress IP for POST/PUT traffic to the SCM
provider's git endpoint. A transparent HTTPS inspection proxy (MITM) will also break JGit's certificate pinning — the
proxy host's egress IP should bypass SSL inspection, not just be allowlisted at the IP layer.

## TLS termination and forwarded headers

The git and SCM API listeners need nothing special behind a TLS-terminating proxy: the git protocol does not consult
forwarded headers, and fogwall reads none and emits none on those paths. The **dashboard** is the exception — it
resolves the external scheme, host and port so that OIDC login redirects, other absolute URLs, and the session cookie's
`Secure` flag reflect the address the browser used. `server.trust-forwarded-headers` (default `true`) controls where it
reads that address from. There are two supported shapes.

**TLS terminated at fogwall.** The browser reaches fogwall's own HTTPS connector directly on the external hostname (see
[TLS configuration](../configuration/server.md)). fogwall's own request is already `https` on the right host, so it can
resolve the external address from the connection itself. Set `server.trust-forwarded-headers: false` — there is no proxy
in front setting the headers, and leaving them trusted would let any client that reaches the port spoof them.

**TLS terminated at an ingress, plaintext inside the cluster.** The browser reaches an ingress or load balancer over
HTTPS, which forwards plaintext HTTP to fogwall. fogwall's own request is `http` on an internal host, so it cannot
derive the external address from the connection — it must read the `Forwarded` / `X-Forwarded-*` headers the ingress
sets. Keep `server.trust-forwarded-headers: true` (the default), and also **set `server.service-url`** to the external
base URL: it is separate from forwarded-header handling and needed by the paths that run without a browser request in
scope — SCM OAuth account-linking (which refuses to build a `redirect_uri` without it) and the links embedded in
sideband messages to git clients (which are omitted without it).

The precondition for `trust-forwarded-headers: true` is that the dashboard listener is reachable **only** through the
ingress that sets the headers; a client able to reach the dashboard port directly could otherwise spoof scheme and host.
A startup log line names the active setting and this precondition. Turning the setting off on a deployment behind a TLS
ingress breaks login redirects and drops the session cookie's `Secure` flag, which is why the default is `true`.

## Large pushes failing behind a reverse proxy (chunked transfer-encoding)

When fogwall is deployed behind a reverse proxy (HAProxy, nginx, a cloud load balancer), pushes with large packs (> 1
MiB) can fail with:

```
send-pack: unexpected disconnect while reading sideband packet
fatal: the remote end hung up unexpectedly
```

Server-side logs show `ParseGitRequestFilter` errors such as `EOFException: Short read of block` or
`Invalid packet line header`.

**Root cause:** git uses `Transfer-Encoding: chunked` for pushes exceeding `http.postBuffer` (default 1 MiB). Many
reverse proxies don't fully support chunked request forwarding — they may terminate the chunked stream early, dechunk
and rebuffer it, or split the body across multiple backend requests, so fogwall receives a truncated or malformed
request. Small pushes (< 1 MiB) use `Content-Length` instead and are unaffected, which is why this often shows up only
once a repo or commit grows past that size.

**Client-side workaround** — force git to send the pack as a single `Content-Length` request instead of chunked:

```bash
git config --global http.postBuffer 524288000
```

**Server-side workaround (nginx)** — ensure the proxy buffers the full request body before forwarding and allows a large
enough body size:

```
proxy_request_buffering on;
client_max_body_size 500m;
```

## Sizing memory for pushes

fogwall buffers each request body in memory for the life of the request, in both proxy modes — validation needs the
whole pack before it can decide anything. Two settings bound that, and they multiply:

| Setting                          | Default | Bounds                        |
| -------------------------------- | ------- | ----------------------------- |
| `server.max-push-bytes`          | 64 MiB  | how large one push may be     |
| `server.max-concurrent-requests` | 512     | how many run at the same time |

The worst case is `max-push-bytes × concurrent large pushes`, so **raising `max-push-bytes` means raising the
container's memory limit to match.** Do not set JVM heap flags to compensate: fogwall's image deliberately ships without
`-Xmx` so the JVM sizes its heap from the container's cgroup limit (about 25% of it by default). Setting `-Xmx` yourself
overrides that and pins the heap regardless of how the container is sized. Give the container more memory instead.

A push over the limit is rejected before the body is read, so it costs no memory and the developer gets a clear message
naming the limit rather than a timeout or a connection reset.

**Interaction with `http.postBuffer`.** The client workaround above raises the threshold at which git switches to
chunked encoding; it does not change how large a push may be. A push under `http.postBuffer` declares a
`Content-Length`, which lets fogwall reject an over-size push without reading anything. Above it, the push is chunked
and fogwall counts bytes as they arrive instead. Both paths enforce the same limit.

**If 64 MiB is too small for your estate**, prefer these over raising the limit:

- Seed one-off imports and repository migrations directly upstream, then let the proxy handle incremental pushes. A
  migration is a coordinated, one-time event and does not need to be self-service.
- Push large histories in stages — older commits first, then newer.
- Keep large binaries out of git history in the first place. Note that **Git LFS is not currently supported through
  fogwall** (see the User Guide); LFS uploads are refused because fogwall cannot inspect content that travels outside
  the git protocol.

## Sizing disk for pushes

Received pack data is inflated into a per-push quarantine directory on disk before validation runs, and `max-push-bytes`
caps only the _compressed_ wire size. `server.max-object-size-bytes` (default 128 MiB) caps what any single object may
inflate to, which stops the cheap decompression-bomb case, but there is no total-decompressed limit: a pack split across
many highly-compressible objects can still inflate to roughly `max-push-bytes × 1000` on disk in the worst case before
it is rejected. Quarantine directories are deleted when the request ends, so this is transient pressure, not growth —
but the volume holding the quarantine (the working directory by default) should be sized, or quota'd, with that worst
case and `max-concurrent-requests` in mind rather than assuming pushes stay near their wire size.

## Local mirror clone depth

fogwall keeps a local bare mirror of each upstream repo to inspect push content. Its clone depth is configurable per
proxy mode under `cache:` (see [Configuration Reference](../configuration/mirror-cache.md)). Server mode defaults to
full history; the transparent proxy defaults to a shallow clone, because a first full clone of a very large repository
through the proxy can exceed HTTP connection timeouts. If proxy-mode first-clones are timing out for a large repo, keep
it shallow (the default) or tune `cache.proxy.shallow-since`; if you want the proxy to mirror full history and can
absorb the first-clone cost, set `cache.proxy.clone-depth: 0`. A shallow default is safe: reachability and hidden-commit
checks deepen the mirror to full history on demand before deciding.

## Inspecting and invalidating the local mirror cache

The **Admin → Local mirror cache** page (requires `ROLE_ADMIN`) shows the mirrors each mode currently holds — server
mode and transparent proxy are listed separately — with each mirror's upstream URL, on-disk size, ref count (expandable
to the branches and tags present), when it was first cloned, and when it last fetched upstream. Two actions are
available: **Invalidate** removes one mirror, and **Invalidate all** clears a mode's cache. Either way the local clone
is deleted and re-created from upstream on the repo's next push/fetch, so this is the fix for a mirror that has gone
stale or been poisoned (e.g. a failed upstream forward left objects upstream never received) — recovery that previously
required a pod restart. Invalidation is safe on a running server: it deletes the per-repo clone but keeps the cache
directory, and every invalidation is logged with the acting admin's login.

This state is **per-pod** — each pod serves its own in-memory cache, so the page reflects the cache of whichever pod
handled the request. To inspect or invalidate a specific pod's cache in a multi-pod deployment, reach that pod directly
(e.g. via `kubectl port-forward` to the pod). The same operations are exposed over REST under `/api/admin/cache` for
scripting.
