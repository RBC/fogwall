# User Guide — Pushing Through fogwall

This guide is for **developers who push code through fogwall**. It covers setting up your git remote, understanding
proxy output, and what to do when a push is blocked or waiting for approval.

If you want to operate or configure fogwall, see the [Configuration Reference](CONFIGURATION.md). If you want to build
on or contribute to the codebase, see [CONTRIBUTING.md](../CONTRIBUTING.md).

---

## What fogwall does

fogwall sits between your `git push` and the upstream host (GitHub, GitLab, Bitbucket, etc.). Every push is inspected
before it reaches the upstream:

- Commit author emails are checked against allowed domains
- Commit messages are scanned for blocked patterns
- Diff content is scanned for sensitive data and secrets
- Your git identity is verified against your proxy account
- You may need approval from a reviewer before the push is forwarded

If everything passes, your push lands on the upstream as normal. If something fails, the push is rejected and you get a
message explaining what to fix.

---

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

If the admin has configured `identity-verification: warn`, pushes will go through even without a registered SCM
identity, but you will see a warning in the push output. If it is set to `strict`, pushes will be blocked until your
identity is registered.

---

## Setting up your remote

The proxy URL is structured as:

```text
http[s]://<proxy-host>/<mode>/<provider-host>/<owner>/<repo>.git
```

For example, if you normally push to `https://github.com/myorg/myrepo`, the proxy remote is:

```text
https://fogwall.corp.example.com/push/github.com/myorg/myrepo
```

Add it as a new remote (recommended — keeps your direct-to-GitHub remote as a fallback):

```shell
git remote add proxy https://fogwall.corp.example.com/push/github.com/myorg/myrepo
```

Then push via the proxy:

```shell
git push proxy main
```

### Credentials in the remote URL

The git push path (`/push/` and `/proxy/`) uses HTTP Basic authentication — this is what the git protocol requires, and
it matches what the upstream SCM expects. Your upstream PAT is the password; the username can be any non-empty string —
`me`, `git`, your name — it is not used for identity resolution (see [Identity verification](#identity-verification)
below). It must not be empty or the upstream SCM will reject the request. The exception is Bitbucket — see below.

This is separate from the dashboard: the dashboard login uses your proxy user account (via your org's IdP or local
credentials), not your SCM token. The two credential sets are independent — one is for `git push`, the other is for the
web UI.

Embed credentials directly in the URL if your git credential helper does not pick them up automatically:

```shell
git remote add proxy https://me:ghp_yourtoken@fogwall.corp.example.com/push/github.com/myorg/myrepo
```

Or use `git credential store` / your OS keychain as you normally would.

<!-- prettier-ignore-start -->
> [!TIP]
> Most credential helpers (macOS Keychain, Windows Credential Manager, `git-credential-store`) pin credentials to a hostname. Since the proxy hostname differs from the upstream SCM, your helper won't automatically supply the right token — it will look for a stored credential for `fogwall.corp.example.com`, not `github.com`. Either store a separate credential entry for the proxy hostname, or embed the token in the remote URL as shown above. For local development environments that are frequently recreated, embedding the token in the URL is simpler than managing keychain entries.
<!-- prettier-ignore-end -->

<!-- prettier-ignore-start -->
> [!WARNING]
> **Bitbucket only:** the username in the remote URL must be your Bitbucket account email address (e.g. `you@company.com`). This is required for identity resolution — see the [Configuration Reference](CONFIGURATION.md#bitbucket-identity-resolution) for details.
<!-- prettier-ignore-end -->

### Required token scopes

The proxy calls the SCM API to resolve your identity. Your PAT needs at least:

| Provider         | Minimum scope                                                          |
| ---------------- | ---------------------------------------------------------------------- |
| GitHub           | No additional scopes required (classic or fine-grained PATs both work) |
| GitLab           | `read_user`                                                            |
| Bitbucket        | `read:user:bitbucket` and `write:repository:bitbucket`                 |
| Codeberg / Gitea | `read:user`                                                            |

---

## SSH remotes

If your administrator has configured the proxy with an SSH provider, you can push over SSH instead of HTTPS. SSH pushes
do not use a PAT — your identity is tied to your SSH key instead.

### Setting up SSH

1. **Register your SSH public key** in the proxy dashboard (profile → SSH keys → add key). This is the key you use to
   connect to the proxy, not directly to the SCM. If you already have a key at `~/.ssh/id_ed25519.pub`, paste its
   contents.

2. **Register the same key on the upstream SCM** (e.g. GitHub → Settings → SSH keys; Gitea/Codeberg → Settings → SSH
   keys). The proxy verifies that the key you connected with is also registered on your SCM account. If it is not, the
   push is blocked.

3. **Enable agent forwarding.** The proxy needs your SSH agent to authenticate outbound connections to the upstream SCM.
   Add a `ForwardAgent yes` entry in your `~/.ssh/config`:

   ```
   Host <proxy-host>
     ForwardAgent yes
   ```

   Or pass `-A` on the command line: `GIT_SSH_COMMAND="ssh -A" git push`.

4. **Add an SSH remote.** Your administrator will give you the proxy SSH hostname and port. SSH push URLs look like:

   ```text
   ssh://proxy-host:2222/<scm-host>:<scm-ssh-port>/<owner>/<repo>.git
   ```

   For example, pushing to a Gitea instance at `git@gitea.corp.example.com`:

   ```shell
   git remote add proxy ssh://fogwall.corp.example.com:2222/gitea.corp.example.com:22/myorg/myrepo.git
   git push proxy main
   ```

   Check the **Providers** page in the dashboard to see the exact SCM host and port for each configured SSH provider —
   it shows the upstream URI verbatim, exactly as your administrator configured it. This matters because whether the
   `<scm-ssh-port>` segment is needed is an exact match against that URI string, not a "is this the default port" check:
   if the port was written explicitly there, include it; if it wasn't, omit it entirely (including it when it's not
   expected is itself a mismatch).

   **Why not the `git@host:owner/repo.git` shorthand you're used to from GitHub?** That shorthand syntax has no way to
   specify a non-default SSH port, and fogwall's SSH listener normally runs on a non-standard port (`2222` by default)
   rather than `22`. It's the same underlying SSH protocol either way — just ask your administrator whether they've
   exposed the proxy's SSH port behind a standard `:22` mapping. If so, the shorthand form works too (same caveat about
   the `<scm-ssh-port>` segment applies):

   ```shell
   git remote add proxy git@fogwall.corp.example.com:gitea.corp.example.com:22/myorg/myrepo.git
   ```

