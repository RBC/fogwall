---
name: dependabot-roundup
description:
  Combine all open Dependabot PRs into one branch by cherry-picking their commits from the remote dependabot/* branches,
  push it, and open a single PR for auto-merge. Use for the weekly dependency sweep, or whenever several Dependabot PRs
  are open and waiting on `@dependabot rebase`.
allowed-tools: Bash
---

## Why

Dependabot opens one PR per ecosystem group (gradle, npm, actions, codeql, docker). Each goes stale as soon as another
merges, and `@dependabot rebase` costs a CI round trip per PR. Cherry-picking every open branch onto fresh `origin/main`
lands them all in one CI run. When the roundup merges, the same bumps are on `main`, so Dependabot closes the original
PRs itself; no `Closes #N` lines are needed.

## Which `gh` host

Host and credentials come from the maintainer's own environment, never from this file. Do not write token paths, tokens
or internal hostnames into the repo.

- Discovery and cherry-picking are pure git; `gh` is only for `gh pr create`, which goes through the SCM API proxy,
  configured per the maintainer's global Claude instructions.
- Always pass `--repo RBC/fogwall`. Never print git remotes.
- The Bash tool's output is not reliably shown to the user. Anything whose output the user must see (the push, with its
  approval link) is run by the user with the `!` prefix, not by Claude.

## Steps

1. Confirm tracked files are clean (`git status --short`).
2. Fetch the live Dependabot branches. Dependabot deletes a branch when its PR closes, so after pruning, whatever is
   left under `dependabot/*` on the remote is exactly the open set. The prune matters: stale local tracking refs from
   earlier fetches look like live branches.

   ```bash
   git fetch -q --prune origin 'refs/heads/dependabot/*:refs/remotes/origin/dependabot/*' origin main
   git for-each-ref --sort=creatordate --format='%(refname:strip=3)' refs/remotes/origin/dependabot
   ```

   If none, say so and stop.

3. Branch from fresh main: `git switch -c deps/weekly-$(date +%Y%m%d) origin/main`. If that name exists on the remote,
   append `-v2`, `-v3`.
4. For each branch, oldest first, cherry-pick its commits. Dependabot branches are often behind main, so range from the
   merge-base rather than `origin/main`:

   ```bash
   git cherry-pick -x "$(git merge-base origin/main origin/<branch>)..origin/<branch>"
   ```

   Conflicts happen when two PRs touch the same file (codeql and github-actions both edit workflow pins):
   - Same action pinned twice: keep the newer SHA/version, `git add <path>`, `git cherry-pick --continue`.
   - `package-lock.json`: take ours (`git checkout --ours`), then
     `npm install --package-lock-only --prefix fogwall-dashboard/frontend`, `git add`, continue.
   - Anything else, or anything not obviously mechanical: `git cherry-pick --abort`, record the PR as skipped with the
     conflicting paths, and carry on. Never leave a half-applied pick.

   Keep the picks as separate commits; they keep Dependabot's authorship as the provenance record.

5. If `build.gradle` changed, compile only: `./gradlew compileJava compileTestJava > <scratch>/build.log 2>&1`, and grep
   the file on failure. Tests, coverage and e2e are CI's job. If it fails, find the offending bump by dropping its
   commit (`git reset --hard` only to a commit on this local, unpushed branch), record it as skipped, and retry.
6. Push without force, and never quietly or filtered: fogwall prints the approval link in the push output. Give the user
   the command to run with `!` and wait for them to confirm it landed.

   A first push of a new ref records and validates only its tip commit, so a multi-commit branch shows one commit in the
   push record and its other commits skip the commit-metadata checks. Until that is fixed, push in two steps:

   ```
   ! git push origin <first-commit-sha>:refs/heads/<branch>
   ! git push origin <branch>
   ```

   The first creates the branch at the oldest picked commit; the second pushes the rest as an update to an existing
   branch, where every commit is listed and checked. Use `git log --reverse --format=%h origin/main..<branch> | head -1`
   for the first commit.

7. Open the PR (SCM API host). Terse body: a table of the included PRs, and a "Skipped" line with the reason for each,
   if any. Reference included PRs as `#N` (same repo; they auto-link). End the body with
   `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.

   ```bash
   gh pr create --repo RBC/fogwall --base main --head <branch> \
     --title "chore(deps): weekly dependency roundup" --body-file <scratch>/body.md
   ```

8. Auto-merge: the SCM API proxy does not allow the `enablePullRequestAutoMerge` mutation, so `gh pr merge --auto` fails
   with a 403 there. Do not retry it or route around the proxy; ask the user to enable auto-merge (merge commit) on the
   PR in the GitHub UI. Merges are always `--merge`. `gh pr edit` is broken on this repo; update bodies with
   `gh api -X PATCH repos/RBC/fogwall/pulls/<N> -F body=@<file>`.
9. Report: roundup PR URL, PRs included, PRs skipped and why. Skipped PRs stay open for Dependabot to rebase. After the
   roundup merges, any original PR still open a few minutes later means its bump did not land.
