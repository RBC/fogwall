# Contributions (PRs, MRs & issues through fogwall)

_Available since v1.4.0, if your administrator has enabled it per-provider._

## Overview

Point your SCM CLI at fogwall and open and iterate on a pull or merge request as you normally would — fogwall inspects
and forwards the traffic instead of you talking to the SCM's API directly. Issue commands work the same way.

If all you need is to **file or comment on an issue**, you don't need a CLI or a token at all: the dashboard's
**Issues** page does it for you, using the account you linked under **Profile**. That path needs only the narrow `ISSUE`
grant (or `PROPOSE`), and your administrator must have enabled it for the provider. The rest of this guide is the CLI
path.

Two permissions gate what you can do, and they're separate: `PROPOSE` lets you open and iterate on issues and pull/merge
requests; `MERGE` lets you merge one. Ask your administrator which grants you hold on a given repository.

Three things apply to every provider below:

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

- **Reviewing isn't proxied.** Approving and requesting changes happen in the SCM's own web UI.

- **What you write is scanned before it is sent.** Titles, descriptions and comment bodies go through the same content
  checks as a push — blocked terms and patterns, secret scanning. A match refuses the request and tells you which rule
  matched; nothing is sent upstream. Unlike a push, there is no reviewer to override it, because there is nothing held
  waiting for one — edit the text and run the command again.

## GitHub — `gh`

### Usage

Point `gh` at fogwall and run issue/PR commands as usual:

```shell
export GH_HOST="<fogwall-github-endpoint>"       # host:port or a dedicated hostname — ask your administrator
export GH_ENTERPRISE_TOKEN="<your PAT>"          # NOT GH_TOKEN — see below
gh issue create -R <fogwall-github-endpoint>/<owner>/<repo> --title "..." --body "..."
gh pr create    -R <fogwall-github-endpoint>/<owner>/<repo> --base main --head <your-branch> ...
```

### Authentication

Bring your own personal access token. Use a classic PAT with the **`repo` scope** — GitHub has no narrower classic scope
covering issues or pull requests alone (`public_repo` only reaches public repositories). A fine-grained PAT needs its
repository's "Issues" and "Pull requests" permissions set to read/write.

Two quirks worth knowing, because neither fails in an obvious way:

- **Use `GH_ENTERPRISE_TOKEN`, not `GH_TOKEN`.** `gh` reserves `GH_TOKEN` for github.com itself and ignores it for any
  other host, so setting it gets you a 401 that looks like a rejected token. `GITHUB_ENTERPRISE_TOKEN` works too.
- **`gh auth login` does not work against fogwall.** Set the environment variables above instead.

### Supported commands

| command                                               | permission needed                       |
| ----------------------------------------------------- | --------------------------------------- |
| `gh issue create/edit/close/comment`                  | `PROPOSE`                               |
| `gh pr create/edit/close/comment`                     | `PROPOSE`                               |
| `gh pr merge`                                         | `MERGE`                                 |
| `gh issue list/view`, `gh pr list/view`, etc. (reads) | none — if enabled by your administrator |

### Limitations

**`gh pr close --delete-branch`.** The close succeeds; deleting the branch does not. `gh` deletes a branch over GitHub's
REST API, and this surface carries GraphQL only. Delete the branch with `git push --delete`, which goes through
fogwall's git path as usual, or in the web UI.

## GitLab — `glab`

### Usage

Point `glab` at fogwall and run issue/MR commands as usual:

```shell
export GITLAB_HOST="<fogwall-gitlab-endpoint>"   # host:port or a dedicated hostname — ask your administrator
export GITLAB_TOKEN="<your PAT>"                 # your own token, `api` scope — see below
glab issue create -R <owner>/<repo> --title "..." --description "..."
glab mr create     -R <owner>/<repo> --source-branch <your-branch> --target-branch main --title "..." ...
```

### Authentication

Bring your own GitLab personal access token with the **`api` scope** — GitLab has no narrower scope covering issues or
merge requests alone.

**`glab mr create` needs a matching git remote.** It refuses to run unless one of the repository's remotes points at
whatever `GITLAB_HOST` is set to, so add one alongside your normal origin:

```shell
git remote add glab-proxy-do-not-use https://<fogwall-gitlab-endpoint>/<owner>/<repo>.git
```

<!-- prettier-ignore-start -->
> [!WARNING]
> This remote is not a working git remote. It exists only to satisfy `glab`'s check — the endpoint serves the GitLab API, not git, so `git push` or `git fetch` through it will fail. Keep using your normal remote for all git operations.
<!-- prettier-ignore-end -->

### Supported commands

| command                                                   | permission needed                       |
| --------------------------------------------------------- | --------------------------------------- |
| `glab issue create/update/note/close`                     | `PROPOSE`                               |
| `glab mr create/update/note/close`                        | `PROPOSE`                               |
| `glab mr merge`                                           | `MERGE`                                 |
| `glab issue list/view`, `glab mr list/view`, etc. (reads) | none — if enabled by your administrator |

## Gitea / Forgejo — `tea` and `fj`

Both CLIs share one endpoint, because they talk to the same API.

### Usage

```shell
# tea (Gitea)
tea login add --name fogwall --url https://<fogwall-gitea-endpoint> --token "<your token>"
tea issue create --login fogwall --repo <owner>/<repo> --title "..." --description "..."
tea pr create    --login fogwall --repo <owner>/<repo> --head <your-branch> --base main --title "..."

# fj (Forgejo)
fj -H https://<fogwall-gitea-endpoint> issue create "..." --body "..."
fj -H https://<fogwall-gitea-endpoint> pr create    "..." --body "..."
```

### Authentication

Bring your own Gitea/Forgejo access token, scoped to at least **`write:issue` and `write:repository`** — pull requests
are created under the repository scope, and issue and PR comments/edits under the issue scope (Forgejo models a pull
request as an issue for several operations, so both scopes are needed even if you only intend to open PRs).

**`tea pr close` and `tea pr edit` are the same request on the wire.** `tea` sends a full object with `"state":"closed"`
alongside every other field, so fogwall permits or denies them together. It cannot tell them apart, and doesn't pretend
to.

### Supported commands

| command                                      | permission needed                       |
| -------------------------------------------- | --------------------------------------- |
| issue create/edit/close/comment (`tea`/`fj`) | `PROPOSE`                               |
| PR create/edit/close/comment (`tea`/`fj`)    | `PROPOSE`                               |
| `tea pr merge`/`fj pr merge`                 | `MERGE`                                 |
| reads (`tea issue list`, `fj pr list`, etc.) | none — if enabled by your administrator |

### Limitations

**`tea issue edit --remove-labels`.** Silently does nothing. `tea` resolves the labels and then sends no request at all,
so there is nothing for fogwall to forward or refuse — it fails the same way without a proxy in the path.
