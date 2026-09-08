# What a successful push looks like

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
To http://fogwall.corp.example.com/server/github.com/myorg/myrepo.git
 * [new branch]      my-feature -> my-feature
```

Each `remote:` line is a validation step streaming in real time. The example above shows `ui` approval mode — a reviewer
approved in the dashboard before the push was forwarded. In `auto` mode the `✅ Push approved by reviewer` line is
replaced by immediate forwarding with no wait.
