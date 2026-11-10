# Participation

**Audit → Participation** shows who contributed through fogwall and where: pushes, and pull/merge requests and issues
opened, edited, commented on and merged through the [SCM API](scm-api.md) or the dashboard's issue form. Auditors and
admins see it. Everyone sees their own figures by clicking their name in the sidebar, and admins see a user's on that
user's page.

## What it shows

- Totals for the period: pushes by status, distinct contributors, pull/merge requests and issues opened with the state
  fogwall last saw them in, edits, comments, and requests refused by policy or for their content.
- The same figures over time, by day for a window up to 45 days, by week up to a year, and by month beyond. Periods are
  in UTC.
- A row per provider and per repository, each with its own contributor count.

Narrow it to a person, a provider or a repository. Choosing a person shows that person's figures. A figure in the
provider or repository table opens **Audit → Decisions** on the records behind it, with the same window, repository and
person.

Every signed-in user sees a summary on **Overview**: their own figures and everyone's for the last 30 days, with the
five most active repositories. The summary names no person.

On **Repos**, a repository's push count opens its pushes over the last 90 days by period and status.

## How it counts

- A contributor is a person with a push in any state, or a pull/merge request or issue action that fogwall forwarded.
- Each person's edits to one pull/merge request or issue count once in a period, however many requests the client sent.
  Comments and merges count the same way.
- Opened counts creates forwarded through fogwall. A pull request opened elsewhere and edited through fogwall counts as
  an edit, not as opened.
- Merged, closed and open are the state fogwall last saw. A pull request merged or closed on the provider directly keeps
  the state of fogwall's last request for it. **Unknown** counts creates whose response named nothing fogwall could
  follow.

When no provider has `scm-api.enabled` or `issues-enabled`, only pushes are shown and no SCM API record is read.

## API

| Endpoint                             | Who                     | Returns                                                        |
| ------------------------------------ | ----------------------- | -------------------------------------------------------------- |
| `GET /api/activity/me`               | any signed-in user      | the caller's activity                                          |
| `GET /api/activity/users/{username}` | `ROLE_AUDITOR`, `ADMIN` | that user's activity                                           |
| `GET /api/activity`                  | `ROLE_AUDITOR`, `ADMIN` | everyone's, with per-provider rows and contributor counts      |
| `GET /api/activity/summary`          | any signed-in user      | everyone's, unfiltered, with the five most active repositories |
| `GET /api/activity/repo-pushes`      | any signed-in user      | one repository's pushes by period and status                   |

Each takes `from` (inclusive) and `to` (exclusive) as ISO-8601; `to` defaults to now and `from` to 90 days before it,
and a window spans at most 3660 days. `provider`, `owner` and `repo` narrow it to exact matches; `repo-pushes` requires
all three.
