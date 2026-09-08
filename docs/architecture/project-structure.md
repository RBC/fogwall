# Project structure

The codebase is a multi-module Gradle build. Dependencies flow upward — `core` is depended on by `server`, `server` is
depended on by `dashboard`.

```
fogwall-core
  Shared library. Contains all validation logic (hooks + filters), the push store, provider
  model, identity resolution, approval abstraction, and database migrations (Flyway). Both
  proxy modes are implemented here. No application entry point — this is a library.

fogwall-server
  Standalone Jetty application (FogwallJettyApplication). Registers both proxy modes for
  every configured provider, loads YAML config via Gestalt, and starts a plain Jetty server.
  No Spring, no dashboard, no REST API. This module also owns the shared servlet registrar
  (FogwallServletRegistrar) and configuration builder (JettyConfigurationBuilder) used by
  the dashboard module.

fogwall-dashboard
  Full application (FogwallDashboardApplication). Depends on both core and server.
  Adds Spring MVC (DispatcherServlet at /*), Spring Security, a REST API (/api/*), and a
  React SPA (built with Vite, bundled into the JAR as static resources). Approval workflow
  is always UI-driven in this mode.
```

The server module defines a `FogwallContext` record that bundles all runtime singletons (push store, user store,
approval gateway, identity resolver, repository caches, TLS config). Both application entry points build this context
from config and pass it to `FogwallServletRegistrar`, which registers the same servlets and filters regardless of
whether the dashboard is present.
