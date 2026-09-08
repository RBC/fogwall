# Tips

## Clone through the proxy from the start

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

## Managing multiple remotes

If you already have a local clone pointed directly at the upstream, add the proxy as a named remote or redirect pushes
through it while keeping direct fetch:

```shell
git remote set-url --push origin https://fogwall.corp.example.com/server/github.com/myorg/myrepo
```

## Private forks and internal mirrors

If your org maintains a private internal fork of a public repo (e.g. a patched version of an upstream library), both can
be proxied independently. A common three-remote setup:

```shell
# upstream — the public project (fetch only, direct)
git remote add upstream https://github.com/someproject/somerepo

# origin — your org's internal fork (all traffic through proxy)
git remote add origin https://fogwall.corp.example.com/server/github.corp.example.com/myorg/somerepo

# The proxy URL reflects whichever provider hosts the fork —
# it does not have to be the same provider as upstream.
```

Each remote is a separate entry in the proxy's access rules and permission grants. Coordinate with your administrator to
ensure both the public upstream and internal fork URLs are configured.

## Finding proxy URLs from the dashboard

The **Repositories** page in the dashboard lists every repo that has seen activity through the proxy. Each entry has a
**Clone via proxy** button that copies the ready-to-use `git clone` command to your clipboard — useful when setting up a
new local clone or adding a proxy remote to an existing one.

The Clone button uses the `/proxy/` mode URL. Swap `/proxy/` for `/server/` if you want the server mode path instead.

When the SSH listener is enabled and the provider serves SSH (see
[Configuration Reference](../configuration/ssh-transport.md#serving-a-provider-over-ssh)), the Clone button also offers
an **HTTPS / SSH** toggle — pick **SSH** to copy the `ssh://…` form instead.

> The repository only appears in the list after it has been pushed to or fetched through the proxy at least once. If you
> do not see it yet, push or fetch first.

## Scrubbing a commit history before pushing

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
