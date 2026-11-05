# Audit evidence

fogwall records each decision it makes and each change to who may do what. An auditor or an admin can query those
records and take them out of the system as an export. Every endpoint here requires `ROLE_AUDITOR` or `ROLE_ADMIN`.

## Dashboard

The Audit group in the sidebar has four pages:

- **User activity**: pushes, SCM API actions and fetch activity.
- **Access changes**: permission, membership and role changes.
- **Current access**: the current grants and group membership.
- **Export**: the chosen record types in one zip, bounded by one time window, with each type's columns chosen
  separately.

Each record type is a tab with its filters. On the history pages, a filter naming a user, group or provider suggests the
ones that exist and still accepts any name, so a deleted user stays findable; on Current access, it offers only the ones
that exist. **Export these records** downloads what the filters select, as CSV with the columns chosen or as JSON Lines.

## Record types

| Type                 | What one record is                                                         | Listing                             |
| -------------------- | -------------------------------------------------------------------------- | ----------------------------------- |
| `pushes`             | A push and its validation steps, commits and approval                      | `GET /api/push`                     |
| `scm-api-actions`    | A pull/merge request or issue operation through the SCM API proxy          | `GET /api/scm-api-actions`          |
| `fetch-activity`     | One hour's clone and fetch count for one repository, transport and outcome | `GET /api/fetches`                  |
| `permission-changes` | A grant or revocation of a user or group repository permission             | `GET /api/audit/permission-changes` |
| `membership-changes` | A user added to or removed from a permission group                         | `GET /api/audit/membership-changes` |
| `role-changes`       | A change to a user's roles                                                 | `GET /api/audit/role-changes`       |
| `grants`             | Current state: one holder's grant, directly or through a group             | `GET /api/audit/grants`             |
| `group-members`      | Current state: one member of one group                                     | `GET /api/audit/group-members`      |

Permission, membership and role changes record who made the change, whose access it was, and where it came from:
`DASHBOARD`, `API` (the API key), `CONFIG` (a live reload) or `IDP` (roles an identity provider asserted at sign-in). A
`CONFIG` change carries the revision it was loaded from: the commit for a git source, `sha256:` and the file's hash for
a file source. Configuration applied at startup is not recorded.

Deleting a group records the revocation of each of its rules and the removal of each member. Deleting a user records the
revocation of each grant, the removal from each group, and the loss of their roles. An identity provider's roles are
compared with the stored ones at each sign-in; a difference is recorded against that provider, and a sign-in that
changes nothing records nothing.

Current grants carry `grantedBy` and `grantedAt`. A grant loaded from configuration has a time and no granter; a grant
that predates the record has neither.

## Filters

The same filter means the same thing on every listing:

| Filter          | Meaning                                                                                                                           |
| --------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| `provider`      | The provider the record belongs to                                                                                                |
| `owner`, `repo` | The repository. On permission changes and grants, given together, they select the grants whose pattern applies to that repository |
| `user`          | Who acted: the pusher, the SCM API caller, or who made an access change                                                           |
| `subject`       | On access changes and grants: whose access it is                                                                                  |
| `from`, `to`    | Time window, ISO-8601; `from` inclusive, `to` exclusive                                                                           |
| `search`        | Free text over the record's names                                                                                                 |

Each type adds its own status or result filter. The older names `project`, `repoOwner` and `repoName` are still
accepted. A filter a type cannot answer — `user` on fetch activity, `repo` on role changes — is a 400, as is an unknown
enum value, a malformed timestamp, or a `limit` outside 1–1000.

## Paging

Listings page with `limit` and `offset`, or by cursor. A full page carries an `X-Next-Cursor` response header; passing
it back as `after` reads the next page. Records are ordered by time, then id, so a walk by cursor reads every record
once. This is the pull API for a log aggregator or SIEM: fogwall pushes nothing anywhere. The current grants and group
membership are a snapshot ordered by holder or group, paged with `limit` and `offset`; `X-Total-Count` gives the number
of rows the filters select.

```bash
curl -H "X-Api-Key: $KEY" -D headers.txt "$FOGWALL/api/audit/permission-changes?newestFirst=false&limit=500"
curl -H "X-Api-Key: $KEY" "$FOGWALL/api/audit/permission-changes?newestFirst=false&limit=500&after=$CURSOR"
```

## Export

Each listing has an export at its path plus `/export`, taking the same filters: `GET /api/push/export`,
`GET /api/audit/role-changes/export`, and so on. The current grants and group membership export from
`GET /api/audit/grants/export` and `GET /api/audit/group-members/export`. `GET /api/audit/export?types=…&from=…&to=…`
exports several types at once; with no `types`, it exports all of them. There, `<type>.columns` chooses one type's CSV
columns, for example `role-changes.columns=username,roles`. `GET /api/audit/record-types` lists every type with its
columns in their default order.

An export is a zip:

```
role-changes/part-00001.csv
role-changes/part-00002.csv
manifest.json
```

- Each type is a folder of parts of at most 100,000 rows.
- `manifest.json` names the filters, the generation time, and each part's row count and SHA-256. It is written last: a
  zip without one is incomplete.
- `format=csv` (the default) writes one row per record with a header in each part. `columns` chooses and orders the
  columns. A nested value — a push's steps, commits and attestation — is its JSON text in one column.
- `format=json` writes JSON Lines: one full record per line, nesting kept.
- In CSV, a cell beginning with `=`, `+`, `-`, `@`, tab or carriage return is prefixed with `'`, so a spreadsheet reads
  it as text. JSON keeps every value exactly.

The export streams from the database to the response; nothing is stored on the server and there is no row limit.
