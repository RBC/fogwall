# SSH transport

_Available since v1.3.0._

An alternative to the HTTP push path — see [SSH transport](../admin/ssh-transport.md) in the administrator guide for how
identity verification and agent forwarding work. This section covers the listener config itself.

```yaml
server:
  ssh:
    enabled: false # off by default
    port: 2222 # non-standard on purpose - see note below
    host-key-path: .ssh/fogwall_host_key # generated on first start if absent
```

| Property        | Type    | Default                 | Description                                                                   |
| --------------- | ------- | ----------------------- | ----------------------------------------------------------------------------- |
| `enabled`       | boolean | `false`                 | Whether the SSH listener starts                                               |
| `port`          | int     | `2222`                  | TCP port the SSH server binds                                                 |
| `host-key-path` | string  | `.ssh/fogwall_host_key` | Path to the SSH host key file (absolute or relative to the working directory) |

<!-- prettier-ignore-start -->
> [!NOTE]
> **Why port 2222, and why `git@host:path` shorthand doesn't work out of the box:** the default avoids clashing with a
> real `sshd` that may already be running on the same host, and avoids the container needing root or
> `CAP_NET_BIND_SERVICE` just to bind a port below 1024. The trade-off is that Git's SCP-like shorthand
> (`git@host:owner/repo.git`, the syntax GitHub's `git@github.com:...` uses) has no field for a non-default port — only
> the explicit `ssh://host:port/path` form does. If you want the shorthand to work, put fogwall's SSH port behind a
> plain TCP/L4 passthrough on your load balancer or `Service` (external `:22` → the pod's `:2222`) rather than changing
> what the container itself binds — the same pattern most deployments already use for terminating TLS on 443 in front
> of the container's plaintext 8080. The Helm chart's `sshService.*` values do exactly this.
<!-- prettier-ignore-end -->

## Serving a provider over SSH

_The single-entry model below is available since v1.4.0._

The `server.ssh` block above starts the SSH listener; SSH transport is then turned on **per provider** via an `ssh:`
sub-block on that provider's entry. A single provider entry serves both HTTP and SSH to the same upstream — there is no
need for a separate `github-ssh` entry, and an OAuth-linked identity applies to both transports automatically.

```yaml
providers:
  github:
    enabled: true # github.com over HTTPS
    ssh:
      enabled: true # also serve SSH — the endpoint is derived as ssh://git@github.com

  gitea-internal:
    enabled: true
    type: gitea
    uri: https://gitea.corp.example.com # HTTP/API endpoint
    ssh:
      enabled: true
      uri: ssh://git@gitea.corp.example.com:3022 # explicit endpoint for a non-standard SSH port
      known-hosts:
        - "[gitea.corp.example.com]:3022 ssh-ed25519 AAAA..." # pin this upstream's host key

  # GitHub Enterprise Cloud with data residency uses the enterprise slug as the SSH username, not "git":
  acme-ghe:
    enabled: true
    type: github
    uri: https://acme.ghe.com
    ssh:
      enabled: true
      uri: ssh://acme@acme.ghe.com
```

| Property (under `ssh:`) | Type    | Default     | Description                                                                                                                                                                                          |
| ----------------------- | ------- | ----------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `enabled`               | boolean | `false`     | Serve this provider over SSH. With no `ssh.uri`, the endpoint is derived as `ssh://git@<host>` from the provider's HTTP `uri` (port 22).                                                             |
| `uri`                   | string  | _(derived)_ | Explicit SSH endpoint. Required when the upstream uses a non-`git` SSH username (e.g. GHEC data residency) or a non-standard port. Must use the `ssh://` scheme.                                     |
| `known-hosts`           | list    | _(none)_    | Inline `known_hosts` lines pinning this upstream's SSH host key(s). Merged with the global `server.ssh.known-hosts-path` / bundled defaults. Entries are host-keyed, so they scope to this upstream. |
| `known-hosts-path`      | string  | _(none)_    | Path to a `known_hosts` file whose lines pin this upstream's host key(s). Read once at startup and merged like `known-hosts`.                                                                        |

<!-- prettier-ignore-start -->
> [!NOTE]
> The `uri` on a provider entry is always the **HTTP/HTTPS** endpoint (it is also the API base used for SSH-key
> identity resolution). A top-level `uri` with an `ssh://` scheme no longer enables SSH — configure SSH through the
> `ssh:` sub-block instead.
<!-- prettier-ignore-end -->
