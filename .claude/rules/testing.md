---
paths:
  - "**/src/test/**"
  - "test/**"
---

# Testing conventions

- Always use JUnit assertions (`org.junit.jupiter.api.Assertions.*`) — not manual `if`/`throw` checks.
- E2e tests use Testcontainers (Gitea) + `JettyProxyFixture`. Credentials in the clone URL are forwarded to upstream
  Gitea, so they must be valid Gitea credentials. Use `GiteaContainer.ADMIN_USER`/`ADMIN_PASSWORD` or create test users
  via `createTestUser()` / `addTestUserAsCollaborator()` — never invent fake usernames that won't authenticate upstream.
- Gradle caches test results — pass `--rerun` when adding or changing tests, e.g.
  `./gradlew :fogwall-core:test --rerun`.
- The HTTP Basic username is meaningless for identity — the token drives resolution (only Bitbucket differs). The
  scripts under `test/` use `me`; never switch it to a real handle or document it as an identity input.

## What each layer is for

Four layers, and a case belongs to exactly one of them. Put it in the cheapest layer that can actually prove it.

| Layer   | Task          | System under test                         | Proves                                                     |
| ------- | ------------- | ----------------------------------------- | ---------------------------------------------------------- |
| unit    | `test`        | a class, collaborators mocked             | the logic, both transports (hook and filter)               |
| e2e     | `e2eTest`     | fogwall assembled in-JVM, Testcontainers  | the wiring, including config loading                       |
| compose | `composeTest` | the packaged image, shipped config        | is it up, does it function, on this database and this auth |
| local   | a script      | the developer's own instance and accounts | a feature works against a real provider, and can be shown  |

**Anything reasonably mockable belongs in unit or integration.** e2e exists for what cannot be mocked — a real git
client, a real SCM API, an assembled filter chain.

**Compose is a smoke test: is it up, does it function, pass and fail.** It covers the axes that only exist once the
thing is assembled — database driver, auth provider, packaging, both proxy modes — with one representative pass and one
representative fail each. It is not a feature matrix. Its job is catching a missed SQL dialect or a directory bind that
stopped working, not re-testing a scanner that unit tests already cover on both transports.

**The local script is for demonstrating features**, against the developer's own repositories and credentials, selected
by feature. Never in CI.

## Assert on the reason, not the exit code

For any layer that drives a real git client — e2e, compose, local. Under `server.approval-mode: ui` a clean push is
refused too: the approval gateway holds it pending a decision, and the transparent proxy sends one buffered refusal
carrying a link to the record. A non-zero `git push` says nothing about which check ran, so a scenario whose pattern
matches nothing still reports as a passing detection case.

Assert on the text fogwall returned, or on the decision recorded against the push.
