package com.rbc.fogwall.validation;

import static com.rbc.fogwall.git.GitClientUtils.SymbolCodes.*;
import static com.rbc.fogwall.git.GitClientUtils.sym;

import com.rbc.fogwall.config.CommitConfig;
import com.rbc.fogwall.config.MatchRule;
import com.rbc.fogwall.git.Commit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

/** Validates that no commit message contains blocked literals or patterns. */
@RequiredArgsConstructor
public class CommitMessageCheck implements CommitCheck {

    private final CommitConfig config;

    @Override
    public List<Violation> check(List<Commit> commits) {
        List<Violation> violations = new ArrayList<>();
        for (Commit commit : commits) {
            String reason = violationReason(commit.getMessage());
            if (reason != null) {
                String subject = commit.getMessage().lines().findFirst().orElse("(empty)");
                String detail = sym(CROSS_MARK) + "  " + subject + ": " + reason + "\n"
                        + "  \u2192 Messages must not contain: WIP, fixup!, squash!, DO NOT MERGE";
                violations.add(new Violation(subject, reason, detail));
            }
        }
        return violations;
    }

    /** Returns the reason the message is rejected, or {@code null} if it is allowed. */
    private String violationReason(String message) {
        if (message == null || message.isEmpty()) {
            return "empty commit message";
        }

        for (MatchRule rule : config.getMessage().getBlock().getRules()) {
            if (rule.matches(message)) {
                return rule.getMatch() == MatchRule.Match.LITERAL
                        ? "contains blocked term: \"" + rule.getValue() + "\""
                        : "matches blocked pattern: " + rule.getValue();
            }
        }

        return null;
    }
}
