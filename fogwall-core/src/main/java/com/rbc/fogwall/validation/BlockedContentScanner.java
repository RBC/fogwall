package com.rbc.fogwall.validation;

import com.rbc.fogwall.config.BlockConfig;
import com.rbc.fogwall.config.MatchRule;
import java.util.ArrayList;
import java.util.List;

/**
 * Matches text against the configured block rules.
 *
 * <p>Separate from {@link BlockedContentDiffCheck} because the rules are about content, not about diffs: the same
 * blocked term is equally unwelcome in a pushed line and in the body of a pull request opened through the SCM API
 * proxy. The diff check supplies added lines and their file; the SCM API path supplies a title or description. Only
 * what is fed in differs.
 */
public final class BlockedContentScanner {

    /** One match, with enough context to explain the decision after the fact. */
    public record Match(String rule, String location, String line) {

        /** Human-readable summary, used as both the violation title and its audit reason. */
        public String summary() {
            return location == null || location.isBlank() ? rule : rule + " in " + location;
        }
    }

    private BlockedContentScanner() {}

    /** Whether {@code block} would match anything at all — lets a caller skip the work entirely. */
    public static boolean isConfigured(BlockConfig block) {
        return block != null && !block.getRules().isEmpty();
    }

    /**
     * Scans one piece of text, reporting at most one match per rule. {@code location} names where the text came from —
     * a file path for a diff line, a field name such as {@code title} for an SCM API entity — and may be {@code null}.
     */
    public static List<Match> scan(String text, String location, BlockConfig block) {
        if (text == null || text.isEmpty() || !isConfigured(block)) {
            return List.of();
        }
        List<Match> matches = new ArrayList<>();
        for (MatchRule rule : block.getRules()) {
            if (rule.matches(text)) {
                matches.add(new Match(describe(rule), location, text.strip()));
            }
        }
        return matches;
    }

    /** Rule label for the violation message, distinguishing a literal term from a regex pattern. */
    private static String describe(MatchRule rule) {
        return rule.getMatch() == MatchRule.Match.LITERAL
                ? "blocked term: \"" + rule.getValue() + "\""
                : "blocked pattern: " + rule.getValue();
    }
}
