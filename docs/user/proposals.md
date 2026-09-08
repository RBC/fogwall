# Proposals (PR/MRs through fogwall)

_Available since v1.4.0, if your administrator has enabled it per-provider._

Point your SCM CLI at fogwall and open and iterate on a pull or merge request as you normally would — fogwall inspects
and forwards the traffic instead of you talking to the SCM's API directly. Issue commands work the same way.

Two things apply to every provider below:

- **Each provider has its own endpoint, and it is never a URL path.** Which form it takes depends on how your
  administrator deployed fogwall:

  | deployment              | what you configure                    |
  | ----------------------- | ------------------------------------- |
  | a port per provider     | `fogwall.corp.example.com:9443`       |
  | a hostname per provider | `fogwall-github-api.corp.example.com` |

  Either way it is separate from the address you push git through, and the CLIs accept only a host — optionally with a
  port — so there is nowhere to put a path even if you wanted to. It is always reached over HTTPS; the CLIs offer no way
  to ask for plain HTTP. **Get the actual value from your administrator or your internal documentation**; the examples
  below use a placeholder.

- **Reviewing and merging aren't proxied.** Approving, requesting changes and merging happen in the SCM's own web UI.

- **What you write is scanned before it is sent.** Titles, descriptions and comment bodies go through the same checks as
  a push: blocked terms and patterns, secret scanning, and — where your administrator has enabled them — the built-in
  PII/identifier patterns (national ID numbers, IBANs and similar). A match refuses the request and tells you which rule
  matched; nothing is sent upstream. Unlike a push, there is no reviewer to override it, because there is nothing held
  waiting for one — edit the text and run the command again.

## GitHub — `gh`

```shell
export GH_HOST="<fogwall-github-endpoint>"       # host:port or a dedicated hostname — ask your administrator
export GH_ENTERPRISE_TOKEN="<your PAT>"          # NOT GH_TOKEN — see below
gh issue create -R <fogwall-github-endpoint>/<owner>/<repo> --title "..." --body "..."
gh pr create    -R <fogwall-github-endpoint>/<owner>/<repo> --base main --head <your-branch> ...
```

**Token:** bring your own personal access token, with enough scope and permission for the operations you're running.

Two `gh` quirks worth knowing, because neither fails in an obvious way:

- **Use `GH_ENTERPRISE_TOKEN`, not `GH_TOKEN`.** `gh` reserves `GH_TOKEN` for github.com itself and ignores it for any
  other host, so setting it gets you a 401 that looks like a rejected token. `GITHUB_ENTERPRISE_TOKEN` works too.
- **`gh auth login` does not work against fogwall.** Set the environment variables above instead.

**What's allowed:** `gh issue create/edit/close/comment` and `gh pr create/edit/close/comment`, on repositories where
you hold the `PROPOSE` permission (ask your administrator if you're not sure). Anything else — another mutation type, or
a repo you don't have `PROPOSE` on — is rejected with a clear error, the same as an unauthorized `git push`. Read
commands (`gh issue list`, `gh pr view`, etc.) are unaffected by your `PROPOSE` grants; they're gated separately by your
administrator's provider-level configuration.

## GitLab — `glab`

```shell
export GITLAB_HOST="<fogwall-gitlab-endpoint>"   # host:port or a dedicated hostname — ask your administrator
export GITLAB_TOKEN="<your PAT>"                 # your own token, `api` scope — see below
glab issue create -R <owner>/<repo> --title "..." --description "..."
glab mr create     -R <owner>/<repo> --source-branch <your-branch> --target-branch main --title "..." ...
```

**Token:** bring your own GitLab personal access token with `api` scope.

**`glab mr create` needs a matching git remote.** It refuses to run unless one of the repository's remotes points at
whatever `GITLAB_HOST` is set to, so add one alongside your normal origin:

```shell
git remote add glab-proxy-do-not-use https://<fogwall-gitlab-endpoint>/<owner>/<repo>.git
```

> [!WARNING] This remote is not a working git remote. It exists only to satisfy `glab`'s check — the endpoint serves the
> GitLab API, not git, so `git push` or `git fetch` through it will fail. Keep using your normal remote for all git
> operations.

**What's allowed:** `glab issue create/update/note/close` and `glab mr create/update/note/close`, on repositories where
you hold the `PROPOSE` permission (ask your administrator if you're not sure). Anything else is rejected with a clear
error, the same as an unauthorized `git push`. Read commands (`glab issue list`, `glab mr view`, etc.) are unaffected by
your `PROPOSE` grants; they're gated separately by your administrator's provider-level configuration.

## Gitea / Forgejo — `tea` and `fj`

Both CLIs share one endpoint, because they talk to the same API:

```shell
# tea (Gitea)
tea login add --name fogwall --url https://<fogwall-gitea-endpoint> --token "<your token>"
tea issue create --login fogwall --repo <owner>/<repo> --title "..." --description "..."
tea pr create    --login fogwall --repo <owner>/<repo> --head <your-branch> --base main --title "..."

# fj (Forgejo)
fj -H https://<fogwall-gitea-endpoint> issue create "..." --body "..."
fj -H https://<fogwall-gitea-endpoint> pr create    "..." --body "..."
```

**Token:** bring your own Gitea/Forgejo access token.

**What's allowed:** issue create/edit/close/comment and PR create/edit/close/comment, on repositories where you hold the
`PROPOSE` permission. One quirk worth knowing: **`tea pr close` and `tea pr edit` are the same request on the wire** —
`tea` sends a full object with `"state":"closed"` alongside every other field, so fogwall permits or denies them
together. It cannot tell them apart, and doesn't pretend to.

Read commands are unaffected by your `PROPOSE` grants, the same as for the other CLIs.

## Known limits

Labels, assignees and reviewer requests all work, on create and on edit. Two things do not:

- **`gh pr close --delete-branch`.** The close succeeds; deleting the branch does not. `gh` deletes a branch over
  GitHub's REST API, and this surface carries GraphQL only. Delete the branch with `git push --delete`, which goes
  through fogwall's git path as usual, or in the web UI.
- **`tea issue edit --remove-labels`.** Silently does nothing. `tea` resolves the labels and then sends no request at
  all, so there is nothing for fogwall to forward or refuse — it fails the same way without a proxy in the path.

Submitting or approving a review is not supported anywhere, by design: use the SCM's own UI. Requesting a reviewer is
supported.
