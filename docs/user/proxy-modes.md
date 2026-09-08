# Choosing a proxy mode: `/server/` vs `/proxy/`

There are two URL prefixes, each with different behaviour:

|                       | `/server/` (server mode)                                                                          | `/proxy/` (transparent proxy)                                                                                                        |
| --------------------- | ------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| **How it works**      | The proxy receives your push locally, validates it, then forwards to upstream                     | The proxy forwards HTTP requests directly to upstream while inspecting them inline                                                   |
| **Terminal feedback** | Live streaming — each validation step prints as it runs                                           | Silent until the end — one response after all checks complete                                                                        |
| **Approval workflow** | Push stays open waiting for approval; same `git push` command completes once approved             | Push is blocked and you must run `git push` again after a reviewer approves — the second push is matched to the existing push record |
| **Push record**       | Every push is persisted with a full event history                                                 | Every push is persisted; the re-push after approval references the same record                                                       |
| **Local disk usage**  | Clones each repo to ephemeral pod storage for diff inspection — proportional to repo history size | None — git bytes stream directly through the proxy with no local storage                                                             |
| **Recommendation**    | **Use this for most workflows**                                                                   | Use when network reliability or disk constraints are a concern                                                                       |

For day-to-day use, `/server/` gives a better experience: you see each validation step in real time and the same
`git push` command completes once approved.

Prefer `/proxy/` if your network infrastructure is flaky or connections between client → proxy → upstream are
unreliable. Server mode keeps the client connection open for the full validation and approval cycle — a dropped
connection means starting over. Transparent proxy completes each HTTP request atomically, so a network hiccup during
approval does not lose the push record.

## Disk usage in server mode

In server mode, the proxy maintains local mirrors of each upstream repository on ephemeral pod storage (`emptyDir` in
Kubernetes/OpenShift). A full clone is kept for the serve path (so clients can fetch through the proxy) and a shallow
clone (depth 100) for diff inspection. These are rebuilt automatically on pod restart — there is no durable state in the
cache.

**Large repositories** (deep history, large binaries, monorepos) can consume significant disk on the proxy pod.
Operators should set an `emptyDir.sizeLimit` in the pod spec to prevent runaway clones from exhausting node disk:

```yaml
volumes:
  - name: tmp
    emptyDir:
      sizeLimit: 5Gi
```

If disk pressure becomes an issue for a specific large repo, route it through `/proxy/` instead — transparent proxy mode
uses zero local disk and shifts the concern purely to network reliability between the proxy and upstream. A transient
network failure during a push just means the developer retries; the push record is preserved.