### SSH identity verification

SSH pushes are subject to the same compliance guarantee as HTTP pushes. The proxy:

1. Verifies your SSH key against the fogwall user database (MINA public-key auth).
2. Calls the upstream SCM API to fetch the SSH public keys registered on your linked SCM identity.
3. Checks that the connecting key's SHA-256 fingerprint appears in that list.

If step 3 fails — for example because you have a key registered in fogwall but not on your SCM account — the push is
blocked. Add the key to your SCM account and retry. If the provider or SCM identity is misconfigured, contact your
administrator.

There is no token to supply for SSH pushes — no `Authorization` header, no credential in the URL. The agent-forwarded
key is the only credential.

---

## Choosing a proxy mode: `/push/` vs `/proxy/`

There are two URL prefixes, each with different behaviour:

|                       | `/push/` (store-and-forward)                                                                      | `/proxy/` (transparent proxy)                                                                                                        |
| --------------------- | ------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| **How it works**      | The proxy receives your push locally, validates it, then forwards to upstream                     | The proxy forwards HTTP requests directly to upstream while inspecting them inline                                                   |
| **Terminal feedback** | Live streaming — each validation step prints as it runs                                           | Silent until the end — one response after all checks complete                                                                        |
| **Approval workflow** | Push stays open waiting for approval; same `git push` command completes once approved             | Push is blocked and you must run `git push` again after a reviewer approves — the second push is matched to the existing push record |
| **Push record**       | Every push is persisted with a full event history                                                 | Every push is persisted; the re-push after approval references the same record                                                       |
| **Local disk usage**  | Clones each repo to ephemeral pod storage for diff inspection — proportional to repo history size | None — git bytes stream directly through the proxy with no local storage                                                             |
| **Recommendation**    | **Use this for most workflows**                                                                   | Use when network reliability or disk constraints are a concern                                                                       |

For day-to-day use, `/push/` gives a better experience: you see each validation step in real time and the same
`git push` command completes once approved.

Prefer `/proxy/` if your network infrastructure is flaky or connections between client → proxy → upstream are
unreliable. Store-and-forward keeps the client connection open for the full validation and approval cycle — a dropped
connection means starting over. Transparent proxy completes each HTTP request atomically, so a network hiccup during
approval does not lose the push record.

### Disk usage in store-and-forward mode

In S&F mode, the proxy maintains local mirrors of each upstream repository on ephemeral pod storage (`emptyDir` in
Kubernetes/OpenShift). A full clone is kept for the serve path (so clients can fetch through the proxy) and a shallow
clone (depth 100) for diff inspection. These are rebuilt automatically on pod restart — there is no durable state in the
cache.

