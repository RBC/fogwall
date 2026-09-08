# Advanced use cases

## Private-to-private proxying

The provider `uri` does not have to be a public SaaS host. Any Git HTTP server works:

```yaml
providers:
  internal-github:
    type: github
    uri: https://github.mycompany.com
  acquired-gitlab:
    type: gitlab
    uri: https://git.acquiredco.internal
```

Pushes to `/server/internal-github/...` and `/server/acquired-gitlab/...` go through the same validation pipeline. The
proxy validates identity, author email, commit messages, and diff content before forwarding to the appropriate internal
host. This is useful for enforcing consistent push policy across multiple internally-hosted Git services.

## Credential rewriting (planned)

A planned extension is proxy-level credential substitution: the developer authenticates to the proxy with their own
identity, but the forwarded push uses a proxy-managed service account credential for the upstream.

Motivating scenario: an acquired company (Org A) has developers with credentials for Org A's Git host, but they need to
push to shared repositories on the acquiring company's Git host (Org B). Org A developers don't have Org B credentials.
The proxy can:

1. Accept the Org A developer's push (authenticated against their proxy user record).
2. Validate author attribution, commit messages, and diff content normally — the developer's identity is still enforced.
3. Forward the push to Org B's Git host using a proxy-managed service account that has write access there.

This separates authentication (who you are, proven by your token against Org A's API) from forwarding credentials (what
gets sent upstream). All existing validation steps remain active — the credential rewrite only changes what appears in
the `Authorization` header on the forwarded request.
