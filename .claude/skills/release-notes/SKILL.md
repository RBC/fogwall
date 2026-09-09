---
name: release-notes
description:
  Draft or refresh the release notes for a milestone, starting from GitHub's auto-generated changelog and folding the
  merged PRs into summarised sections with a breaking-changes/upgrade block. Use when asked to write, update or
  regenerate release notes for a version, or before an internal image tag bump. Never creates or pushes a git tag.
allowed-tools: Bash, Read, Write, Edit
---

## Hard rule: never create a tag

Tags on this repo are **immutable** — a mistaken tag cannot be deleted and re-pushed.

Never run, for any reason, while this skill is active:

- `git tag`, `git push --tags`, `git push origin <anything that looks like a version>`
- `gh release create` **without** `--draft`
- `gh release edit --draft=false`, `gh release edit --tag`, or anything that publishes
- the `release` or `release-tag` skills

A GitHub **draft** release does not create its tag — GitHub only creates the tag when the draft is published. So the
draft carries a deliberately unusable placeholder tag that would be obviously wrong if it ever were published:

```
<version>-draft-<UTC timestamp>      e.g. 1.4.0-draft-20260908T193000Z
```

Never use the real `vX.Y.Z` tag name on a draft. Publishing the release is a separate, human-only step done with the
real tag through the `release` / `release-tag` skills.

## Inputs

Ask only if it isn't obvious from the request:

- **version / milestone** — e.g. `1.4.0` (milestone titles are unprefixed; release tags are `vX.Y.Z`)
- **previous tag** — defaults to the newest published release (`gh release list --limit 1`)

## Step 1 — gather

Run these together; they are the whole factual basis for the notes.

```bash
V=1.4.0                     # the milestone / version being drafted
PREV=$(gh release list --exclude-drafts --limit 1 --json tagName --jq '.[0].tagName')

# GitHub's own auto-generated changelog — the starting point, NOT the output.
# This is a read-only POST. It does not create a tag or a release.
gh api repos/:owner/:repo/releases/generate-notes \
  -f tag_name="$V-draft-probe" -f previous_tag_name="$PREV" -f target_commitish=main --jq .body

# What the milestone says the release is about, and what is still open.
gh issue list --milestone "$V" --state all --limit 100 \
  --json number,title,state,labels --jq '.[] | "\(.state) #\(.number) \(.title) [\([.labels[].name]|join(","))]"'
```

Then read the bodies of the PRs that look like features, security fixes, or config changes — the auto-gen title alone is
never enough to write a breaking-change note:

```bash
for n in <numbers>; do echo "=== #$n ==="; gh pr view $n --json title,body --jq '"\(.title)\n\(.body)"'; done
```

The PR bodies in this repo are unusually detailed and often state the compatibility impact outright — look for
`Breaking change`, `Behaviour change`, `Compatibility`, `deprecated alias`, `migration V<n>`, `default … → …`, and
`Operators upgrading`.

Also check for anything the auto-gen changelog cannot see:

- **Schema migrations** — `git log --oneline "$PREV"..main -- '*/migration/*' '*/db/migration/*'`, and the
  `DatabaseMigrator` registry. Every new `V<n>` belongs in the upgrade section.
- **Config keys** — `git diff "$PREV"..main -- docs/configuration/ | grep -E '^[-+].*`[a-z].*`'` catches renamed,
  defaulted and deprecated keys.
- **Open milestone issues** — if the milestone still has open items, the notes are a working draft; say so in the chat,
  not in the release body.

## Step 2 — write

Write the body to the scratchpad (never into the repo — release notes live on GitHub, not in a tracked file).

### House style

