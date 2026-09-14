# Throwaway SSH key for the compose SSH smoke test

`compose_ed25519` / `.pub` are a dedicated, disposable ed25519 pair used **only** by the SSH leg of the compose smoke
suite (`SshProxyModeComposeTest`) against the local Docker Compose Gitea. They authenticate a `git push` over fogwall's
SSH transport to `localhost`; they have no access to anything real and are safe to regenerate.

- The public key is registered in the compose Gitea for `test-user` by `docker/gitea-seed.sh`, and declared as
  `test-user`'s SSH key in `docker/fogwall-docker-default.yml`, so fogwall authenticates the connection and resolves it
  to `test-user`.
- The private key is committed so the test can push without generating one; the SSH server it reaches only exists while
  `bash compose.sh --ssh -- up -d` is running.
