package com.rbc.fogwall.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EmailRuleTest {

    // --- Action.fromString ---

    @Test
    void action_fromString_parsesAllowBlockAndDenyAlias() {
        assertEquals(EmailRule.Action.ALLOW, EmailRule.Action.fromString("allow"));
        assertEquals(EmailRule.Action.ALLOW, EmailRule.Action.fromString(" ALLOW "));
        assertEquals(EmailRule.Action.BLOCK, EmailRule.Action.fromString("block"));
        assertEquals(EmailRule.Action.BLOCK, EmailRule.Action.fromString("deny"));
    }

    @Test
    void action_fromString_nullOrUnknown_throws() {
        assertThrows(IllegalArgumentException.class, () -> EmailRule.Action.fromString(null));
        var ex = assertThrows(IllegalArgumentException.class, () -> EmailRule.Action.fromString("permit"));
        assertTrue(ex.getMessage().contains("permit"));
    }

    // --- Field.fromString ---

    @Test
    void field_fromString_parsesAllThree() {
        assertEquals(EmailRule.Field.DOMAIN, EmailRule.Field.fromString("domain"));
        assertEquals(EmailRule.Field.LOCAL, EmailRule.Field.fromString("LOCAL"));
        assertEquals(EmailRule.Field.ADDRESS, EmailRule.Field.fromString("address"));
    }

    @Test
    void field_fromString_nullOrUnknown_throws() {
        assertThrows(IllegalArgumentException.class, () -> EmailRule.Field.fromString(null));
        assertThrows(IllegalArgumentException.class, () -> EmailRule.Field.fromString("subject"));
    }

    // --- Match.fromString ---

    @Test
    void match_fromString_defaultsToRegexWhenBlankOrNull() {
        assertEquals(EmailRule.Match.REGEX, EmailRule.Match.fromString(null));
        assertEquals(EmailRule.Match.REGEX, EmailRule.Match.fromString(""));
        assertEquals(EmailRule.Match.REGEX, EmailRule.Match.fromString("  "));
    }

    @Test
    void match_fromString_parsesLiteralAndRegex_rejectsUnknown() {
        assertEquals(EmailRule.Match.LITERAL, EmailRule.Match.fromString("literal"));
        assertEquals(EmailRule.Match.REGEX, EmailRule.Match.fromString("regex"));
        assertThrows(IllegalArgumentException.class, () -> EmailRule.Match.fromString("glob"));
    }

    // --- construction validation ---

    @Test
    void construct_emptyValue_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EmailRule(EmailRule.Action.ALLOW, EmailRule.Field.DOMAIN, EmailRule.Match.REGEX, ""));
    }

    @Test
    void construct_invalidRegex_throwsWithContext() {
        var ex = assertThrows(
                IllegalArgumentException.class,
                () -> new EmailRule(EmailRule.Action.BLOCK, EmailRule.Field.LOCAL, EmailRule.Match.REGEX, "("));
        assertTrue(ex.getMessage().contains("invalid email rule regex"));
    }

    // --- matches() across fields and match types ---

    @Test
    void matches_domainRegex() {
        var r = EmailRule.allow(EmailRule.Field.DOMAIN, EmailRule.Match.REGEX, "corp\\.com$");
        assertTrue(r.matches("dev", "corp.com", "dev@corp.com"));
        assertFalse(r.matches("dev", "gmail.com", "dev@gmail.com"));
    }

    @Test
    void matches_localRegex() {
        var r = EmailRule.block(EmailRule.Field.LOCAL, EmailRule.Match.REGEX, "^svc-");
        assertTrue(r.matches("svc-ci", "corp.com", "svc-ci@corp.com"));
        assertFalse(r.matches("alice", "corp.com", "alice@corp.com"));
    }

    @Test
    void matches_addressLiteral_caseInsensitive() {
        var r = EmailRule.allow(EmailRule.Field.ADDRESS, EmailRule.Match.LITERAL, "noreply@anthropic.com");
        assertTrue(r.matches("noreply", "anthropic.com", "NoReply@Anthropic.com"));
        assertFalse(r.matches("someone", "anthropic.com", "someone@anthropic.com"));
    }

    @Test
    void matches_nullTarget_isFalse() {
        // A DOMAIN rule against a null domain (e.g. malformed input) never matches.
        var r = EmailRule.allow(EmailRule.Field.DOMAIN, EmailRule.Match.LITERAL, "corp.com");
        assertFalse(r.matches("dev", null, "dev"));
    }

    // --- describe() ---

    @Test
    void describe_rendersActionFieldOperatorAndValue() {
        assertEquals(
                "block local ~ ^svc-",
                EmailRule.block(EmailRule.Field.LOCAL, EmailRule.Match.REGEX, "^svc-")
                        .describe());
        assertEquals(
                "allow address = bot@corp.com",
                EmailRule.allow(EmailRule.Field.ADDRESS, EmailRule.Match.LITERAL, "bot@corp.com")
                        .describe());
    }
}