Set by [v1.3.0](https://github.com/RBC/fogwall/releases/tag/v1.3.0) and
[v1.3.2](https://github.com/RBC/fogwall/releases/tag/v1.3.2) — match them. Earlier releases are raw auto-gen dumps; do
not imitate those.

- **One line per change, not one line per PR.** Related PRs collapse into a single bullet with their numbers grouped at
  the end: `(#379, #432, #451)`. This is what GitHub's own auto-gen achieves through linking — the PR is where the
  detail lives, so the bullet does not restate it.
- **Never hard-wrap the body.** GitHub renders a single newline in a release body as a `<br>`, so a bullet wrapped at
  120 columns comes out ragged. Write each bullet and each paragraph on one physical line, however long, with blank
  lines between them. The repo's `proseWrap: always` governs Markdown files in the tree, not text posted through the
  API. Verify before handing over the link — the count must be `0`:

  ```bash
  gh api repos/:owner/:repo/releases/tags/"$TAG" -H "Accept: application/vnd.github.html+json" \
    --jq .body_html | grep -c '<br'
  ```

- **Bold lead-in, then plain language.**
  `- **Fetch toggle for server mode** — an operator can now run a push-only gateway…`. Lead with what a developer or
  operator sees change, then why, then the PR refs.
- **Link the docs page for anything configurable**, pinned to the docs site:
  `https://rbc.github.io/fogwall/configuration/<page>.html`. Page names come from `docs/SUMMARY.md`.
- **No narrative.** No "we discovered", no rejected alternatives, no test plans, no session history. What is in the
  release, and what an operator must do about it.
- **No internal infrastructure detail** — state fogwall's behaviour, never a specific deployment's network shape.
- Never claim more than the code does. If a capability landed on one proxy mode, one database family or one provider
  only, say which; parity gaps are named, not glossed.

### Sections, in this order

Omit any section with nothing in it.

```markdown
# fogwall <version> (DRAFT)

<One or two sentences: what this release is, and — plainly — whether the default developer push workflow changes.>

## Breaking changes & upgrade notes

<Config key renames and deprecations, changed defaults, changed routes, REST field changes, DB migrations, and
behavioural changes that can newly block a push that used to pass. Each one says what an operator must do. A control
that used to be a silent no-op and now enforces belongs here — it will start blocking pushes.>

## New features

## Security

<Hardening and vulnerability fixes, each naming what was exposed and what closed it.>

## Bug fixes

## Performance

## Maintenance

<CI, build, refactors, docs — one or two bullets with grouped PR refs, not a list.>

## Dependencies

<One bullet per ecosystem group (Java, frontend npm, GitHub Actions, base images), with grouped PR refs and the notable
version jumps only.>

## Upgrade

<Numbered, actionable steps in the order an operator performs them. End with the ones that need no action: "No DB
migration" / "no REST changes" / "pull the new images". If there IS a migration, name it (V14, V15) and say it applies
automatically on startup.>

**Full Changelog**: https://github.com/RBC/fogwall/compare/<prev tag>...<version>
```

Judgement on where a change goes: a fix that closes an exposure is **Security**, not Bug fixes. A behavioural change an
operator must know about is **Breaking changes**, even if its PR was titled `fix:`. A change nobody running fogwall
would ever notice is **Maintenance**.

## Step 3 — publish the draft

Reuse the existing draft for this version if there is one; do not accumulate drafts.

```bash
TAG_GLOB="$V-draft-"
EXISTING=$(gh release list --limit 50 --json tagName,isDraft \
  --jq ".[] | select(.isDraft and (.tagName | startswith(\"$TAG_GLOB\"))) | .tagName" | head -1)

if [ -n "$EXISTING" ]; then
  gh release edit "$EXISTING" --draft --notes-file "$BODY"
else
  gh release create "$V-draft-$(date -u +%Y%m%dT%H%M%SZ)" \
    --draft --title "fogwall $V (DRAFT)" --notes-file "$BODY" --target main
fi
```

`--draft` is mandatory on both commands. Confirm afterwards with `gh release list --json tagName,isDraft` that the entry
is still a draft, and report the draft URL.

Do not bump `version` in `build.gradle` — that is the `release` skill's job at publish time.
