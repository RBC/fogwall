-- Merge pull/merge requests through fogwall's maintainer path. The SHA a merge produced, when the upstream's own
-- response names one (GitLab does; GitHub's mergePullRequest response is only what gh itself selects, and
-- Forgejo/Gitea's merge endpoint returns no body at all, so this stays null for those two dialects).
--
-- A separate migration rather than folded into V15: a database that already ran V15 (the Playwright fixture, any dev
-- DB) must still pick the column up on the next boot, which only a new version does.
ALTER TABLE scm_api_action_records ADD COLUMN merge_commit_sha VARCHAR(64);
