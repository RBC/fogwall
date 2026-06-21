# Security Policy

## Supported Versions

Security fixes are applied to the latest release only. Upgrade to receive fixes.

| Version | Supported        |
| ------- | ---------------- |
| 1.x     | ✅ (latest only) |
| < 1.0.0 | ❌               |

## Reporting a Vulnerability

Please **do not** open a public GitHub issue for security vulnerabilities.

Use GitHub's private vulnerability reporting instead: 👉 https://github.com/RBC/fogwall/security/advisories/new

Include as much detail as you can: affected component, steps to reproduce, and potential impact. We'll acknowledge the
report within a few business days and keep you updated on the fix timeline.

## Scope

In scope:

- Authentication and authorisation bypasses
- Remote code execution via the proxy or filter chain
- Credential or secret exposure through logs, API responses, or the push store
- Injection vulnerabilities in git path handling or SCM API calls

Out of scope:

- Vulnerabilities in the git client binary or JGit library itself — report those to the respective upstream projects
- Vulnerabilities in upstream dependencies (we track these via Grype in CI)
- Issues requiring physical or administrative access to the host
- Social engineering
