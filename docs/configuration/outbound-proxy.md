# Outbound proxy

_Available since v1.3.0._

For environments without direct internet access, fogwall can route its own outbound connections through a corporate HTTP
proxy. This covers all three places fogwall makes outbound connections: server mode upstream pushes (JGit Transport),
transparent-proxy forwarding (Jetty `HttpClient`), and provider REST API calls (identity resolution, SSH key listing).

```yaml
server:
  outbound-proxy:
    https-proxy: http://proxy.example.com:8080
    no-proxy: localhost,*.internal.example.com
    auth:
      type: none # none (default) | basic | kerberos
```

`http-proxy`, `https-proxy`, and `no-proxy` fall back to the `HTTP_PROXY`/`HTTPS_PROXY`/`NO_PROXY` environment variables
when left unset in YAML — an explicit YAML value always takes precedence. Leave `auth.type` at `none` (the default) when
the configured proxy doesn't require fogwall to authenticate itself.

## Proxy authentication

When the configured proxy requires authentication, two schemes are supported:

```yaml
server:
  outbound-proxy:
    https-proxy: http://proxy.example.com:8080
    auth:
      type: basic
      username: ${PROXY_USER}
      password: ${PROXY_PASS}
```

```yaml
server:
  outbound-proxy:
    https-proxy: http://proxy.example.com:8080
    auth:
      type: kerberos
      # keytab-path and principal are both optional. Omitted (the default): fogwall authenticates using
      # whatever Kerberos ticket is already in the OS's ticket cache — the common case on a machine that
      # already has a live ticket from domain/SSO login. Set both only when fogwall should authenticate
      # as its own service identity instead (e.g. a long-running headless deployment with no live session).
      # keytab-path: /etc/fogwall/proxy.keytab
      # principal: fogwall/host@CORP.EXAMPLE.COM
```

NTLM is intentionally not supported as a scheme fogwall speaks directly — it's a deprecated protocol, and Jetty's HTTP
client (the transparent-proxy path) has no NTLM support at all and can't cleanly add it, since the reference NTLM
implementation (jcifs) is LGPL-licensed and Jetty won't bundle it. Kerberos/Negotiate is the modern successor in Active
Directory environments and is natively supported across all three outbound paths.
