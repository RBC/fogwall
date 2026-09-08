# fogwall documentation

fogwall is a Git push proxy: it sits between developers and upstream Git hosting providers, and runs every push through
a validation and approval pipeline before it reaches the upstream remote.

## Start here

- **[User Guide](user/index.md)** — you push code through fogwall. Setting up a remote, reading push output, and what to
  do when a push is blocked or waiting for approval.
- **[Administrator and Operator Guide](admin/index.md)** — you run fogwall. Accounts, permissions, deployment,
  networking, and diagnosing problems.
- **[Configuration Reference](configuration/index.md)** — every YAML key, what it does, and what it defaults to.
- **[Architecture](architecture/index.md)** — you are changing fogwall. Modules, proxy modes, request flow, and the core
  abstractions.
- **[Internals](internals/index.md)** — contributor working notes on git, JGit, and SCM API behaviour.

Build and test instructions live in [CONTRIBUTING.md](https://github.com/RBC/fogwall/blob/main/CONTRIBUTING.md).
