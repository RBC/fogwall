# Developer onboarding — the Setup page

The dashboard serves a **Setup** page (reached from the help / quick-start icon in the top bar) that generates
deployment-specific git config for developers, with this deployment's real hostnames filled in. It is generated from the
running configuration (providers, service URL, SSH listener), so it cannot drift from what fogwall actually serves.

- **Push-only by default.** The generated config reroutes only developers' _pushes_ to fogwall (git `pushInsteadOf`);
  clones and fetches keep going straight to the upstream. This keeps read-only access unaffected and avoids a read-time
  dependency on fogwall — reads are typically already inspected elsewhere. Routing fetches through fogwall is offered as
  an explicit opt-in, and the page tells developers who only clone/fetch that they need nothing at all.
- **Global vs per-repo.** The page offers both a one-paste global `~/.gitconfig` form (applies to every repo under the
  upstream host, gated by your URL rules) and an explicit per-repository form (`git remote set-url --push`, visible in
  `git remote -v`), noting the global form's blast radius.
- **It is public** (served at `/api/setup`, no login) — a developer who cannot yet log in is exactly who needs setup
  instructions, and fogwall is often deployed where the GitHub-hosted docs are blocked. It exposes only routing
  information already implied by the provider list; no secrets.
- **Set [`server.service-url`](../configuration/server.md)** so the generated URLs are correct. When it is unset, the
  page derives the base URL from the developer's own browser address, which is wrong behind a reverse proxy — the page
  shows a warning in that case. Setting `service-url` is the fix.
