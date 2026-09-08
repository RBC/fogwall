# TLS

## Server HTTPS listener

By default fogwall listens on plain HTTP. To enable HTTPS, add a `server.tls` block.

**PEM-based (preferred — no keytool required):**

```yaml
server:
  tls:
    port: 8443
    certificate: /etc/fogwall/tls/server.pem # X.509 certificate or chain, PEM
    key: /etc/fogwall/tls/server-key.pem # PKCS8 private key, unencrypted PEM
```

The private key must be in PKCS8 format. Convert a PKCS1 key with:

```bash
openssl pkcs8 -topk8 -nocrypt -in server.pem -out server-key.pem
```

**Keystore-based (for shops with existing managed keystores):**

```yaml
server:
  tls:
    port: 8443
    keystore:
      path: /etc/fogwall/tls/keystore.p12
      password: changeit
      type: PKCS12 # or JKS
```

Plain HTTP on `server.port` remains active when HTTPS is configured — both listeners run concurrently.

## Custom upstream CA trust

Enterprise PKIs typically issue certificates that Java's built-in truststore doesn't include, causing
`SSLHandshakeException` on upstream connections to internal GitLab/Bitbucket/Forgejo instances. fogwall supports
trusting a custom CA bundle without touching the JVM truststore or running `keytool`.

```yaml
server:
  tls:
    trust-ca-bundle: /etc/fogwall/tls/internal-ca.pem
```

The PEM file may contain one or more `-----BEGIN CERTIFICATE-----` blocks (a full CA chain is fine). Custom CAs are
merged with the JVM's built-in trust anchors — public hosts (GitHub, GitLab SaaS, Bitbucket Cloud) continue to work
without any changes.

This applies to both proxy modes:

- **Transparent proxy** — Jetty's `HttpClient` used for upstream forwarding
- **Server mode** — JGit's HTTP transport used for forwarding after local receipt
