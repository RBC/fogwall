# Common problems

## Push hangs on credential prompt

Your git credential helper is prompting for the proxy URL but nothing appears. Embed credentials directly in the remote
URL or configure your credential helper to recognise the proxy host.

## Cloning a public repository asks for credentials, or fails in CI

fogwall determines whether to ask for credentials by checking whether the upstream repository serves anonymous reads. If
it cannot reach the upstream to check — a network timeout, an outbound proxy misconfiguration — it asks for credentials
rather than assuming the repository is public. In a non-interactive environment (`GIT_TERMINAL_PROMPT=0`, most CI
runners) that surfaces as an outright failure rather than a prompt.

Check the fogwall server log for a line about probing the upstream. If the upstream genuinely is private, supply a token
as normal.

## `SSL certificate problem`

Your corporate PKI certificate is not trusted by your git client. Ask your administrator for the CA bundle and install
it:

```shell
git config http.sslCAInfo /path/to/corporate-ca.pem
```

Or for a specific remote only:

```shell
git config --local http.https://fogwall.corp.example.com.sslCAInfo /path/to/corporate-ca.pem
```

## Push succeeds but commits appear with wrong author

The push was forwarded using your PAT, but your `git config user.name` / `user.email` were not set correctly when you
committed. The upstream shows the author from the commit object — fix your git config and amend before pushing next
time.

## `error: src refspec main does not match any`

Standard git error — the branch name in your push command does not match a local branch. Not a proxy issue.

## Push blocked as too large

fogwall accepts pushes up to a configured size — 64 MiB by default. Over that, the push is refused before any data is
read:

```text
remote: ⛔  Push Blocked - Too Large
remote: ❌  This push is 512 MiB; the limit is 64 MiB.
```

A push this large is usually one of three things: a binary or archive committed by mistake, generated build output that
should be in `.gitignore`, or a large file's entire history still present after it was deleted in a later commit (git
keeps every version). Check what is actually big:

```shell
git count-objects -vH
git rev-list --objects --all | git cat-file --batch-check='%(objecttype) %(objectname) %(objectsize) %(rest)' \
  | awk '$1=="blob" {print $3, $4}' | sort -rn | head
```

If the content genuinely belongs in the repository — a first push of a long-lived history, for example — talk to your
administrator rather than trying to split it. A one-time import is normally seeded directly upstream instead of pushed
through the proxy.

## Git LFS pushes are rejected

```text
Git LFS is not supported through fogwall at this time.
```

Git LFS moves file content outside the git protocol, so fogwall never sees the bytes and cannot make any statement about
them — secret scanning, content checks, and diff review would all inspect the small pointer file instead of the real
content. Rather than pass content it cannot inspect, fogwall refuses the upload.

Cloning and fetching repositories that already contain LFS objects is unaffected; only uploads are refused. If you need
LFS for a repository, raise it with your administrator.

## Push options (`git push -o`) are rejected

```text
Push options (git push -o ...) are not supported. Please push again without -o.
```

Push options are instructions to the hosting platform, not part of the commits: on GitLab they can open a merge request
or skip CI, on Gitea and Forgejo they can change a repository's visibility. fogwall's validation and approval steps
never see them, so rather than relay an instruction it cannot review, fogwall refuses the push. In server mode the
capability is not offered at all and git itself reports `the receiving end does not support push options`.

Push without `-o` and perform the platform-side action (opening a merge request, changing a setting) through the
platform's UI or CLI, where the usual permissions apply.
