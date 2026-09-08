# SSH remotes

If your administrator has configured the proxy with an SSH provider, you can push over SSH instead of HTTPS. SSH pushes
do not use a PAT — your identity is tied to your SSH key instead.

## Setting up SSH

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

## SSH identity verification

SSH pushes are subject to the same compliance guarantee as HTTP pushes. The proxy:

1. Verifies your SSH key against the fogwall user database (MINA public-key auth).
2. Calls the upstream SCM API to fetch the SSH public keys registered on your linked SCM identity.
3. Checks that the connecting key's SHA-256 fingerprint appears in that list.

If step 3 fails — for example because you have a key registered in fogwall but not on your SCM account — the push is
blocked. Add the key to your SCM account and retry. If the provider or SCM identity is misconfigured, contact your
administrator.

There is no token to supply for SSH pushes — no `Authorization` header, no credential in the URL. The agent-forwarded
key is the only credential.
