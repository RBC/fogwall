package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ValidationContextTest {

    @Test
    void newContext_hasNoIssues() {
        ValidationContext ctx = new ValidationContext();
        assertFalse(ctx.hasIssues());
        assertTrue(ctx.getIssues().isEmpty());
    }

    @Test
    void addIssue_thenHasIssues() {
        ValidationContext ctx = new ValidationContext();
        ctx.addIssue("TestHook", "Something broke", "Details here");
        assertTrue(ctx.hasIssues());
        assertEquals(1, ctx.getIssues().size());
    }

    @Test
    void multipleIssues_preservesOrder() {
        ValidationContext ctx = new ValidationContext();
        ctx.addIssue("HookA", "Issue A", "Detail A");
        ctx.addIssue("HookB", "Issue B", "Detail B");
        ctx.addIssue("HookC", "Issue C", "Detail C");

        assertEquals(3, ctx.getIssues().size());
        assertEquals("HookA", ctx.getIssues().get(0).hookName());
        assertEquals("HookC", ctx.getIssues().get(2).hookName());
    }

    @Test
    void getIssues_returnsUnmodifiableList() {
        ValidationContext ctx = new ValidationContext();
        ctx.addIssue("Hook", "Summary", "Detail");
        assertThrows(
                UnsupportedOperationException.class,
                () -> ctx.getIssues().add(new ValidationContext.ValidationIssue("X", "Y", "Z")));
    }

    @Test
    void issueRecord_fieldsStoredCorrectly() {
        ValidationContext ctx = new ValidationContext();
        ctx.addIssue("myHook", "my summary", "my detail");
        ValidationContext.ValidationIssue issue = ctx.getIssues().get(0);
        assertEquals("myHook", issue.hookName());
        assertEquals("my summary", issue.summary());
        assertEquals("my detail", issue.detail());
    }

    @Test
    void addMultipleIssues_hasIssuesRemainsTrue() {
        ValidationContext ctx = new ValidationContext();
        ctx.addIssue("H1", "s1", "d1");
        ctx.addIssue("H2", "s2", "d2");
        assertTrue(ctx.hasIssues());
        assertEquals(2, ctx.getIssues().size());
    }
}