**Large repositories** (deep history, large binaries, monorepos) can consume significant disk on the proxy pod.
Operators should set an `emptyDir.sizeLimit` in the pod spec to prevent runaway clones from exhausting node disk:

```yaml
volumes:
  - name: tmp
    emptyDir:
      sizeLimit: 5Gi
```

If disk pressure becomes an issue for a specific large repo, route it through `/proxy/` instead — transparent proxy mode
uses zero local disk and shifts the concern purely to network reliability between the proxy and upstream. A transient
network failure during a push just means the developer retries; the push record is preserved.

---

## What a successful push looks like

```text
$ git push proxy my-feature
Enumerating objects: 4, done.
Counting objects: 100% (4/4), done.
Delta compression using up to 20 threads
Compressing objects: 100% (2/2), done.
Writing objects: 100% (3/3), 523 bytes | 523.00 KiB/s, done.
Total 3 (delta 1), reused 0 (delta 0), pack-reused 0 (from 0)
remote: Resolving deltas: 100% (1/1)
remote: 🔑  Checking URL allow rules...
remote:   ✅  repository allowed
remote: 🔑  Checking user permission...
remote:   ✅  user authorized
remote: 🔑  Verifying commit identity...
remote:   ✅  identity verified
remote: 🔑  Checking branch...
remote:   ✅  branch OK
remote: 🔑  Checking for hidden commits...
remote:   ✅  no hidden commits
remote: 🔑  Checking author emails...
remote:   ✅  emails OK
remote: 🔑  Checking commit messages...
remote:   ✅  messages OK
remote: 🔑  Scanning diff content...
remote:   ✅  clean
remote: 🔑  Checking GPG signatures...
remote:   ✅  signatures OK
remote: 🔑  Scanning for secrets...
remote:   ✅  no secrets detected
remote:
remote: ────────────────────────────────────────
remote: 🔗  View push record: http://fogwall.corp.example.com/dashboard/push/4d6196fb-...
remote: ✅  Push approved by reviewer
remote: 🔗  Forwarding to https://github.com/myorg/myrepo.git...
remote:   ✅  refs/heads/my-feature -> OK
remote: ✅  Forwarding complete
To http://fogwall.corp.example.com/push/github.com/myorg/myrepo.git
 * [new branch]      my-feature -> my-feature
```

Each `remote:` line is a validation step streaming in real time. The example above shows `ui` approval mode — a reviewer
approved in the dashboard before the push was forwarded. In `auto` mode the `✅ Push approved by reviewer` line is
replaced by immediate forwarding with no wait.

---

## Understanding the approval workflow

What happens after validation depends on how the administrator has configured the approval mode:

### Auto-approve (`approval-mode: auto`)

Clean pushes (no validation failures) are immediately approved and forwarded. You see output like the example above — no
human reviewer is needed. This is the typical setting for solo developers or teams that use validation as a guardrail
without a manual review step.

### Review required (`approval-mode: ui`)

After validation passes, the push enters a **PENDING** state and waits for a reviewer to approve it in the dashboard.
You will see:

```text
remote: 🔗  View push record: http://fogwall.corp.example.com/dashboard/push/4d6196fb-...
remote: ⚠  Push requires review. Waiting for approval...
remote: 🔑  Push ID: 4d6196fb-4cc3-47d1-ac6d-17fbcc5f71d3
remote:    Review at: http://fogwall.corp.example.com/dashboard/push/4d6196fb-...
remote: Awaiting review... (5s elapsed, ~1794s remaining)
remote: .
remote: Awaiting review... (10s elapsed, ~1789s remaining)
```

The push command stays open, printing keepalive dots while it waits. Once a reviewer approves in the dashboard, the
proxy forwards the push and the command completes:

```text
remote: ✅  Push approved by reviewer
remote: Updating references: 100% (1/1)
remote: 🔗  Forwarding to https://github.com/myorg/myrepo.git...
remote:   Pushing 1 ref(s) to upstream...
remote:   ✅  refs/heads/my-feature -> OK
remote: ✅  Forwarding complete
To http://fogwall.corp.example.com/push/github.com/myorg/myrepo.git
 * [new branch]      my-feature -> my-feature
```

If no approval comes, your git client will eventually time out. You can re-run the push — it will resume waiting for
approval on the existing push record rather than creating a new one.

### Attestation questions

The administrator may configure attestation questions that you must answer before a push is approved. These appear in
the dashboard push record view, not in the terminal. A reviewer (or yourself, if you have `SELF_CERTIFY` permission for
the repo) answers them as part of the approval step. A question may carry one or more linked references (e.g. a link to
the internal policy the attestation is checking against) — these render as clickable links alongside the question so
reviewers can check the source policy before attesting.

