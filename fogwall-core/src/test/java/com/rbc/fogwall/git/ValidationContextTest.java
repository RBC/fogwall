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
        ctx.addIssue(PushStepKind.URL_RULE, "Something broke", "Details here");
        assertTrue(ctx.hasIssues());
        assertEquals(1, ctx.getIssues().size());
    }

    @Test
    void multipleIssues_preservesOrder() {
        ValidationContext ctx = new ValidationContext();
        ctx.addIssue(PushStepKind.URL_RULE, "Issue A", "Detail A");
        ctx.addIssue(PushStepKind.AUTHOR_EMAIL, "Issue B", "Detail B");
        ctx.addIssue(PushStepKind.SECRET_SCAN, "Issue C", "Detail C");

        assertEquals(3, ctx.getIssues().size());
        assertEquals(PushStepKind.URL_RULE, ctx.getIssues().get(0).kind());
        assertEquals(PushStepKind.SECRET_SCAN, ctx.getIssues().get(2).kind());
    }

    @Test
    void getIssues_returnsUnmodifiableList() {
        ValidationContext ctx = new ValidationContext();
        ctx.addIssue(PushStepKind.URL_RULE, "Summary", "Detail");
        assertThrows(
                UnsupportedOperationException.class,
                () -> ctx.getIssues()
                        .add(new ValidationContext.ValidationIssue(PushStepKind.GPG_SIGNATURE, "Y", "Z", false)));
    }

    @Test
    void issueRecord_fieldsStoredCorrectly() {
        ValidationContext ctx = new ValidationContext();
        ctx.addIssue(PushStepKind.COMMIT_MESSAGE, "my summary", "my detail");
        ValidationContext.ValidationIssue issue = ctx.getIssues().get(0);
        assertEquals(PushStepKind.COMMIT_MESSAGE, issue.kind());
        assertEquals("my summary", issue.summary());
        assertEquals("my detail", issue.detail());
    }

    @Test
    void addMultipleIssues_hasIssuesRemainsTrue() {
        ValidationContext ctx = new ValidationContext();
        ctx.addIssue(PushStepKind.URL_RULE, "s1", "d1");
        ctx.addIssue(PushStepKind.AUTHOR_EMAIL, "s2", "d2");
        assertTrue(ctx.hasIssues());
        assertEquals(2, ctx.getIssues().size());
    }
}
