---
name: release
description: Bump the project version in build.gradle and create a matching annotated git tag.
user-invocable: true
allowed-tools:
  - Bash
  - Read
  - Grep
  - Glob
---

# /release — Bump the project version and create a matching git tag.

You are modifying this project's Gradle build scripts to increment the version as well as create a new git tag to push &
initiate a release process (GitHub Actions workflow).

Examples: `/release 1.0.0-alpha.3`, `/release 1.0.0-beta.1`, `/release 1.0.0`

Arguments passed: `$ARGUMENTS`

`$ARGUMENTS` is an optional semantic version string (without the `v` prefix). If omitted, the next version is inferred
automatically by incrementing the last numeric component of the current version:

- `1.0.0-alpha.9` → `1.0.0-alpha.10`
- `1.0.0-beta.2` → `1.0.0-beta.3`
- `1.0.0-rc.1` → `1.0.0-rc.2`
- `1.2.3` → `1.2.4` (patch)

---

## Steps

1. **Determine the new version.**
   - Read the current version from `build.gradle` (line containing `version = '...'` in the `allprojects` block).
   - If `$ARGUMENTS` is provided and looks like a valid semver/semver-pre string, use it as-is.
   - If `$ARGUMENTS` is blank, auto-increment: for pre-release suffixes (`-alpha.N`, `-beta.N`, `-rc.N`) increment N;
     otherwise increment the patch component. Show the user the inferred version and confirm before proceeding.
   - If `$ARGUMENTS` is provided but doesn't look like a valid version string, stop and ask the user to correct it.

2. **Show the current state.** Run `git tag --sort=-version:refname | head -5` and show the user the current version and
   the inferred/provided new version alongside the most recent tags.

3. **Ensure a release branch exists.** Run `git branch --show-current` to check the current branch.
   - If already on a branch named `release/<new-version>`, continue.
   - If on `main` (or any other branch), create and switch to `release/<new-version>`:
     ```
     git checkout -b release/<new-version>
     ```

4. **Update `build.gradle`.** In the `allprojects { ... }` block, replace the existing `version = '...'` line with
   `version = '<new-version>'`. Use the Edit tool.

5. **Run `./gradlew spotlessApply`** to ensure formatting is clean before committing.

6. **Ask about additional changes.** Run `git diff --stat` and show the output to the user, then ask: "Any other changes
   to include in this commit?" Wait for their response. If they say yes, apply those changes before staging. If no,
   proceed.

7. **Commit the version bump.** Stage `build.gradle` plus any additional files the user specified and commit:

   ```
   chore: bump version to <new-version>
   ```

   No `closes #N`, no co-author trailer needed for version bumps.

8. **Push and open a PR with auto-merge.** Run:

   ```
   git push -u origin release/<new-version>
   ```

   Then create the PR and enable auto-merge:

   ```
   gh pr create --base main --title "chore: bump version to <new-version>" --body ""
   gh pr merge --auto --squash
   ```

   Tell the user:

   > PR created for `release/<new-version>` with auto-merge enabled. It will merge into main automatically once all
   > checks pass.
   >
   > **Once merged**, run `/release-tag <new-version>` to create and push the tag.
   >
   > Monitor check status: `gh run list --branch release/<new-version> --limit 4`

   **Stop here.** Do NOT create a tag or push tags. The tag ruleset on GitHub will reject the tag push if the required
   status checks haven't passed yet.
