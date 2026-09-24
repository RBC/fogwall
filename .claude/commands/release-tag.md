---
name: release-tag
description:
  Phase 2 of a release — tag the release commit on main or a release/* branch, publish the GitHub release, then move the
  branch to the next -SNAPSHOT version (PR on main, direct push on release/*).
user-invocable: true
allowed-tools:
  - Bash
  - Read
---

# /release-tag — Tag, publish, and start the next development iteration.

Phase 1 (`/release`) landed the commit that sets the exact release version on the base branch. This command tags that
commit, which triggers the Release Publish workflow, then moves the branch back to a `-SNAPSHOT` version so it is never
mistaken for a released build.

Pushing the tag makes `release-publish.yml` promote the **commit-pinned `build-<sha>` image** for the tagged commit to
the release tags — it does not rebuild, and it does not use `:edge`, so the image released is exactly the one CI built
and scanned for that commit. This works the same on `main` and on `release/*` branches; both build `build-<sha>` images.
The image must carry `org.opencontainers.image.version=<version>` (set from `build.gradle` at build time) or the
promotion is refused. The release always gets `:<version>`; `:X.Y`, `:X` and `:latest` move only when it is at least as
new as the image each points at now (`scripts/release_image_tags.py`), so a patch on an older line leaves `:latest` on
the newer minor.

The tag ruleset is the release gate on both kinds of branch: it refuses a tag whose commit has not passed the required
checks. On `main` those checks also ran on the PR; on `release/*` — direct-push branches — the push itself is what
triggers them, so the tag ruleset is the only gate, and it is sufficient.

Arguments passed: `$ARGUMENTS`

`$ARGUMENTS` is the version string without the `v` prefix, e.g. `1.5.0` or `1.4.2`.

---

## Steps

1. **Validate the argument.** If blank or not semver, stop and ask.

2. **Identify the base branch.** Run `git branch --show-current`. It must be `main` or `release/X.Y.x`; otherwise stop.
   Everything below uses `<base>` — do not assume `main`.

3. **Verify the version — and that it is not a snapshot.** Read `build.gradle` and confirm the `version` in
   `allprojects` equals the argument exactly. If it still carries `-SNAPSHOT`, **stop**: the release commit has not
   landed (or you are on the wrong branch). A snapshot is never tagged.

4. **Verify checks passed on the base branch.** Run:

   ```
   gh run list --branch <base> --limit 8 --json name,status,conclusion,headSha
   ```

   For the run set whose `headSha` is the release commit, all of these must show `conclusion: "success"`:
   - `CI / Build & Test`
   - `CI / E2E Test`
   - `CodeQL / java-kotlin`
   - `CodeQL / actions`
   - `CVE / Gradle`
   - `CVE / npm`
   - `Container Scan`

   If any is in progress or failed, tell the user and stop.

5. **Verify the tag doesn't already exist.** `git tag -l v<version>`. If it exists, stop — tags on this repo are
   immutable.

6. **Sync and verify HEAD.**

   ```
   git fetch origin <base>
   git rev-parse HEAD
   git rev-parse origin/<base>
   ```

   If they differ, **stop**:

   > Local HEAD (`<sha>`) is not `origin/<base>` (`<sha>`). CI built and scanned `origin/<base>`; tagging another commit
   > breaks the promote-not-rebuild flow. Run `git switch <base> && git reset --hard origin/<base>` and re-run.

7. **Create the signed annotated tag.**

   ```
   git tag -s v<version> -m "Release v<version>"
   ```

   If signing hangs, it is the passphrase prompt — wait for the user. Do not disable signing. Only if no signing key is
   configured at all, fall back to `git tag -a` and say that signing was skipped.

8. **Show and confirm.** `git show v<version> --stat`, then ask: "Push the tag? This triggers the Release Publish
   workflow." On yes, `git push origin v<version>`. On no, remind them to push manually and stop.

9. **Publish the GitHub release.** The `release-notes` skill may already have left a curated draft under a placeholder
   tag `<version>-draft-<timestamp>`. Prefer it:

   ```
   DRAFT=$(gh release list --limit 50 --json tagName,isDraft \
     --jq '.[] | select(.isDraft and (.tagName | startswith("<version>-draft-"))) | .tagName' | head -1)
   ```

   - Draft found → publish it on the real tag:
     `gh release edit "$DRAFT" --tag v<version> --title "v<version>" --draft=false`
   - No draft → `gh release create v<version> --title "v<version>" --generate-notes`

   Add `--prerelease` in either case when the version has a pre-release suffix (`-rc.N`, `-beta.N`, …). Show the release
   URL.

10. **Start the next development iteration.** The base branch must not sit at a released version once the tag exists.
    Compute the next snapshot from the base:

    - `main` → next **minor**: `1.5.0` → `1.6.0-SNAPSHOT`
    - `release/X.Y.x` → next **patch**: `1.4.2` → `1.4.3-SNAPSHOT`

    Set `version = '<next-version>'` in `build.gradle` (Edit tool). Leave `Chart.yaml`'s `appVersion` at the released
    version — it tracks what is released, not what is in development. Run `./gradlew spotlessApply`, then commit
    `build.gradle` only:

    ```
    chore: start <next-version> development
    ```

    **On `main` — PR with auto-merge** (do this from a branch, `git switch -c chore/next-<next-version>`, before
    committing):

    ```
    git push -u origin chore/next-<next-version>
    gh pr create --base main --title "chore: start <next-version> development" --body ""
    gh pr merge --auto --merge
    ```

    Always `--merge`.

    **On `release/X.Y.x` — direct push** (commit on the release branch itself):

    ```
    git push origin release/X.Y.x
    ```

    Tell the user the release URL, the image tags that will be promoted, and that `<base>` is now (or, on `main`, will
    be once the PR merges) at `<next-version>`.
