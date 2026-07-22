---
name: refresh-pattern-bundles
description:
  Check hand-ported detection rules against a pinned upstream open-source recognizer library for drift, and walk through
  applying changes deliberately. Use when a project vendors regex/validator logic (not the whole library/runtime) from a
  project like data-privacy-stack/presidio and needs to periodically refresh it.
---

## What this is for

A general technique for projects that want detection-rule _content_ (regexes, context keywords, structural validators)
from a mature open-source library, without taking on that library as a live dependency - no new runtime, no
service/sidecar, no NLP models. Rules are hand-translated once into the host project's own language/runtime, pinned to a
specific upstream commit for provenance, and periodically checked for drift with this skill.

This trades automatic upstream updates for a much smaller footprint and no new attack surface / failure mode in a
security-relevant code path. It's the right tradeoff when you only need a handful of well-defined rules (a few regexes +
context words + a checksum), not when you need the full breadth of what the upstream library does (e.g. NLP-based entity
detection) - in that case, actually depending on the library is more honest than reimplementing it piecemeal.

**Example instance in this repo:** fogwall's PII content-pattern bundles
(`fogwall-core/src/main/resources/pattern-bundles/`) are ported from
[data-privacy-stack/presidio](https://github.com/data-privacy-stack/presidio)'s regex-based `PatternRecognizer` classes.
See `PROVENANCE.md` in that directory for the pinned commit and the upstream-file-to-fogwall-bundle mapping. Adjust the
paths/mapping below for other instances of this pattern.

## This is a diff-and-review workflow, not an auto-apply one

Unlike a mechanical version bump, a changed regex or context-keyword list is a judgment call about
false-positive/false-negative tradeoffs. **Never silently apply an upstream diff** - always explain what changed and why
it's safe (or not) to adopt, and let the user decide.

## Steps

### 1. Read the current pin

Find the project's provenance file (e.g. `PROVENANCE.md` alongside the ported rule resources) for the pinned upstream
commit SHA and the table mapping each local rule/bundle to its upstream source file(s).

### 2. Fetch the latest upstream commit

```bash
gh api repos/{upstream-owner}/{upstream-repo}/commits/{default-branch} -q .sha
```

If it matches the pinned SHA, report "up to date" and stop.

### 3. Diff each tracked source file, old pin vs. latest

For each upstream file in the provenance mapping:

```bash
gh api "repos/{owner}/{repo}/contents/{path}?ref={pinned_sha}" -q .content | base64 -d > /tmp/old
gh api "repos/{owner}/{repo}/contents/{path}?ref={latest_sha}" -q .content | base64 -d > /tmp/new
diff -u /tmp/old /tmp/new
```

Files with no diff need no action. For files with a diff, read both versions in full - don't reason from the diff hunk
alone; regex escaping and delimiter changes are easy to miss in a unified diff.

### 4. For each changed file, present the change and a recommendation

Explain in plain terms what changed: the regex pattern, the context-keyword list, or any structural validation logic
(checksum, placeholder rejection, etc.). State whether adopting it as-is looks safe, or whether it needs re-tuning for
the host project's specific use case - upstream libraries tune rules for their own scanning target (often free-text/NLP
documents), which may behave differently against the host project's actual input (e.g. source code diffs, structured
data, commit messages).

**Stop and ask before applying anything.**

### 5. Apply approved changes

- Update the corresponding rule entries in the local resource/config (regex, context keywords, confidence tier if
  tracked).
- If validation logic changed, update the matching local validator implementation.
- Update the provenance file's pinned SHA, and note what changed and when in its changelog section.
- Re-run the project's tests covering the ported rules before considering the refresh done.

### 6. Report

Summarize: files checked, files changed, what was adopted vs. deferred (with reasoning for anything deferred), and the
new pinned SHA.
