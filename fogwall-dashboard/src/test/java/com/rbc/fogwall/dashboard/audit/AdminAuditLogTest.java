package com.rbc.fogwall.dashboard.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AdminAuditLogTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void format_noDetail_omitsDetailField() {
        String line = AdminAuditLog.format("alice", "user.create", "user:bob", AdminAuditLog.Outcome.SUCCESS, null);

        assertEquals("admin_action actor=alice action=user.create target=user:bob outcome=SUCCESS", line);
    }

    @Test
    void format_withDetail_appendsDetailField() {
        String line = AdminAuditLog.format(
                "alice", "user.email.add", "user:bob", AdminAuditLog.Outcome.SUCCESS, "email=bob@example.com");

        assertEquals(
                "admin_action actor=alice action=user.email.add target=user:bob outcome=SUCCESS "
                        + "detail=email=bob@example.com",
                line);
    }

    @Test
    void format_blankDetail_omitsDetailField() {
        String line = AdminAuditLog.format("alice", "user.create", "user:bob", AdminAuditLog.Outcome.SUCCESS, "  ");

        assertEquals("admin_action actor=alice action=user.create target=user:bob outcome=SUCCESS", line);
    }

    @Test
    void format_denied_reportsDeniedOutcome() {
        String line = AdminAuditLog.format(
                "alice", "group.delete", "group:g1", AdminAuditLog.Outcome.DENIED, "config-defined group");

        assertEquals(
                "admin_action actor=alice action=group.delete target=group:g1 outcome=DENIED "
                        + "detail=config-defined group",
                line);
    }

    @Test
    void success_withAuthenticatedPrincipal_doesNotThrow() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("alice", null));

        new AdminAuditLog().success("user.create", "user:bob");
    }

    @Test
    void success_withNoAuthentication_doesNotThrow() {
        new AdminAuditLog().success("cache.invalidate_all", "cache:server");
    }
}
