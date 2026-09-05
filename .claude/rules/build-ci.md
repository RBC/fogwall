---
paths:
  - "build.gradle"
  - "**/build.gradle"
  - "settings.gradle"
  - "gradle.properties"
  - "gradle/**"
  - ".github/**"
  - "Dockerfile"
  - "compose.sh"
  - "docker/**"
  - "perf/**"
  - "charts/**"
---

# Build, CI and distribution conventions

## Dependencies

- Transitive CVE pins go in the root `build.gradle` subprojects `eachDependency` table with `because 'CVE fix: GHSA-…'`.
  Never declare the vulnerable artifact as a dependency, not even as a platform/BOM. The `buildscript` force block
  covers only the plugin classpath: it can't fix an image-scan finding, and it is the only thing that clears
  plugin-classpath Dependabot alerts. The `pin-transitive-cve` skill walks through it.
- Vendored data (pattern bundles etc.) is imported, not fetched: a one-time import script pinned to a commit SHA, plus
  the upstream LICENSE verbatim routed through `generateThirdPartyNotices`. No submodules, no build-time fetches. The
  `refresh-pattern-bundles` skill covers drift checks.

## GitHub Actions

- CI binaries (grype, cosign…) are installed by downloading the release tarball plus `checksums.txt` and verifying with
  `sha256sum --check`; `container-scan.yml` shows the shape. Never `curl | sh`, never the tool's own install script,
  never a hardcoded hash.
- Add workflow steps to the existing job that already did the prerequisite work; don't create new jobs. Scope
  `secrets.*` to the step's `env:`, not the job's.
- Action `uses:` pins are commit SHAs with a ratchet comment; the `/update-actions` command bumps them.

## Docker

- Docker is the primary distribution. The Dockerfile is fully self-contained (no host tooling assumed) and image
  references always carry the `docker.io/` registry prefix.
- Dockerfile digest pins must be multi-arch _index_ digests, verified with `skopeo inspect --raw … | sha256sum`. PR CI
  builds amd64 only; arm64 breakage only surfaces post-merge.
- `compose.sh` is for the main stack only. The perf harness uses bare `docker compose -f perf/docker-compose.yml` per
  `perf/README.md`.
