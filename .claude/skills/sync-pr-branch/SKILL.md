---
name: sync-pr-branch
description:
  Bring an open PR branch up to date with origin/main by rebasing (never merging), force-push it, and re-arm auto-merge.
  Use when a PR is behind main, when `gh pr view` shows mergeStateStatus BEHIND or UNKNOWN with green checks, or right
  after any force-push.
allowed-tools: Bash
---

## Why rebase, not merge

The repo only allows merge-commit merges (squash and rebase merges are disabled). Merging `origin/main` into the branch
would put a second merge commit into history when the PR lands. Rebasing keeps the branch linear and, done locally,
keeps every commit signed with the developer's key rather than GitHub's. The Bash guard hook blocks `git merge main` and
`git rebase -i` for these reasons.

## Steps

1. Confirm the working tree is clean for tracked files (`git status --short`); untracked scratch files are fine. Never
   use bare `git stash` — worktrees share the stash stack.
2. `git fetch origin` and check the gap: `git rev-list --left-right --count origin/main...HEAD`.
3. `git rebase origin/main`. On a conflict, resolve it, `git add` the specific paths, `git rebase --continue`. If the
   conflict is not obviously mechanical, stop and show the user the hunks.
4. `git push --force-with-lease origin <branch>`.
5. Check auto-merge survived: `gh pr view <N> --json autoMergeRequest,mergeStateStatus`. A force-push often drops it;
   re-arm with `gh pr merge <N> --merge --auto`.
6. `mergeStateStatus: UNKNOWN` with green checks means GitHub has not recomputed mergeability yet or the branch is
   stalled behind main again; re-check after a minute before assuming the PR merged.

`gh pr edit` is broken against this repo (a Projects classic GraphQL error); update PR bodies with
`gh api -X PATCH repos/RBC/fogwall/pulls/<N> -F body=@<file>`.
