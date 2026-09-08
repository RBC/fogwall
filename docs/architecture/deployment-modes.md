# Deployment modes

## Proxy only (`fogwall-server`)

`FogwallJettyApplication` boots a plain Jetty server. It loads YAML config (base `fogwall.yml` + profile overlays +
environment variable overrides), builds the `FogwallContext`, and registers both proxy modes for every provider. There
is no Spring context, no dashboard, and no REST API — just the git servlets on `/server/*` and `/proxy/*`.

The approval gateway defaults to `AutoApprovalGateway` — clean pushes go straight through with no human review. A
`LiveConfigLoader` watches the config file and hot-reloads commit validation rules (email patterns, message patterns,
diff scan rules) without restarting the server.

Everything is configured upfront in YAML: users, permissions, URL allow/deny rules, and validation settings. The
standalone server has no REST API, so there is no way to create or modify users, permissions, or rules at runtime. This
makes it well-suited for enforcement-only deployments where configuration is managed as code — CI pipelines, automated
environments, or setups where an external system like ServiceNow handles approval.

```
./gradlew :fogwall-server:run     # start (FOGWALL_CONFIG_PROFILES=local by default)
./gradlew :fogwall-server:stop    # stop via PID file
```

## Proxy + dashboard (`fogwall-dashboard`)

`FogwallDashboardApplication` builds the same `FogwallContext` and calls the same `FogwallServletRegistrar`, then layers
on a Spring MVC `DispatcherServlet` at `/*`. Jetty's servlet path-matching rules give the more-specific git paths
(`/server/*`, `/proxy/*`) precedence, so the Spring servlet only handles `/api/*`, `/dashboard/*`, `/login`, and static
assets.

This is Spring MVC and Spring Security directly on a Jetty `Server` we construct and configure ourselves — not Spring
Boot. Boot's auto-configuration assumes it owns the embedded servlet container: it wants to build the `Server`, wire the
connectors, and register its own default servlet mappings. fogwall needs the opposite — the JGit `ReceivePack` servlets
and the git-protocol filter chain must be registered on that same Jetty instance with precise path and order control
(see [Two proxy modes](proxy-modes.md) above), and `fogwall-server` needs to run the identical servlet setup with zero
Spring on the classpath at all. Wiring Spring MVC onto a Jetty server we already built is straightforward; carving a
Boot application apart to let something else own the container is fighting the framework. So the dashboard module adds
Spring as a set of servlets/filters registered onto fogwall's Jetty server, not the other way around.

Spring Security is registered as a filter chain on a narrow set of paths (`/api/**`, `/login`, `/logout`, `/`,
`/oauth2/**`) — deliberately not on git paths, to avoid interfering with async streaming. Four auth providers are
supported: local (BCrypt from YAML), LDAP, Active Directory, and OIDC (authorization code flow). When using an IdP
(LDAP/AD/OIDC), users are automatically provisioned in the database on first login.

The approval gateway is always `UiApprovalGateway` in this mode, regardless of config. Pushes that pass validation land
in `PENDING` status; a reviewer approves or rejects via the dashboard UI, and the proxy polls the push store for the
decision.

The dashboard adds runtime management that the standalone server does not have: user and permission CRUD, URL rule
management, push history queries, and the approval workflow UI. This is the recommended mode for operational deployments
where administrators need to manage users, review pushes, and adjust policies without redeploying.

The React frontend is built by Vite at Gradle build time and copied into the JAR as static resources. For local
development, Vite's dev server can run separately and proxy `/api` calls to the backend.

```
./gradlew :fogwall-dashboard:run  # start (dashboard at http://localhost:8080/)
./gradlew :fogwall-dashboard:stop # stop via PID file
```

## Docker

The primary production distribution is a Docker image. The Dockerfile builds the dashboard module's distribution
(including the frontend), producing a self-contained image with a Temurin JRE. Config overrides are mounted at
`/app/conf/fogwall-local.yml`.
