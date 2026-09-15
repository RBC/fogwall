package com.rbc.fogwall.config;

import lombok.Getter;

/**
 * One allow/block rule in an email-match policy. Rules apply symmetrically across every dimension of an email address
 * (domain, local part, full address), literal or regex, mirroring fogwall's {@code AccessRule}/{@code RepoPermission}
 * "unified rule shape".
 *
 * <p>Extends the shared {@link MatchRule} for its data — the match type, value, and compiled pattern, with their
 * fail-fast validation — and adds the two concerns an email policy needs and a content-block rule does not: an
 * {@link Action} (allow / block) and a {@link Field} (which part of the address to test). It supplies its own match
 * (see {@link #matches(String, String, String)}): a literal is exact and case-insensitive here, versus the base's
 * case-sensitive substring scan of a free-text body.
 *
 * <p>A policy is a list of these. Evaluation (see {@link CommitConfig.EmailConfig#violationReason(String)}):
 *
 * <ul>
 *   <li><b>block wins</b> — if any {@code BLOCK} rule matches, the email is rejected;
 *   <li><b>allow gates</b> — if any {@code ALLOW} rule exists, the email must match at least one to pass; with no allow
 *       rule, everything not blocked is permitted.
 * </ul>
 */
@Getter
public final class EmailRule extends MatchRule {

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

    private final Action action;
    private final Field field;

    public EmailRule(Action action, Field field, Match match, String value) {
        super(match, value);
        this.action = action;
        this.field = field;
    }

    /** Convenience factory for an allow rule. */
    public static EmailRule allow(Field field, Match match, String value) {
        return new EmailRule(Action.ALLOW, field, match, value);
    }

    /** Convenience factory for a block rule. */
    public static EmailRule block(Field field, Match match, String value) {
        return new EmailRule(Action.BLOCK, field, match, value);
    }

    /**
     * Whether this rule matches the given already-split email parts. A {@code LITERAL} tests exact, case-insensitive
     * equality of the selected field — email fields are conventionally case-insensitive, and an allow-literal
     * {@code corp.com} must mean the domain <em>is</em> {@code corp.com}, not merely contains it. A {@code REGEX} tests
     * {@link java.util.regex.Pattern#find()}. This is deliberately different from the base {@link #matches(String)},
     * which scans a free-text body for a substring.
     */
    boolean matches(String local, String domain, String address) {
        String target =
                switch (field) {
                    case DOMAIN -> domain;
                    case LOCAL -> local;
                    case ADDRESS -> address;
                };
        if (target == null) return false;
        return switch (getMatch()) {
            case LITERAL -> target.equalsIgnoreCase(getValue());
            case REGEX -> getPattern().matcher(target).find();
        };
    }

    /** Short human-readable form for violation messages, e.g. {@code block local ~ ^svc-}. */
    @Override
    String describe() {
        return action.name().toLowerCase() + " " + field.name().toLowerCase() + " " + super.describe();
    }
}
