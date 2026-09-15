package com.rbc.fogwall.config;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.Getter;

/**
 * One literal-or-regex matcher against a single piece of text: the shared shape behind fogwall's pattern-based content
 * controls. A value plus how to compare it — nothing about what is being matched or whether a match permits or rejects.
 *
 * <p>Content controls (diff scan, commit message, SCM API content) hold a list of these directly and treat every match
 * as a block; that is all they need. {@link EmailRule} extends this with the two concerns an email policy adds and a
 * content rule has no use for: an {@code action} (allow / block) and a {@code field} (which part of the address to
 * test). Both layers share one {@link Match} type and one comparison, so literal-vs-regex behaves identically wherever
 * a rule appears.
 *
 * <p>Regex rules pre-compile their pattern at construction so a malformed pattern fails fast at config load, not on the
 * hot push path.
 */
@Getter
public class MatchRule {

    /** How the rule value is compared against the target. */
    public enum Match {
        /** Case-insensitive exact string equality. */
        LITERAL,
        /** {@link Pattern#find()} against the target. */
        REGEX;

        public static Match fromString(String value) {
            if (value == null || value.isBlank()) return REGEX; // regex is the historical default
            return switch (value.trim().toLowerCase()) {
                case "literal" -> LITERAL;
                case "regex" -> REGEX;
                default ->
                    throw new IllegalArgumentException("invalid rule match '" + value + "' (expected literal | regex)");
            };
        }
    }

    private final Match match;
    private final String value;
    private final Pattern pattern; // non-null iff match == REGEX

    public MatchRule(Match match, String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("rule 'value' must not be empty");
        }
        this.match = match;
        this.value = value;
        try {
            this.pattern = match == Match.REGEX ? Pattern.compile(value) : null;
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("invalid rule regex '" + value + "': " + e.getMessage(), e);
        }
    }

    /** A regex rule over {@code value}. */
    public static MatchRule regex(String value) {
        return new MatchRule(Match.REGEX, value);
    }

    /** A literal (case-insensitive substring) rule over {@code value}. */
    public static MatchRule literal(String value) {
        return new MatchRule(Match.LITERAL, value);
    }

    /**
     * Whether this rule matches the given text. A {@code LITERAL} rule tests case-sensitive containment (the term
     * appears anywhere in the text, exact case); a {@code REGEX} rule tests {@link Pattern#find()}. Case-insensitive
     * matching is opt-in through a {@code (?i)} regex, never a silent default. A {@code null} target never matches.
     */
    public boolean matches(String text) {
        if (text == null) return false;
        return switch (match) {
            case LITERAL -> text.contains(value);
            case REGEX -> pattern.matcher(text).find();
        };
    }

    /** The operator shown in {@link #describe()}: {@code =} for a literal, {@code ~} for a regex. */
    protected String operator() {
        return match == Match.LITERAL ? "=" : "~";
    }

    /** Short human-readable form, e.g. {@code ~ ^svc-}. */
    String describe() {
        return operator() + " " + value;
    }
}
