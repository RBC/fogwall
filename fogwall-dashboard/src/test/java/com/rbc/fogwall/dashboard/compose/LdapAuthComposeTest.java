package com.rbc.fogwall.dashboard.compose;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The auth axis: a directory identity signs in and reviews a push.
 *
 * <p>Only runs when the stack was brought up with {@code --auth ldap}; otherwise it skips. What it covers is the thing
 * that breaks silently — a directory whose users still authenticate but whose groups stop mapping to roles, while every
 * other suite stays green because none of them involve a directory at all.
 *
 * <p>Two identities: the push is authorised by a Gitea token that resolves to {@code test-user}, the review done by an
 * LDAP member of {@code fogwall_admins}. A single-identity test cannot tell an approval from a self-approval.
 */
@Tag("compose")
class LdapAuthComposeTest {

    /**
     * From docker/ldap-bootstrap.ldif: a member of {@code fogwall_admins}, which fogwall-ldap.yml maps to ADMIN.
     *
     * <p>This account exists only in the directory. docker-default declares local users (admin, test-user, user2,
     * user3, reviewer), so signing in as one of those would authenticate without involving the directory at all.
     */
    private static final String LDAP_ADMIN = "user5";

    private static final String LDAP_ADMIN_PASSWORD = "testpass123";

    private static ComposeStack stack;
    private static String token;

    @BeforeAll
    static void resolveStack() throws Exception {
        stack = ComposeStack.requireRunning();
        token = stack.accessToken(ComposeStack.TEST_USER, ComposeStack.TEST_USER_PASSWORD);
        assumeTrue(
                stack.ldapConfigured(),
                "the stack is not running the LDAP overlay — run: bash compose.sh --auth ldap -- up -d");
    }

    @Test
    void directoryUserSignsIn_andReviewsAPush() throws Exception {
        var session = stack.signIn(LDAP_ADMIN, LDAP_ADMIN_PASSWORD);
        assertTrue(session.isPresent(), "a directory user should be able to sign in");

        JsonNode me = new JsonMapper().readTree(session.get().get("/api/me").body());
        assertEquals(LDAP_ADMIN, me.path("username").asText(), "the session should belong to the directory user");
        assertTrue(
                me.path("authorities").toString().contains("ROLE_ADMIN"),
                "fogwall_admins should still map to ADMIN; the group mapping is what breaks quietly. Authorities: "
                        + me.path("authorities"));

        Path workspace = Files.createTempDirectory("fogwall-ldap-");
        String branch = "compose-ldap-" + UUID.randomUUID().toString().substring(0, 8);
        var git = new Git(workspace);
        Path repo = git.clone(
                stack.proxyUrl("proxy", ComposeStack.TEST_USER, token, ComposeStack.TEST_ORG, ComposeStack.TEST_REPO),
                "reviewed");
        git.branch(repo, branch);
        git.commit(repo, "ldap.txt", "reviewed by a directory user at " + Instant.now() + "\n", "feat: for review");

        assertFalse(git.push(repo).succeeded(), "the push should be held for review");
        JsonNode record = pendingFor(branch);
        assertNotNull(record, "fogwall should have recorded the held push for " + branch);

        var approval = session.get().post("/api/push/" + record.get("id").asText() + "/authorise", "{}");
        assertTrue(
                approval.statusCode() < 300,
                "the directory user should be able to approve someone else's push: " + approval.statusCode() + " "
                        + approval.body());

        assertTrue(git.push(repo).succeeded(), "the approved push should reach Gitea");
    }

    @Test
    void wrongDirectoryPassword_isRefused() throws Exception {
        assertTrue(
                stack.signIn(LDAP_ADMIN, "not-the-password").isEmpty(),
                "a bad directory password should not produce a session");
    }

    private JsonNode pendingFor(String branch) throws Exception {
        JsonNode records =
                new JsonMapper().readTree(stack.api("/api/push?limit=50").body());
        for (JsonNode record : records) {
            if ("PENDING".equals(record.path("status").asText())
                    && record.path("branch").asText().endsWith(branch)) {
                return record;
            }
        }
        return null;
    }
}
