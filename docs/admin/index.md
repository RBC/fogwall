# Administrator and Operator Guide

This guide covers deploying, configuring, and operating fogwall. It is written for the person responsible for running
the proxy — setting up user accounts, configuring providers and rules, diagnosing problems, and keeping the service
healthy.

For the YAML configuration reference, see [Configuration Reference](../configuration/index.md). For developers pushing
through the proxy, see [User Guide](../user/index.md).

## Contents

- [Conceptual model: three independent layers](concepts.md) — how access rules, permissions and approval fit together
- [Developer onboarding — the Setup page](onboarding.md) — what a new developer sees on their first visit
- [User accounts](user-accounts.md) — provisioning, roles, emails and SCM identities
- [Repo permissions](repo-permissions.md) — who may push, approve, self-certify or propose where
- [Access rules](access-rules.md) — which repositories fogwall will proxy at all
- [Approval mode](approval-mode.md) — auto-approve versus human review
- [Logging](logging.md) — log locations, debug profiles, and reading a failed push
- [JGit filesystem requirements](filesystem-requirements.md) — home directory, `/tmp`, and gitleaks permissions
- [Externalized configuration](externalized-config.md) — mounting config into a container and overriding it
- [Network requirements](network-requirements.md) — outbound connections, corporate proxies, reverse proxies, sizing
- [Production checklist](production-checklist.md) — what to settle before the first real push
- [SSH transport](ssh-transport.md) — exposing SSH, host keys, identity verification and agent forwarding
- [SCM OAuth account linking](scm-oauth.md) — registering OAuth apps and operating strict identity mode
- [SCM API](scm-api.md) — enabling the PR/MR path, its token model and authorization
- [Common operational problems](troubleshooting.md) — symptoms an operator sees, and what causes them
