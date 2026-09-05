#!/usr/bin/env bash
# Claude Code PreToolUse hook for the Bash tool. Wired up in .claude/settings.json.
#
# Blocks shell commands that break repo conventions which are easy to state mechanically
# (see CLAUDE.md "Git workflow" and "Issue and PR hygiene"). Prose rules get forgotten;
# this does not. Input is the hook JSON on stdin; exit 2 blocks the command and sends
# stderr back to Claude as the reason. Anything else passes through untouched.
set -euo pipefail

input=$(cat)
cmd=$(printf '%s' "$input" | jq -r '.tool_input.command // empty')
[ -n "$cmd" ] || exit 0

block() {
    printf 'BLOCKED by .claude/hooks/guard-bash.sh: %s\n' "$1" >&2
    exit 2
}

# Text that ends up in commits, issues or PRs may come from a file rather than the command
# line (git commit -F, gh --body-file, gh api -F body=@file). Pull those files in so the
# footer/trailer checks below see them too.
text="$cmd"
while read -r ref; do
    f="${ref#*@}"
    f="${f#--body-file}"; f="${f#--file}"; f="${f#-F}"; f="${f# }"; f="${f#=}"
    f="${f//\"/}"; f="${f//\'/}"
    [ -n "$f" ] && [ -f "$f" ] && text+=$'\n'"$(cat "$f")"
done < <(grep -oE '(body=@|--body-file[ =]|--file[ =]|-F )["'"'"']?[^ "'"'"']+' <<<"$cmd" || true)

is_git()  { grep -Eq '(^|[;&|] *)git ' <<<"$cmd"; }
is_gh()   { grep -Eq '(^|[;&|] *)gh ' <<<"$cmd"; }

# --- git staging / hooks / signing -------------------------------------------------------
if grep -Eq 'git +add( +[^;&|]*)? +(-A|--all)\b' <<<"$cmd"; then
    block "never 'git add -A' / '--all'; the working tree may hold sensitive or scratch files. Stage paths explicitly."
fi
if is_git && grep -Eq -- '--no-verify|--no-gpg-sign|gpgsign=false|core\.hooksPath' <<<"$cmd"; then
    block "never bypass commit hooks or signing. The pre-commit hook (format, lint, PMD) is the signal that something is off; fix the cause instead."
fi

# --- branch maintenance ------------------------------------------------------------------
if grep -Eq 'git +merge +([^;&|]* )?(origin/)?main\b' <<<"$cmd"; then
    block "never merge main into a branch (the repo is merge-commit-only, so this yields two merge commits). Rebase onto origin/main instead; see the sync-pr-branch skill."
fi
if grep -Eq 'git +rebase +([^;&|]* )?(-i|--interactive)\b' <<<"$cmd"; then
    block "interactive rebase needs a TTY. Squash with 'git reset --soft <base>' against a freshly fetched origin/main."
fi

# --- commit message content --------------------------------------------------------------
if is_git && grep -Eiq '\[(ci skip|skip ci)\]' <<<"$text"; then
    block "no '[ci skip]' unless the user explicitly asked for it."
fi
if grep -Eq 'Claude-Session:' <<<"$text"; then
    block "no 'Claude-Session:' trailer; session URLs are garbage-collected within weeks. Keep the Co-Authored-By trailer only."
fi

# --- gh / GitHub hygiene ------------------------------------------------------------------
if grep -Eq 'gh +pr +merge( +[^;&|]*)? +(-s|--squash|-r|--rebase)\b' <<<"$cmd"; then
    block "squash and rebase merges are disabled on this repo; 'gh pr merge' is always --merge."
fi
if grep -Eq 'gh +label +create' <<<"$cmd"; then
    block "only use labels that already exist; never create new ones."
fi
if grep -Eq 'gh +(issue|pr) +create( +[^;&|]*)? +(-R|--repo)[ =]' <<<"$cmd" \
   && ! grep -Eq '(-R|--repo)[ =]["'"'"']?(github\.com/)?RBC/fogwall\b' <<<"$cmd"; then
    block "never open issues or PRs on external repos on the maintainer's behalf. Note the upstream tracker for manual follow-up instead."
fi
if grep -Eq 'gh +api( +[^;&|]*)? +(-X|--method)[ =]POST( +[^;&|]*)? +(repos/)' <<<"$cmd" \
   && grep -Eq 'repos/[^ /]+/[^ /]+/(issues|pulls)\b' <<<"$cmd" \
   && ! grep -Eq 'repos/RBC/fogwall/' <<<"$cmd"; then
    block "never open issues or PRs on external repos on the maintainer's behalf."
fi
if is_gh && grep -Eiq 'Generated with \[?Claude Code|claude\.com/claude-code' <<<"$text"; then
    block "no 'Generated with Claude Code' footer on issues or PRs in this public repo."
fi

exit 0
