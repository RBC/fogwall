# Groups

_Available since v1.3.0._

Named groups of users that share a common set of permission grants — an alternative to repeating the same `permissions:`
entry for every member individually. A user assigned to a group inherits all of the group's grants in addition to any
directly-assigned permissions.

```yaml
groups:
  - name: team-alpha
    description: "Alpha team push access"
    members:
      - alice
      - bob
    grants:
      - provider: github
        match:
          target: SLUG
          value: /myorg/**
          type: GLOB
        grant: PUSH
```

## Group properties

| Property            | Type   | Default | Description                                                                                                             |
| ------------------- | ------ | ------- | ----------------------------------------------------------------------------------------------------------------------- |
| `name`              | string | —       | Group name, shown in the dashboard                                                                                      |
| `description`       | string | `""`    | Free-text description                                                                                                   |
| `members`           | list   | `[]`    | Usernames belonging to this group (must match a `users:` entry or a DB user)                                            |
| `grants`            | list   | `[]`    | Permission grants applied to every member — same shape as `permissions:` entries, minus `username`                      |
| `grants[].provider` | string | —       | Provider name as defined in `providers:` config                                                                         |
| `grants[].match`    | object | —       | Repository match criteria — same semantics as [Permissions](permissions.md)                                             |
| `grants[].grant`    | enum   | `PUSH`  | `PUSH`, `REVIEW`, `PUSH_AND_REVIEW`, `SELF_CERTIFY`, `ISSUE`, `PROPOSE`, or `MERGE` — see [Grant](permissions.md#grant) |

<!-- prettier-ignore-start -->
> [!NOTE]
> Groups defined here are CONFIG-sourced and read-only from the dashboard — editing or deleting a config-sourced group
> via the UI/REST API is rejected. Groups created through the dashboard instead are DB-sourced and fully editable there.
> This mirrors how CONFIG-sourced `permissions:`/`rules:` entries behave.
<!-- prettier-ignore-end -->
