package com.rbc.fogwall.validation;

import static com.rbc.fogwall.git.GitClientUtils.SymbolCodes.*;
import static com.rbc.fogwall.git.GitClientUtils.sym;

import com.rbc.fogwall.config.CommitConfig;
import com.rbc.fogwall.git.Commit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
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
            String reason = violationReason(email, config.getCommitter().getEmail());
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
            String reason = violationReason(email, config.getAuthor().getEmail());
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

    /** Returns the reason the email is rejected under the given email config, or {@code null} if it is allowed. */
    private String violationReason(String email, CommitConfig.EmailConfig emailConfig) {
        Pattern localBlock = emailConfig.getLocal().getBlock();
        Pattern domainAllow = emailConfig.getDomain().getAllow();

        if (localBlock == null && domainAllow == null) {
            return null;
        }

        if (email == null || email.isEmpty()) {
            return "empty email";
        }

        int atIndex = email.lastIndexOf('@');
        if (atIndex < 0) {
            return "missing @ in email";
        }
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex + 1);

        if (localBlock != null && localBlock.matcher(local).find()) {
            return "blocked local part (" + local + ")";
        }

        if (domainAllow != null && !domainAllow.matcher(domain).find()) {
            return "domain not allowed (" + domain + ")";
        }

        return null;
    }
}
