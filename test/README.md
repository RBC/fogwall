# test/

Helpers for running and demonstrating fogwall by hand. The automated tests live in the Gradle suites, not here:

- `./gradlew test` — unit tests
- `./gradlew e2eTest` — assembled proxy against a Testcontainers Gitea
- `./gradlew :fogwall-dashboard:composeTest` — the packaged image, brought up with `compose.sh`, across the database,
  auth, packaging, proxy-mode and telemetry axes

See [CONTRIBUTING.md](../CONTRIBUTING.md) for what each layer covers.

## What is here

| File                 | What it does                                                                                    |
| -------------------- | ----------------------------------------------------------------------------------------------- |
| `make-certs.sh`      | Generates the local CA and `localhost` certificate the SCM API listeners serve over TLS         |
| `capture/capture.py` | One-shot producer of the Playwright fixture database (see `capture/README.md`)                  |
| `gitea/env.sh`       | Exports credentials for the dev-stack Gitea container, from what `docker/gitea-setup.sh` seeded |
| `generate-demos.sh`  | Rebuilds the demo GIFs in `demos/`                                                              |
