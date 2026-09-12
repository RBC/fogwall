# User permissions vs access rules

Every request passes two independent layers: a site-wide gate your administrator configures, and a permission granted to
you. Both must say yes, and both deny by default — nothing is open because it was never mentioned.

The two surfaces have their own pair:

|                     | git push and fetch                           | contributions (PR/MR and issue operations) |
| ------------------- | -------------------------------------------- | ------------------------------------------ |
| **site-wide gate**  | `rules.allow` / `rules.deny`, per repository | none — the surface is on or off            |
| **your permission** | `PUSH` grant on that repository              | `PROPOSE` grant on that repository         |

**Access rules** decide which repositories the proxy will handle at all. A repository that is not allowed is rejected
immediately, before any user-level check runs. They exist because a fetch of a public repository sends no credential —
there is no user to check, so the URL is the only thing to gate on. Every contribution request is authenticated, so it
is checked against your permissions directly.

**Your permissions** decide what you personally may do. `PUSH` and `PROPOSE` are separate grants — pushing code to a
fork and opening a pull request against the upstream are different operations on different repositories, so holding one
does not imply the other. A contribution is always authorized against the repository it is opened on, never the fork the
branch came from.

Read commands (`gh issue list`, `glab mr view`) are not checked against your `PROPOSE` grants at all; only the
provider-level rule applies to them.

The error message tells you which layer rejected the request — see [When a push is blocked](blocked-pushes.md) for the
push-path messages and what to do for each.
