---
name: release
description:
  Phase 1 of a release — strip the -SNAPSHOT suffix (or set an explicit version) in build.gradle and the Helm chart,
  then open the bump PR (main) or push the bump straight to the release branch (release/*).
user-invocable: true
allowed-tools:
  - Bash
  - Read
  - Grep
  - Glob
---

# /release — Prepare a release: set the version and land the bump commit.

`main` and every `release/X.Y.x` branch sit at a `-SNAPSHOT` version between releases (`1.5.0-SNAPSHOT`,
`1.4.3-SNAPSHOT`). A release is the commit where that suffix is stripped. This command makes that commit and lands it;
`/release-tag` tags it once it is on the base branch and then starts the next `-SNAPSHOT` iteration.

How the commit lands depends on the base branch:

- **`main`** requires a PR (branch ruleset), so the bump goes up as a PR with auto-merge.
- **`release/X.Y.x`** takes direct pushes — the release-branch ruleset is non-fast-forward and no-delete only, because
  patches are cherry-picked from already-reviewed main commits. The bump is committed on the release branch and pushed.
  The tag ruleset is the gate: `/release-tag` cannot tag a commit whose checks have not passed.

Examples: `/release` (strips the suffix), `/release 1.5.0`, `/release 1.5.0-rc.1`

Arguments passed: `$ARGUMENTS`

`$ARGUMENTS` is an optional version string without the `v` prefix. If omitted, the version is inferred from
`build.gradle`:

- `1.5.0-SNAPSHOT` → `1.5.0` (the normal case)
- `1.5.0-rc.1` → `1.5.0-rc.2`, `1.5.0-beta.2` → `1.5.0-beta.3` (pre-release trains increment)
- a version with no suffix at all → stop and ask; the branch is not at a snapshot, so the intended next version is not
  inferable.

---

## Steps

1. **Identify the base branch.** Run `git branch --show-current`.
   - `main` → the release is a minor (or major). Main never produces a patch: once a minor-worthy change has merged, the
     next release from main is that minor.
   - `release/X.Y.x` → the release is a patch on the `X.Y` line.
   - Anything else → stop. Releases are cut from `main` or a `release/*` branch only.

   Then `git fetch origin <base>` and confirm the local branch is at `origin/<base>`; if not, stop and say so. On a
   release branch this also confirms every cherry-pick meant for the patch has been pushed.

2. **Determine the new version.** Read the `version = '...'` line in the `allprojects` block of `build.gradle`.
   - If `$ARGUMENTS` is a valid semver / semver-pre string, use it as-is.
   - If blank, infer as described above. Show the inferred version and confirm before proceeding.
   - Sanity-check against the base: on `release/1.4.x` the version must be `1.4.Z`; on `main` it must be greater than
     the newest tag's minor. If it is not, stop and ask.
   - If `$ARGUMENTS` is present but not a version string, stop and ask.

3. **Show the current state.** Run `git tag --sort=-version:refname | head -5` and show the current version, the new
   version, and the most recent tags.

4. **On `main` only, create the bump branch:** `git switch -c chore/bump-<new-version>`. On a release branch, stay on it
   — the commit lands there directly.

5. **Set the version in both places.** Use the Edit tool:
   - `build.gradle`, `allprojects` block: `version = '<new-version>'`
   - `charts/fogwall/Chart.yaml`: `appVersion: "<new-version>"` — the chart's `appVersion` tracks the released
     application version. Leave the chart's own `version:` alone; it is the chart's version, not fogwall's.

6. **Run `./gradlew spotlessApply`** so formatting is clean before committing.

7. **Ask about additional changes.** Run `git diff --stat`, show it, and ask: "Any other changes to include in this
   commit?" Apply them if so.

8. **Commit.** Stage `build.gradle`, `charts/fogwall/Chart.yaml`, and any files the user named — explicit paths, never
   `-A`:

   ```
   chore: release <new-version>
   ```

   No `closes #N` and no co-author trailer on version bumps.

9. **Land it.**

   **On `main` — PR with auto-merge:**

   ```
   git push -u origin chore/bump-<new-version>
   gh pr create --base main --title "chore: release <new-version>" --body ""
   gh pr merge --auto --merge
   ```

   Always `--merge` — never squash or rebase merges on this repo. Tell the user:

   > PR opened for `chore/bump-<new-version>` with auto-merge enabled. It merges once checks pass.
   >
   > **Once merged**, run `/release-tag <new-version>` from `main` to tag, publish, and start the next snapshot.
   >
   > Watch checks: `gh run list --branch chore/bump-<new-version> --limit 4`

   **On `release/X.Y.x` — direct push:**

   ```
   git push origin release/X.Y.x
   ```

   Tell the user:

   > Pushed `chore: release <new-version>` to `release/X.Y.x`. CI runs on the push; the tag ruleset will not accept
   > `v<new-version>` until those checks are green on this commit.
   >
   > **Once green**, run `/release-tag <new-version>` from `release/X.Y.x`.
   >
   > Watch checks: `gh run list --branch release/X.Y.x --limit 6`

   **Stop here in both cases.** Do not create or push a tag. The tag ruleset rejects a tag whose commit has not passed
   the required checks.
