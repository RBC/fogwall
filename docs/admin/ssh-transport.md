# SSH transport

_Available since v1.3.0._

fogwall can accept pushes over SSH on port 2222 (default). This is an alternative to the HTTP push path — not a
replacement. SSH transport and HTTP transport run side-by-side; a provider can be reached via either or both.

## Exposing SSH on the standard port

The container never binds port 22 directly — that would need root or `CAP_NET_BIND_SERVICE`, the same constraint that
already keeps the HTTP listener on plaintext 8080 behind your load balancer's TLS termination for 443. Apply the same
pattern for SSH: a plain TCP/L4 passthrough rule (external `:22` → the pod's `:2222`) needs no app or container change,
since SSH is a single TCP stream with no Host-header-style routing for an L7 proxy to key off. The Helm chart's
`sshService.*` values do this out of the box.

This matters for clients: Git's SCP-like shorthand (`git@host:owner/repo.git`, what GitHub's own
`git@github.com:owner/repo.git` uses) has no field for a non-default port — only the explicit `ssh://host:port/path`
form does. Without the port-22 passthrough above, your users are stuck with the explicit form (see
[Adding an SSH remote](../user/ssh-remotes.md#setting-up-ssh) in the user guide). With it, the shorthand form works
unchanged, since fogwall's own command parsing doesn't care which URL syntax the client's git produced it from.

## How SSH identity verification works

The SSH push path enforces the **same compliance guarantee** as the HTTP path — every push is tied to a verified SCM
user — but the mechanism is different because there is no token available:

1. **Inbound MINA auth (connection gate):** the client's public key must be registered in the pusher's fogwall profile
   (`ssh-keys`). This is equivalent to HTTP Basic auth — it authenticates the proxy user.
2. **SCM identity verification (compliance gate):** fogwall calls the provider REST API to fetch the SSH public keys
   registered by each SCM identity linked to the proxy user, then checks whether the connecting key's SHA-256
   fingerprint is among them. If it is, the push record's `scmUsername` is set to the matching SCM login. If it is not,
   **the push is blocked** — the same outcome as a failed token verification on the HTTP path.

Both steps are required. Step 1 alone is not sufficient — a key registered only in fogwall (but not on the SCM) will
clear MINA auth but fail step 2.

**Provider support:** fingerprint lookup is implemented for GitHub, GitLab, Forgejo, and Gitea. Providers that do not
implement this lookup (Bitbucket, generic proxy) will block all SSH pushes fail-closed. SSH is intentionally not
supported for those providers until a compliant identity verification path exists.

## Configuring a provider for SSH

_The single-entry model below is available since v1.4.0 (earlier releases required a separate `ssh://` provider entry)._

SSH transport is a property of a provider entry — the **same** entry serves both HTTP and SSH. Turn it on with an `ssh:`
sub-block. For a self-hosted Gitea instance:

```yaml
providers:
  gitea:
    type: gitea
    uri: https://gitea.corp.example.com # HTTP/API endpoint (also used for SSH-key identity lookup)
    api-token: <service-account-PAT> # see below
    ssh:
      enabled: true # also serve SSH; endpoint derived as ssh://git@gitea.corp.example.com
      # uri: ssh://git@gitea.corp.example.com:3022  # set explicitly for a non-standard SSH port or username
```

With `ssh.enabled: true` and no `ssh.uri`, the SSH endpoint is derived as `ssh://git@<host>` from the provider's HTTP
`uri`. Set `ssh.uri` explicitly when the upstream uses a non-`git` SSH username (GitHub Enterprise Cloud with data
residency uses the enterprise slug: `ssh://{slug}@{tenant}.ghe.com`) or a non-standard SSH port. The path clients use is
`ssh://fogwall-host:2222/<provider-host>/<org>/<repo>.git`, keyed on the provider's HTTP host.

Permissions, access rules, and SCM identities are all keyed by the single provider name and apply to both transports:

```yaml
permissions:
  - username: alice
    provider: gitea # one entry covers HTTP and SSH pushes
    match:
      target: SLUG
      value: /myorg/.*
      type: REGEX
    grant: PUSH
```

## SCM identity link for SSH

Because HTTP and SSH share one provider entry, a user needs only **one** `scm-identities` entry — it applies to both
transports. The provider ID is the provider's name in the `providers:` block:

```yaml
users:
  - username: alice
    scm-identities:
      - provider: gitea # applies to HTTP and SSH pushes alike
        username: alice-gitea
```

An identity linked via OAuth (see [SCM OAuth](scm-oauth.md)) likewise applies to both transports — a user who links
their account over HTTP can then push over SSH with no extra configuration.

## Upstream host key verification

When fogwall forwards an SSH push it authenticates to the upstream SCM using the developer's **forwarded SSH agent**.
The upstream host key is what binds that agent to the genuine provider, so fogwall verifies it and **fails closed by
default**: an unknown or changed upstream host key aborts the forward. (Without this, an attacker able to redirect the
upstream connection would receive the developer's forwarded agent — an account-takeover primitive.)

Trust is resolved in this order:

1. **Bundled defaults.** fogwall ships pinned host keys for its built-in hosts — github.com, gitlab.com, codeberg.org,
   bitbucket.org, gitea.com — so they work out of the box. Regenerate with `scripts/pin-ssh-host-keys.sh` when a
   provider rotates its key.
2. **Pinned in config (recommended for custom providers).** Pin a private/internal SCM's host key with a standard
   `known_hosts` line — globally under `server.ssh.extra-known-hosts`, or per-provider under that provider's
   `ssh.known-hosts` (scoped to its upstream, since known_hosts lines are host-keyed):

   ```yaml
   server:
     ssh:
       extra-known-hosts:
         - "git.internal.example.com ssh-ed25519 AAAA..."

   providers:
     gitea:
       uri: https://gitea.corp.example.com
       ssh:
         enabled: true
         known-hosts:
           - "gitea.corp.example.com ssh-ed25519 AAAA..."
         # known-hosts-path: /etc/fogwall/gitea_known_hosts  # or point at a file
   ```

3. **Operator-supplied file.** Point `server.ssh.known-hosts-path` at a `known_hosts` file. The container image bakes
   the bundled keys at `/etc/fogwall/known_hosts`; mount your own file there (or anywhere, and set the path) to add or
   rotate host keys **without upgrading fogwall**.
4. **Trust on first use (opt-in).** `server.ssh.trust-on-first-use: true` pins an otherwise-unknown host's key on the
   first connection — logged loudly with its fingerprint — and rejects a later change. Convenient for internal providers
   on a trusted network whose key can't be pinned ahead of time; it is **not** a substitute for pinning across an
   untrusted network. Default is `false` (unknown key rejected).

Effective trust is the union of the bundled/configured file, the inline `extra-known-hosts`, and any TOFU-pinned keys.

## The `api-token` requirement

The provider REST API is called to fetch SSH public keys for registered SCM identities. GitHub's endpoint
(`GET /users/{login}/keys`) is public — no token is needed. Forgejo and GitLab require authentication when the instance
is configured with `REQUIRE_SIGNIN_VIEW=true` (common in corporate deployments where the git server is not publicly
accessible).

Create a service account on the upstream SCM and generate a PAT with `read:user` scope (Forgejo) or `read_user` scope
(GitLab). This account does not need repository access — it only needs to list user SSH public keys. Set the token in
the provider config:

```yaml
providers:
  gitea:
    type: gitea
    uri: https://gitea.corp.example.com
    api-token: <service-account-PAT>
    ssh:
      enabled: true
```

There is no environment variable override for `api-token` (the env var mechanism does not support hyphenated config
keys). Use a profile config file to supply the token outside of the checked-in base config:

```yaml
# /app/conf/fogwall-local.yml  (mounted into the container, not committed)
providers:
  gitea:
    api-token: gta_xxxxx
```

## `api-uri` — when it is needed

The provider's `uri` is the HTTP/HTTPS endpoint, so the REST API base is derived from it directly and no `api-uri` is
needed in the normal case.

`api-uri` is only required when the HTTP API runs on a non-standard port on the same host — for example a local
development Gitea where HTTP is on 3000 and SSH on 3022:

```yaml
gitea:
  type: gitea
  uri: http://localhost:3000
  api-uri: http://localhost:3000
  ssh:
    enabled: true
    uri: ssh://git@localhost:3022
```

## Requiring agent forwarding

Fogwall uses the client's forwarded SSH agent to authenticate outbound SSH connections to the upstream SCM. The client
**must** connect with `ssh -A` (or `ForwardAgent yes` in `~/.ssh/config`). If agent forwarding is absent, the push is
blocked with a clear error:

```text
error: SSH agent forwarding required — connect with 'ssh -A' or set 'ForwardAgent yes' in ~/.ssh/config
```

There is no configuration to disable this requirement — fogwall never reads local identity files for upstream auth.
