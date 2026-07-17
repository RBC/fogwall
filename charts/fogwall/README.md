# fogwall Helm chart

A minimal starting chart for deploying fogwall on Kubernetes: `Deployment`, `Service`, `ConfigMap`, and
liveness/readiness probes. It defaults to the dashboard image (`ghcr.io/rbc/fogwall`) — proxy, REST API, and approval
UI.

## Not yet included

This is intentionally the 80% case, not the full spec from [#309](https://github.com/RBC/fogwall/issues/309). Not
covered yet, tracked as follow-up work:

- `Ingress` / `IngressRoute`
- `PersistentVolumeClaim` for `h2-file`/SQLite (single-replica-only databases)
- `ServiceAccount` + RBAC
- OpenShift `SecurityContextConstraints` / `anyuid` guidance
- A Kustomize base as a GitOps-friendly alternative

## Install

```bash
helm install fogwall charts/fogwall \
  --set image.tag=1.2.1 \
  -f my-values.yaml
```

Where `my-values.yaml` sets at minimum the `config` value — the contents of the operator-supplied
`fogwall-<configProfileName>.yml` profile (providers, rules, permissions; see
[docs/CONFIGURATION.md](../../docs/CONFIGURATION.md)):

```yaml
config: |
  providers:
    github:
      type: github
      enabled: true
  rules:
    allow:
      - match:
          target: OWNER
          value: my-org
          type: LITERAL
```

## Secrets

This chart does not create a `Secret` — bring your own (database password, OIDC client secret, API key) and project it
via `extraEnvFrom`:

```yaml
extraEnvFrom:
  - secretRef:
      name: fogwall-secrets
```

## Multi-replica deployments

The default config (`h2-file`) only works for a single replica — it's a local file database. For `replicaCount > 1`,
override `config` to point at PostgreSQL or MongoDB, and set `server.session-store` to `jdbc`, `mongo`, or `redis` so
sessions are shared across pods. See
[docs/ADMIN_GUIDE.md — Production checklist](../../docs/ADMIN_GUIDE.md#production-checklist).

## Standalone server image

`variant` picks which image (and matching probe type) to deploy:

```yaml
variant: server # proxy + validation pipeline only, no dashboard/REST API
```

This switches the default image to `ghcr.io/rbc/fogwall-server` and the liveness/readiness probes to a plain `tcpSocket`
check — the server image has no `/api/health` endpoint (see
[docs/ADMIN_GUIDE.md — Standalone server image](../../docs/ADMIN_GUIDE.md#standalone-server-image-no-dashboard)). Set
`image.repository`/`image.tag` or `livenessProbe`/`readinessProbe` explicitly to override either independently of
`variant` (e.g. to point at a private mirror of either image).

## Values reference

| Key                  | Default        | Description                                                                       |
| -------------------- | -------------- | --------------------------------------------------------------------------------- |
| `variant`            | `dashboard`    | `dashboard` or `server` — picks the default image and probe type together         |
| `replicaCount`       | `1`            | Pod replica count                                                                 |
| `image.repository`   | _(by variant)_ | Override to deploy a private mirror; blank uses the `variant` default             |
| `image.tag`          | `latest`       | Image tag — pin to a specific release in production                               |
| `image.pullPolicy`   | `IfNotPresent` |                                                                                   |
| `service.type`       | `ClusterIP`    |                                                                                   |
| `service.port`       | `80`           | Service port (routes to `targetPort` on the pod)                                  |
| `service.targetPort` | `8080`         | Container port                                                                    |
| `configProfiles`     | `custom`       | `FOGWALL_CONFIG_PROFILES` value — comma-separated profile names                   |
| `configProfileName`  | `custom`       | Name used for the operator-supplied profile file mounted from the `ConfigMap`     |
| `config`             | _(sample)_     | Contents of `fogwall-<configProfileName>.yml` — see [Install](#install)           |
| `extraEnvFrom`       | `[]`           | `envFrom` entries — use to project an existing `Secret`                           |
| `extraEnv`           | `[]`           | Additional `env` entries                                                          |
| `resources`          | `{}`           | Pod resource requests/limits                                                      |
| `livenessProbe`      | _(by variant)_ | Override to replace the variant default entirely (`httpGet`, `tcpSocket`, `exec`) |
| `readinessProbe`     | _(by variant)_ | Override to replace the variant default entirely                                  |
| `nodeSelector`       | `{}`           |                                                                                   |
| `tolerations`        | `[]`           |                                                                                   |
| `affinity`           | `{}`           |                                                                                   |
