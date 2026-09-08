# Commit trailer requirements (DCO / co-authors)

Your administrator may require or restrict commit-message trailers. These are enforced per-commit, so a single offending
commit anywhere in the pushed range blocks the push — the rejection names the specific SHAs.

**`Signed-off-by` (Developer Certificate of Origin).** If sign-off is required, every commit must carry a
`Signed-off-by: Your Name <you@corp.com>` line. Add it as you commit with `-s`:

```bash
git commit -s -m "Fix the thing"          # new commit
git commit --amend --signoff              # add sign-off to the latest commit
git rebase --signoff <base>               # add sign-off across a range
```

If the policy also requires the sign-off to **match the author**, the `Signed-off-by` email must equal your commit
author email — set `git config user.email` to your work address before signing off.

**`Co-authored-by`.** Depending on policy, co-author trailers may be **banned** (remove any `Co-authored-by:` lines with
`git commit --amend`), **required** (add a `Co-authored-by: Name <email>` line), or **allowlisted** (only approved
co-author addresses are permitted — an unapproved one is rejected). The rejection message tells you which case applies
and how to fix it.

Both trailers are also recorded on the push record and shown per-commit in the dashboard, so they double as an
attribution audit trail even when no policy is configured.
