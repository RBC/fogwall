package com.rbc.fogwall.validation;

import static com.rbc.fogwall.git.GitClientUtils.SymbolCodes.*;
import static com.rbc.fogwall.git.GitClientUtils.sym;

import com.rbc.fogwall.config.CommitConfig;
import com.rbc.fogwall.git.Commit;
import com.rbc.fogwall.git.Contributor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

/**
 * Validates committer and author emails in the pushed commits against configured domain/local rules.
 *
 * <p>Committer validation ({@code commit.committer.email.*}) is the primary corporate control: the committer is the
 * employee who authored or rebased the change, and must use their work identity. Author validation
 * ({@code commit.author.email.*}) is an optional stricter policy: when configured, it also checks the original author
 * email, which effectively disallows rebasing commits from contributors outside the allowed domain.
 *
 * <p>Each policy is independent — configure one, both, or neither. Violations from each are reported separately with
 * explicit labels so developers know exactly which identity triggered the block and what to do about it.
 */
@RequiredArgsConstructor
public class AuthorEmailCheck implements CommitCheck {

    private final CommitConfig config;

    @Override
    public List<Violation> check(List<Commit> commits) {
        List<Violation> violations = new ArrayList<>();

        Set<String> committerEmails =
                commits.stream().map(c -> c.getCommitter().getEmail()).collect(Collectors.toSet());
        for (String email : committerEmails) {
            String reason = config.getCommitter().getEmail().violationReason(email);
            if (reason != null) {
                String detail = sym(CROSS_MARK) + "  committer email (" + email + "): " + reason + "\n"
                        + "  \u2192 The committer is you — the person who ran git commit or git rebase.\n"
                        + "  \u2192 Fix: git config user.email \"you@corp.com\"";
                violations.add(new Violation("committer:" + email, reason, detail));
            }
        }

        Set<String> authorEmails =
                commits.stream().map(c -> c.getAuthor().getEmail()).collect(Collectors.toSet());
        for (String email : authorEmails) {
            String reason = config.getAuthor().getEmail().violationReason(email);
            if (reason != null) {
                String detail = sym(CROSS_MARK) + "  author email (" + email + "): " + reason + "\n"
                        + "  \u2192 This commit was originally authored by someone outside the allowed domain.\n"
                        + "  \u2192 Rebasing external commits onto this branch is not permitted by policy.\n"
                        + "  \u2192 Alternative: open a PR from the original author's fork instead of rebasing.";
                violations.add(new Violation("author:" + email, reason, detail));
            }
        }

        return violations;
    }

    /**
     * Validates an annotated tag's tagger email against the committer policy ({@code commit.committer.email.*}).
     *
     * <p>Git fills the tagger line from the same identity that fills the committer line ({@code user.email} /
     * {@code GIT_COMMITTER_*}), so the person creating a tag is held to the same corporate-identity rules as the person
     * creating a commit — there is deliberately no separate tagger config to drift out of sync.
     *
     * @param tagger the tag object's tagger, or {@code null} when the push carries no annotated tag
     * @return violations against the committer email policy; empty when allowed or no policy is configured
     */
    public List<Violation> checkTagger(Contributor tagger) {
        if (tagger == null) {
            return List.of();
        }
        String email = tagger.getEmail();
        String reason = config.getCommitter().getEmail().violationReason(email);
        if (reason == null) {
            return List.of();
        }
        String detail = sym(CROSS_MARK) + "  tagger email (" + email + "): " + reason + "\n"
                + "  → The tagger is you — the person who ran git tag -a (or -s).\n"
                + "  → Fix: git config user.email \"you@corp.com\", then delete and re-create the tag.";
        return List.of(new Violation("tagger:" + email, reason, detail));
    }
}