---

## Reviewing a push

If you have been asked to review a push, or you are an administrator, log in to the dashboard and open the **Pushes**
page. Pushes awaiting review have status **PENDING**.

### Push record states

| State       | Meaning                                                        |
| ----------- | -------------------------------------------------------------- |
| `RECEIVED`  | Push has arrived and is being processed                        |
| `PENDING`   | Validation passed; awaiting a reviewer's decision              |
| `APPROVED`  | Approved by a reviewer (or self-certified) — will be forwarded |
| `FORWARDED` | Successfully sent to the upstream SCM                          |
| `REJECTED`  | Reviewer declined the push                                     |
| `BLOCKED`   | Validation failed — push will not be forwarded                 |
| `CANCELED`  | Canceled by the pusher or an administrator                     |

### Approving or rejecting

Open the push record to see the full diff, commit list, and validation results. You can:

- **Approve** — forwards the push to the upstream. If attestation questions are configured, you must answer them before
  approving.
- **Reject** — blocks the push. The reason field is optional but recommended — it is shown to the pusher in the
  dashboard and helps them understand what to fix.

The reason field is recorded in the audit log regardless of whether it is shown to the pusher.

### Self-certification

If you have `SELF_CERTIFY` permission for the repository, you can approve your own pushes from the push record view. The
approval is recorded in the audit log with a self-certification flag, distinguishing it from peer review. Attestation
questions still apply.

### Who can review

By default any authenticated user can review any push they did not push themselves. If your administrator has set
`server.require-review-permission: true`, you need an explicit `REVIEW` permission entry for the repository to approve
or reject. Contact your administrator if you receive a 403 trying to approve a push.

---

## When a push is blocked

In store-and-forward mode (`/push/`), each validation step streams live and all failures are summarised at the end. A
push with multiple issues across several commits looks like this:

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
To http://fogwall.corp.example.com/push/github.com/myorg/myrepo.git
 ! [remote rejected] my-feature -> my-feature (5 validation issue(s) - see above)
error: failed to push some refs to 'http://fogwall.corp.example.com/push/github.com/myorg/myrepo.git'
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

---

## Identity verification

The proxy confirms that the person pushing is who they say they are. The mechanism differs by transport.

### HTTP pushes

1. **Token → SCM username**: your PAT is used to call the SCM API (`GET /user`). The returned username must match the
   SCM identity registered in your proxy user profile. This check is **always enforced** — a push is blocked immediately
   if your token cannot be matched to a registered proxy user, regardless of any other settings.
2. **Commit emails → proxy user**: every author and committer email in the pushed commits must match an email address
   registered on your proxy account. This check is controlled by `identity-verification` — in `warn` mode mismatches are
   logged but the push proceeds; in `strict` mode the push is blocked.

**The HTTP Basic-auth username in your remote URL is not used for identity.** Use any value — `me`, `git`, your name —
it makes no difference. Only the password (your PAT) matters.

You can add and remove your own SCM identities and email addresses from your profile page in the dashboard. If your push
is blocked with "Identity Not Linked" or a commit email mismatch, log in to the dashboard and add the missing identity
or email under your profile before pushing again.

If you cannot resolve it yourself — for example, because the email address or SCM username is already registered to
another user — contact an administrator. Duplicate identity conflicts (two users claiming the same email or SCM handle)
require admin intervention to resolve.

### SSH pushes

SSH identity verification enforces the same compliance guarantee, but via SSH key fingerprint rather than a PAT:

1. **Public-key auth (connection gate):** your SSH key must be registered in your proxy profile.
2. **SCM fingerprint check (compliance gate):** the proxy calls the upstream SCM API and fetches the SSH public keys
   registered on your linked SCM identity. Your connecting key's fingerprint must appear in that list.

Both steps are required and both are always enforced — there is no warn-only mode for SSH identity. If either step fails
the push is blocked.

**Your SSH key must be registered in two places:** your fogwall profile, and your upstream SCM account. Registering it
in fogwall alone is not enough — the proxy cross-checks against the SCM to confirm the key actually belongs to you.

If you are blocked with "SSH key not linked to any SCM identity", add the key to your SCM account settings and retry.

---

## User permissions vs access rules

Two independent configuration layers control whether a push is allowed:

**Access rules** (`rules.allow` / `rules.deny`) are administrator-configured gates on which repositories the proxy will
handle at all. If a repository is not in the allow list, the proxy rejects requests for it immediately — no user-level
check is run. This is a site-wide filter.

