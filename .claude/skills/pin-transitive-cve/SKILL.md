---
name: pin-transitive-cve
description:
  Fix a Dependabot, grype, or GHSA finding against a transitive dependency by pinning it in the Gradle build. Use when a
  CVE is reported for a library fogwall does not declare directly (Netty via lettuce, Jackson 2.x via gestalt, and so
  on).
allowed-tools: Bash, Read, Edit, Grep
---

## Where pins live

Two places in the root `build.gradle`, and the finding tells you which:

| Finding source                                    | Where the pin goes                                                                               |
| ------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| Image scan (grype), runtime dependency graph      | `subprojects { configurations.configureEach { resolutionStrategy.eachDependency { … } } }` table |
| Dependabot alert on the Gradle _plugin_ classpath | `buildscript { configurations.configureEach { resolutionStrategy { force '…' } } }` block        |

The `buildscript` force cannot fix an image-scan finding (it never reaches the runtime classpath), and the runtime table
cannot clear a plugin-classpath alert. Some artifacts need both entries; Jackson 2.x is the current example.

## Rules

- Never declare the vulnerable artifact as a dependency in any module, not even as a platform/BOM import. Pins only.
- Each `eachDependency` rule is keyed on group (and name when the group has siblings on other lines) and carries
  `details.because 'CVE fix: GHSA-…'` listing every advisory it closes. A comment above names which declared dependency
  pulls it in.
- Keep the table as the one auditable place; a rule is a no-op in modules where the group never appears, so there is no
  need to scope it.

## Steps

1. Identify the artifact and fixed version from the advisory. Confirm it is transitive:
   `./gradlew :fogwall-server:dependencyInsight --dependency <name> --configuration runtimeClasspath`.
2. Add or bump the rule in the right block above, with the `because` reason and the GHSA ids.
3. Re-run the insight task to see the new resolved version, then `./gradlew build` for the compile and unit tests.
4. If the finding came from an image scan, the real proof is the next `container-scan.yml` run on the PR; say so in the
   PR body rather than claiming it locally.
