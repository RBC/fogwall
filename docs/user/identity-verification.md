# Identity verification

The proxy confirms that the person pushing is who they say they are. The mechanism differs by transport.

## HTTP pushes

1. **Token → SCM username**: your PAT is used to call the SCM API (`GET /user`). The returned username must match the
   SCM identity registered in your proxy user profile. This check is **always enforced** — a push is blocked immediately
   if your token cannot be matched to a registered proxy user, regardless of any other settings.
2. **Commit emails → proxy user**: every author and committer email in the pushed commits must match an email address
   registered on your proxy account. This check is controlled by `attribution-policy` — in `warn` mode mismatches are
   logged but the push proceeds; in `strict` mode the push is blocked.

**The HTTP Basic-auth username in your remote URL is not used for identity.** Use any value — `me`, `git`, your name —
it makes no difference. Only the password (your PAT) matters.

You can add and remove your own SCM identities and email addresses from your profile page in the dashboard. If your push
is blocked with "Identity Not Linked" or a commit email mismatch, log in to the dashboard and add the missing identity
or email under your profile before pushing again.

If you cannot resolve it yourself — for example, because the email address or SCM username is already registered to
another user — contact an administrator. Duplicate identity conflicts (two users claiming the same email or SCM handle)
require admin intervention to resolve.

## SSH pushes

SSH identity verification enforces the same compliance guarantee, but via SSH key fingerprint rather than a PAT:

1. **Public-key auth (connection gate):** your SSH key must be registered in your proxy profile.
2. **SCM fingerprint check (compliance gate):** the proxy calls the upstream SCM API and fetches the SSH public keys
   registered on your linked SCM identity. Your connecting key's fingerprint must appear in that list.

Both steps are required and both are always enforced — there is no warn-only mode for SSH identity. If either step fails
the push is blocked.

**Your SSH key must be registered in two places:** your fogwall profile, and your upstream SCM account. Registering it
in fogwall alone is not enough — the proxy cross-checks against the SCM to confirm the key actually belongs to you.

If you are blocked with "SSH key not linked to any SCM identity", add the key to your SCM account settings and retry.

## Linking your account via OAuth

If your administrator has configured it, your profile page's SCM Identities tab has a **"Link with `<hostname>`"**
button for each supported provider instance (GitHub, GitLab, or a Forgejo/Gitea/Codeberg instance) — the hostname
identifies which specific account you're about to link, since a GitHub- or GitLab-type provider isn't always
github.com/gitlab.com (it may be a GitHub Enterprise tenant or a self-hosted GitLab/Forgejo/Gitea instance). This is the
preferred way to register an SCM identity — instead of typing your SCM username into a free-text field, you authorize
fogwall through the provider's real OAuth login, and fogwall sets a **verified** badge on the resulting identity once
it's confirmed you actually control that account.

Some deployments require a verified identity for push authorization — if your push is blocked with "SCM Identity Not
Verified," link your account this way rather than adding a free-text identity.

Linking also saves you some manual setup:

- **Emails your provider has verified are imported automatically** and locked onto your account (shown as
  `locked (github)` or `locked (gitlab)` on the Emails tab) — including a GitHub noreply address, if you use one. You no
  longer need to add these yourself.
- **Your registered SSH public keys are imported automatically** too, shown with the same locked badge on the SSH Keys
  tab.

A verified identity (and any keys/emails it locked in) can't be removed the normal way — click **"Unlink"** instead,
which removes the identity, the stored OAuth token, and any SSH keys/emails that came from that provider in one step. If
the same key or email is also verified by another linked provider, it stays registered under that provider instead of
being deleted outright. You can re-link at any time.

## If an SSH push is refused

Some deployments identify an SSH push only by keys that came in when you linked your account, so a key you pasted in
yourself is not enough:

```text
⛔️  Push Blocked - SSH Key Not Linked
❌️  This SSH key is not linked to a verified account.
```

Link the account for that provider and push again. If it is already linked and you have registered a new key with your
SCM since, unlink and re-link to import your current keys. Removing a key from your SCM account stops it working here
too. If it still fails, ask your administrator.
