# When a push is blocked

In server mode (`/server/`), each validation step streams live and all failures are summarised at the end. A push with
multiple issues across several commits looks like this:

```text
remote: 🔑  Checking URL allow rules...
remote:   ✅  repository allowed
remote: 🔑  Checking user permission...
remote:   ✅  user authorized
remote: 🔑  Verifying commit identity...
remote:   ⚠  2 commit email(s) not registered to thomas-cooper
remote: 🔑  Checking branch...
remote:   ✅  branch OK
remote: 🔑  Checking for hidden commits...
remote:   ✅  no hidden commits
remote: 🔑  Checking author emails...
remote:   ❌  blocked local part (noreply)
remote: 🔑  Checking commit messages...
remote:   ❌  contains blocked term: "WIP"
remote: 🔑  Scanning diff content...
remote:   ❌  Diff contains blocked content
remote: 🔑  Checking GPG signatures...
remote:   ✅  signatures OK
remote: 🔑  Scanning for secrets...
remote:   ❌  [github-pat]  ci-config.env:1
remote:   commit: e9085c9
remote:   match:  REDACTED
remote: ────────────────────────────────────────
remote: ⛔  Push Blocked - 5 validation issue(s)
remote: ❌  noreply@example.com: blocked local part (noreply)
remote:   → git config user.email "you@example.com"
remote: ❌  WIP: commit 2 — bad commit message: contains blocked term: "WIP"
remote:   → Messages must not contain: WIP, fixup!, squash!, DO NOT MERGE
remote:
remote: ⛔  Push Blocked - Diff Contains Blocked Content
remote: ❌  blocked term: "internal.corp.example.com" in config.yml
remote: ❌  blocked pattern: (?i)https?://[a-z0-9.-]*\.corp\.example\.com\b in config.yml
remote:
remote: ❌  [github-pat]  ci-config.env:1
remote:   commit: e9085c9
remote:   match:  REDACTED
remote: ────────────────────────────────────────
remote: 🔗  View push record: http://fogwall.corp.example.com/dashboard/push/b65bee10-...
To http://fogwall.corp.example.com/server/github.com/myorg/myrepo.git
 ! [remote rejected] my-feature -> my-feature (5 validation issue(s) - see above)
error: failed to push some refs to 'http://fogwall.corp.example.com/server/github.com/myorg/myrepo.git'
```

In transparent proxy mode (`/proxy/`), all validation runs first and the summary is returned in one response at the end.
The terminal output is otherwise identical to the above, but ends with:

```text
remote: push rejected by fogwall

fatal: the remote end hung up unexpectedly
error: failed to push some refs to 'http://fogwall.corp.example.com/proxy/github.com/myorg/myrepo.git'
```

Common block reasons and what to do:

| Message                                   | Fix                                                                                                                                                                                       |
| ----------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `author email '...' is not allowed`       | Your `git config user.email` does not match an allowed domain. Set it to your corporate email: `git config user.email you@corp.example.com` then amend or rebase to update the commits.   |
| `commit message contains blocked pattern` | Reword the commit message (`git commit --amend` or `git rebase -i`) to remove the blocked string.                                                                                         |
| `diff contains blocked content`           | The push contains content matching a deny rule (e.g. an internal hostname, a secret pattern). Remove it from the commit and amend/rebase.                                                 |
| `secret detected by gitleaks`             | A secret was found in the diff. Remove it from the commit history — a simple amend is not enough if the secret was ever committed; rewrite the history with `git filter-repo` or similar. |
| `Repository Not Allowed`                  | The repository is not in the proxy's allow list — it hasn't been enabled for use through the proxy at all. Contact your administrator to add it to the access rules.                      |
| `Repository Denied`                       | The repository is explicitly blocked by a deny rule. Contact your administrator.                                                                                                          |
| `Push Blocked - Unauthorized`             | The repository is allowed through the proxy but you do not have a `PUSH` permission entry for it. Contact your administrator to grant you access.                                         |
| `identity not resolved`                   | Your PAT did not resolve to a known SCM identity. Check your token scopes and ask your administrator to register your upstream username.                                                  |

After fixing the issue, push again normally — the proxy will re-validate from scratch.

> **Annotated tags:** the message you pass to `git tag -a -m "…"` is validated the same way a commit message is — the
> same blocked terms, patterns, and content-pattern (PII) checks apply. The tag's **tagger email** (git fills it from
> the same `user.email` as a commit's committer line) is likewise held to the committer email policy. If a tag push is
> blocked for its message or tagger, fix the cause (`git config user.email` for a tagger block), then re-create the tag
> (`git tag -d <tag>` then `git tag -a <tag> -m "…"`) and push again.
