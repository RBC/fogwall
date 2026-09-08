# Setting up your remote

> **Fastest path — the in-app setup page.** Your fogwall deployment serves a **Setup** page in the dashboard (the help /
> quick-start icon in the top bar, reachable without logging in) that generates copy-pasteable git config for _this_
> deployment, with the real hostnames already filled in. By default it reroutes only your **pushes** to fogwall (via
> git's `pushInsteadOf`) and leaves your clones and fetches going straight to the upstream — so read-only access is
> unaffected and you don't need it at all if you only clone or fetch. It offers both a one-paste global form and an
> explicit per-repository form. The manual per-remote steps below are the same thing done by hand.

The proxy URL is structured as:

```text
http[s]://<proxy-host>/<mode>/<provider-host>/<owner>/<repo>.git
```

For example, if you normally push to `https://github.com/myorg/myrepo`, the proxy remote is:

```text
https://fogwall.corp.example.com/server/github.com/myorg/myrepo
```

> The `/server/` prefix was previously `/push/` (when this mode was called _store-and-forward_). Remotes using `/push/`
> still work — it is a deprecated alias — but new remotes should use `/server/`.

Add it as a new remote (recommended — keeps your direct-to-GitHub remote as a fallback):

```shell
git remote add proxy https://fogwall.corp.example.com/server/github.com/myorg/myrepo
```

Then push via the proxy:

```shell
git push proxy main
```

## Credentials in the remote URL

The git push path (`/server/` and `/proxy/`) uses HTTP Basic authentication — this is what the git protocol requires,
and it matches what the upstream SCM expects. Your upstream PAT is the password; the username can be any non-empty
string — `me`, `git`, your name — it is not used for identity resolution (see
[Identity verification](identity-verification.md) below). It must not be empty or the upstream SCM will reject the
request. The exception is Bitbucket — see below.

This is separate from the dashboard: the dashboard login uses your proxy user account (via your org's IdP or local
credentials), not your SCM token. The two credential sets are independent — one is for `git push`, the other is for the
web UI.

Embed credentials directly in the URL if your git credential helper does not pick them up automatically:

```shell
git remote add proxy https://me:ghp_yourtoken@fogwall.corp.example.com/server/github.com/myorg/myrepo
```

Or use `git credential store` / your OS keychain as you normally would.

**Fetching from a public repository needs no credentials.** When you clone or pull through the proxy, fogwall asks the
upstream SCM whether that repository serves anonymous reads. If it does, your request goes through without a credential
prompt. If it doesn't — a private repository — you get the usual 401 challenge and your git client supplies the token,
which fogwall forwards upstream.

Pushing always requires credentials, whatever the repository's visibility, because fogwall forwards the push upstream
using your own token.

<!-- prettier-ignore-start -->
> [!TIP]
> Most credential helpers (macOS Keychain, Windows Credential Manager, `git-credential-store`) pin credentials to a hostname. git authenticates to the proxy host, not the upstream — so **if you have previously authenticated directly to the upstream (e.g. `github.com`), that credential won't be reused for the proxy**; git looks for one stored under `fogwall.corp.example.com` instead. Either let git prompt on the first push and your helper store it under the proxy host, store a separate entry for the proxy host yourself, or embed the token in the remote URL as shown above. For local development environments that are frequently recreated, embedding the token in the URL is simpler than managing keychain entries.
<!-- prettier-ignore-end -->

<!-- prettier-ignore-start -->
> [!TIP]
> **Pushing to more than one provider through the same proxy?** The proxy serves every provider under one hostname, differing only by URL path (`/server/github.com/…` vs `/server/codeberg.org/…`), but credential helpers key on hostname alone — so one stored credential would be reused for all of them. Run `git config --global credential.https://fogwall.corp.example.com.useHttpPath true` to key credentials on the full URL (host + path) instead, so each provider gets its own entry.
<!-- prettier-ignore-end -->

<!-- prettier-ignore-start -->
> [!WARNING]
> **Bitbucket only:** the username in the remote URL must be your Bitbucket account email address (e.g. `you@company.com`). This is required for identity resolution — see the [Configuration Reference](../configuration/providers.md#bitbucket-identity-resolution) for details.
<!-- prettier-ignore-end -->

## Required token scopes

The proxy calls the SCM API to resolve your identity. Your PAT needs at least:

| Provider         | Minimum scope                                                          |
| ---------------- | ---------------------------------------------------------------------- |
| GitHub           | No additional scopes required (classic or fine-grained PATs both work) |
| GitLab           | `read_user`                                                            |
| Bitbucket        | `read:user:bitbucket` and `write:repository:bitbucket`                 |
| Codeberg / Gitea | `read:user`                                                            |
