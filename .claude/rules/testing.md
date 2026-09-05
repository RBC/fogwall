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
