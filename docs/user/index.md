# User Guide — Pushing Through fogwall

This guide is for **developers who push code through fogwall**. It covers setting up your git remote, understanding
proxy output, and what to do when a push is blocked or waiting for approval.

If you want to operate or configure fogwall, see the [Configuration Reference](../configuration/index.md). If you want
to build on or contribute to the codebase, see
[CONTRIBUTING.md](https://github.com/RBC/fogwall/blob/main/CONTRIBUTING.md).

## What fogwall does

fogwall sits between your `git push` and the upstream host (GitHub, GitLab, Bitbucket, etc.). Every push is inspected
before it reaches the upstream:

- Commit author emails are checked against allowed domains
- Commit messages are scanned for blocked patterns
- Diff content is scanned for sensitive data and secrets
- Commit trailers may be required or restricted (DCO `Signed-off-by`, `Co-authored-by`)
- Your git identity is verified against your proxy account
- You may need approval from a reviewer before the push is forwarded

If everything passes, your push lands on the upstream as normal. If something fails, the push is rejected and you get a
message explaining what to fix.

## Before you start

You need the following from your administrator before you can push through the proxy:

1. **The proxy URL** — something like `https://fogwall.corp.example.com` or `http://localhost:8080` for local
   development.
2. **A proxy user account** — username and password for the fogwall dashboard. This is separate from your upstream SCM
   credentials.
3. **A personal access token (PAT)** for the upstream SCM — the proxy forwards your token to authenticate with
   GitHub/GitLab/etc. on your behalf.
4. **Push permission on the target repo** — the administrator must grant you `PUSH` permission for the specific
   repository you want to push to.
5. **Your SCM identity registered** — the proxy verifies that your token resolves to the same person as your proxy
   account. Your administrator needs to add your upstream username (e.g. your GitHub login) to your proxy user profile.

If the admin has configured `attribution-policy` in `warn` mode, pushes will go through even without a registered SCM
identity, but you will see a warning in the push output. If it is set to `strict`, pushes will be blocked until your
identity is registered.

## Contents

- [Setting up your remote](remotes.md) — the proxy URL, credentials, and the token scopes you need
- [SSH remotes](ssh-remotes.md) — pushing over SSH and how your key identifies you
- [Choosing a proxy mode: `/server/` vs `/proxy/`](proxy-modes.md) — `/server/` versus `/proxy/`, and what changes
- [Commit trailer requirements (DCO / co-authors)](commit-trailers.md) — DCO sign-off and co-author trailers
- [What a successful push looks like](successful-push.md) — reading the `remote:` output of a push that went through
- [Understanding the approval workflow](approval-workflow.md) — auto-approve, review-required, and attestation questions
- [Reviewing a push](reviewing-a-push.md) — push record states, approving, rejecting, self-certifying
- [When a push is blocked](blocked-pushes.md) — what each block message means and what to do about it
- [Identity verification](identity-verification.md) — how fogwall works out who you are, over HTTP and SSH
- [Contributions (PRs, MRs & issues through fogwall)](contributions.md) — opening PRs, MRs and issues with `gh`, `glab`,
  `tea` and `fj`
- [User permissions vs access rules](permissions-vs-access-rules.md) — why a repo can be blocked even when you have push
  rights
- [Common problems](troubleshooting.md) — credential prompts, TLS errors, rejected pushes
- [Tips](tips.md) — cloning through the proxy, multiple remotes, scrubbing history
