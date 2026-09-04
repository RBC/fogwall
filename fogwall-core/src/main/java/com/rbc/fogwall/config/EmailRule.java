package com.rbc.fogwall.config;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.Getter;

/**
 * One allow/block rule in an email-match policy (fogwall#146). Rules apply symmetrically across every dimension of an
 * email address (domain, local part, full address), literal or regex, mirroring fogwall's {@code AccessRule}/
 * {@code RepoPermission} "unified rule shape".
 *
 * <p>A policy is a list of these. Evaluation (see {@link CommitConfig.EmailConfig#violationReason(String)}):
 *
 * <ul>
 *   <li><b>block wins</b> — if any {@code BLOCK} rule matches, the email is rejected;
 *   <li><b>allow gates</b> — if any {@code ALLOW} rule exists, the email must match at least one to pass; with no allow
 *       rule, everything not blocked is permitted.
 * </ul>
 *
 * <p>Regex rules pre-compile their pattern at construction so a malformed pattern fails fast at config load, not on the
 * hot push path.
 */
@Getter
public final class EmailRule {

    /** Whether a match permits or rejects the email. */
    public enum Action {
        ALLOW,
        BLOCK;

        public static Action fromString(String value) {
            if (value == null) throw new IllegalArgumentException("email rule 'action' is required (allow | block)");
            return switch (value.trim().toLowerCase()) {
                case "allow" -> ALLOW;
                case "block", "deny" -> BLOCK;
                default ->
                    throw new IllegalArgumentException(
                            "invalid email rule action '" + value + "' (expected allow | block)");
            };
        }
    }

    /** Which part of the address the rule matches against. */
    public enum Field {
        /** The domain part, after {@code @}. */
        DOMAIN,
        /** The local part, before {@code @}. */
        LOCAL,
        /** The full {@code local@domain} address. */
        ADDRESS;

        public static Field fromString(String value) {
            if (value == null)
                throw new IllegalArgumentException("email rule 'field' is required (domain | local | address)");
            return switch (value.trim().toLowerCase()) {
                case "domain" -> DOMAIN;
                case "local" -> LOCAL;
                case "address" -> ADDRESS;
                default ->
                    throw new IllegalArgumentException(
                            "invalid email rule field '" + value + "' (expected domain | local | address)");
            };
        }
    }

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
                    throw new IllegalArgumentException(
                            "invalid email rule match '" + value + "' (expected literal | regex)");
            };
        }
    }

    private final Action action;
    private final Field field;
    private final Match match;
    private final String value;
    private final Pattern pattern; // non-null iff match == REGEX

    public EmailRule(Action action, Field field, Match match, String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("email rule 'value' must not be empty (" + action + " " + field + ")");
        }
        this.action = action;
        this.field = field;
        this.match = match;
        this.value = value;
        try {
            this.pattern = match == Match.REGEX ? Pattern.compile(value) : null;
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException(
                    "invalid email rule regex '" + value + "' (" + action + " " + field + "): " + e.getMessage(), e);
        }
    }

    /** Convenience factory for an allow rule. */
    public static EmailRule allow(Field field, Match match, String value) {
        return new EmailRule(Action.ALLOW, field, match, value);
    }

    /** Convenience factory for a block rule. */
    public static EmailRule block(Field field, Match match, String value) {
        return new EmailRule(Action.BLOCK, field, match, value);
    }

    /** Whether this rule matches the given already-split email parts. */
    boolean matches(String local, String domain, String address) {
        String target =
                switch (field) {
                    case DOMAIN -> domain;
                    case LOCAL -> local;
                    case ADDRESS -> address;
                };
        if (target == null) return false;
        return switch (match) {
            case LITERAL -> target.equalsIgnoreCase(value);
            case REGEX -> pattern.matcher(target).find();
        };
    }

    /** Short human-readable form for violation messages, e.g. {@code block local ~ ^svc-}. */
    String describe() {
        String op = match == Match.LITERAL ? "=" : "~";
        return action.name().toLowerCase() + " " + field.name().toLowerCase() + " " + op + " " + value;
    }
}