**User permissions** (`permissions`) control which proxy users can perform which operations on specific repositories.
Even if a repository is in the allow list, you need an explicit `PUSH` permission entry to push to it.

The error message tells you which layer rejected the push — see [When a push is blocked](#when-a-push-is-blocked) for
the exact messages and what to do for each.

---

## Common problems

### Push hangs on credential prompt

Your git credential helper is prompting for the proxy URL but nothing appears. Embed credentials directly in the remote
URL or configure your credential helper to recognise the proxy host.

### `SSL certificate problem`

Your corporate PKI certificate is not trusted by your git client. Ask your administrator for the CA bundle and install
it:

```shell
git config http.sslCAInfo /path/to/corporate-ca.pem
```

Or for a specific remote only:

```shell
git config --local http.https://fogwall.corp.example.com.sslCAInfo /path/to/corporate-ca.pem
```

### Push succeeds but commits appear with wrong author

The push was forwarded using your PAT, but your `git config user.name` / `user.email` were not set correctly when you
committed. The upstream shows the author from the commit object — fix your git config and amend before pushing next
time.

### `error: src refspec main does not match any`

Standard git error — the branch name in your push command does not match a local branch. Not a proxy issue.

---

## Tips

### Clone through the proxy from the start

The recommended approach is to clone via the proxy rather than cloning directly from the upstream and adding a proxy
remote later. Most repos are permitted for both fetch and push — push-only access rules are the exception rather than
the norm. Cloning through the proxy means all activity is audited from the first checkout, and your `origin` remote is
already pointed at the proxy with no extra setup needed.

Use the **Clone via proxy** button on the **Repositories** page in the dashboard, or construct the URL manually:

```shell
# Clone directly through the proxy — origin is set to the proxy URL automatically
git clone https://me:ghp_yourtoken@fogwall.corp.example.com/proxy/github.com/myorg/myrepo
cd myrepo

# Confirm origin points at the proxy
git remote -v
```

If you need a reference to the upstream directly (e.g. to pull in upstream changes that are not yet in your fork), add
it as a second remote after cloning:

```shell
git remote add upstream https://github.com/myorg/myrepo
```

### Managing multiple remotes

If you already have a local clone pointed directly at the upstream, add the proxy as a named remote or redirect pushes
through it while keeping direct fetch:

```shell
git remote set-url --push origin https://fogwall.corp.example.com/push/github.com/myorg/myrepo
```

### Private forks and internal mirrors

If your org maintains a private internal fork of a public repo (e.g. a patched version of an upstream library), both can
be proxied independently. A common three-remote setup:

```shell
# upstream — the public project (fetch only, direct)
git remote add upstream https://github.com/someproject/somerepo

# origin — your org's internal fork (all traffic through proxy)
git remote add origin https://fogwall.corp.example.com/push/github.corp.example.com/myorg/somerepo

# The proxy URL reflects whichever provider hosts the fork —
# it does not have to be the same provider as upstream.
```

Each remote is a separate entry in the proxy's access rules and permission grants. Coordinate with your administrator to
ensure both the public upstream and internal fork URLs are configured.

### Finding proxy URLs from the dashboard

The **Repositories** page in the dashboard lists every repo that has seen activity through the proxy. Each entry has a
**Clone via proxy** button that copies the ready-to-use `git clone` command to your clipboard — useful when setting up a
new local clone or adding a proxy remote to an existing one.

The Clone button uses the `/proxy/` mode URL. Swap `/proxy/` for `/push/` if you want the store-and-forward path
instead.

> The repository only appears in the list after it has been pushed to or fetched through the proxy at least once. If you
> do not see it yet, push or fetch first.

### Scrubbing a commit history before pushing

If the proxy blocks your push due to secrets, blocked URLs, or disallowed commit authors in older commits, a simple
`git commit --amend` only fixes the tip. You need to rewrite history. The recommended tool is
[`git filter-repo`](https://github.com/newren/git-filter-repo):

```shell
# Remove a file that contained a secret from all history
git filter-repo --path path/to/secret-file --invert-paths

# Replace a hardcoded internal URL across all commits
git filter-repo --replace-text <(echo 'internal.corp.example.com==>REDACTED')

# Rewrite all commits by a specific author email to a new address
git filter-repo --email-callback 'return email.replace(b"old@corp.com", b"new@corp.com")'
```

After rewriting, force-push to a new branch and open a pull request rather than force-pushing to a protected branch. If
you're pushing through the proxy, the rewritten history will be re-validated from scratch — confirm the issues are gone
with a dry-run push to a test branch first.
