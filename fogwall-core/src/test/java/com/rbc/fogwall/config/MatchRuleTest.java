package com.rbc.fogwall.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MatchRuleTest {

    @Test
    void match_fromString_defaultsToRegex() {
        assertEquals(MatchRule.Match.REGEX, MatchRule.Match.fromString(null));
        assertEquals(MatchRule.Match.REGEX, MatchRule.Match.fromString(""));
        assertEquals(MatchRule.Match.REGEX, MatchRule.Match.fromString("  "));
        assertEquals(MatchRule.Match.REGEX, MatchRule.Match.fromString("regex"));
        assertEquals(MatchRule.Match.LITERAL, MatchRule.Match.fromString("LITERAL"));
    }

    @Test
    void match_fromString_invalid_throws() {
        assertThrows(IllegalArgumentException.class, () -> MatchRule.Match.fromString("glob"));
    }

    @Test
    void literal_matchesCaseSensitiveSubstring() {
        MatchRule rule = MatchRule.literal("SECRET");
        assertTrue(rule.matches("this has a SECRET in it"), "exact-case substring matches");
        assertFalse(rule.matches("this has a secret in it"), "literal matching is case-sensitive, not folded");
        assertTrue(rule.matches("SECRET"));
        assertFalse(rule.matches("nothing here"));
        assertFalse(rule.matches(null));
    }

    @Test
    void regex_matchesWithFindSemantics() {
        MatchRule rule = MatchRule.regex("(?i)password\\s*=");
        assertTrue(rule.matches("the password = hunter2"));
        assertFalse(rule.matches("passphrase only"));
        assertFalse(rule.matches(null));
    }

    @Test
    void emptyValue_throws() {
        assertThrows(IllegalArgumentException.class, () -> MatchRule.literal(""));
        assertThrows(IllegalArgumentException.class, () -> new MatchRule(MatchRule.Match.REGEX, null));
    }

    @Test
    void invalidRegex_throwsAtConstruction() {
        var ex = assertThrows(IllegalArgumentException.class, () -> MatchRule.regex("("));
        assertTrue(ex.getMessage().contains("invalid rule regex"));
    }

    @Test
    void describe_showsOperatorAndValue() {
        assertEquals("~ ^svc-", MatchRule.regex("^svc-").describe());
        assertEquals("= WIP", MatchRule.literal("WIP").describe());
    }
}
