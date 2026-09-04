# UI regression fixtures — capture

The Playwright suite in `fogwall-dashboard/frontend/tests` boots the dashboard against a **real, pre-populated H2
database** rather than seeding data through the app. That database is produced here, from real pushes through real
providers, and committed as a plain SQL dump.

| File                                               | What it is                                                                                      |
| -------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| `capture.py`                                       | The one-button producer (system Python 3, stdlib only). Linear; the script is the fixture spec. |
| `mapping.env.example`                              | Copy to `mapping.env` (gitignored): your handle, name, emails, SSH key, PAT file paths.         |
| `secrets.env.example`                              | Copy to `secrets.env` (gitignored): OAuth app client ids/secret paths for account linking.      |
| `scrub.sql`                                        | Rows deleted before the dump: OAuth tokens, identity/fingerprint caches, sessions.              |
| `../../fogwall-dashboard/frontend/tests/fixtures/` | Output: `fogwall.sql` (the database), `manifest.json` (scenario → push id), the profile.        |

## Run it

```bash
cp test/capture/mapping.env.example test/capture/mapping.env   # fill in
cp test/capture/secrets.env.example test/capture/secrets.env   # optional, for OAuth-verified badges
ssh-add ~/.ssh/id_ed25519                                       # the key registered on github.com (python3, git, jq needed)
python3 test/capture/capture.py
```

What happens, in order:

1. `fogwall-playwright.yml` placeholders (`fixture-dev`, `fixture-dev@example.com`, the placeholder SSH key, …) are
   rewritten to your real values into a temp profile. The committed profile never changes.
2. A private repo named `fogwall-fixture-<random>` is created on GitHub, GitLab, Codeberg and gitea.com with your PATs.
3. The dashboard boots on an H2 **file** database with that profile.
4. A couple of DB-sourced profile rows are seeded through the API (a `reviewer` identity and email), then the script
   pauses: log in as `dev` and link **GitHub and GitLab** via OAuth. Not Codeberg (the unmapped-identity scenario needs
   it unlinked); Gitea stays config-declared as the locked example.
5. Every scenario runs: rejections one validator at a time, trailer-policy rejections, a multi-failure push, an unmapped
   identity, pending branch/tag/multi-provider pushes — all through the transparent proxy — plus one server-mode push
   over SSH that is held open for review.
6. Second pause: a checklist of pushes to approve, reject, or cancel in the dashboard as `reviewer` or `dev`. The script
   watches their status, continues once none is pending, and re-pushes the approved ones so they end up FORWARDED.
7. The app stops; `scrub.sql` removes secret/session/cache rows; the database is dumped to SQL; every real value is
   replaced by its placeholder and any email outside the fixture domains becomes `fixture-extra-N@example.com`; the dump
   is checked for every real value and every PAT; the ephemeral repos are deleted.
8. `fogwall.sql` and `manifest.json` land in the fixtures directory. Review the diff, commit.

Knobs: `KEEP_WORK=1` keeps the temp directory (logs, clones, unscrubbed dump) for inspection; `SKIP_OAUTH=1` skips the
OAuth pause; `PUSH_TIMEOUT=<s>` caps how long a server-mode push may be held open.

## Replay (what CI does)

```bash
cd fogwall-dashboard/frontend && npx playwright test
```

The Playwright web server command runs `:fogwall-dashboard:restoreFixtureDb` (fresh H2 file from `fogwall.sql`) and then
`:fogwall-dashboard:run` with `FOGWALL_CONFIG_PROFILES=playwright` and the fixtures directory on the classpath. The
migrator applies only migrations newer than the capture, so a dump does not go stale with every schema change. No
credentials and no network are involved on replay: the provider hosts in the profile are real names but nothing contacts
them.

## When to re-capture

- A hook or filter changes what it records (step names, content JSON, messages).
- A scenario is added or changed in `capture.py`.
- The profile changes in a way that affects push outcomes (policy, users, permissions).

Config-page changes (rules, providers, groups) only need the profile edited; the dump is unaffected.

## Why real data and not a seeder

A hand-written seeder encodes what we _believe_ the hooks write and drifts silently. The dump is what they actually
wrote. Re-capturing after a hook change produces a reviewable diff of inserted rows in the PR.
