package com.rbc.fogwall.dashboard.compose;

import static org.junit.jupiter.api.Assertions.*;

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
 * A push that resolves to a user no configuration mentions: provisioned, linked and granted through the API, then
 * resolved from the database.
 *
 * <p>This is the shape a deployment behind an external identity provider actually has. There, {@code users:} in
 * configuration is empty — rows appear when someone signs in, or when an operator provisions them ahead of a first
 * login so permissions can be attached. SCM identities are linked afterwards, never declared.
 *
 * <p>Every other case in this suite pushes as an account the shipped configuration already describes, which means they
 * resolve identity out of config and leave the database paths behind it unexercised. This one uses an upstream account
 * no profile mentions, so the only way the push can resolve is through the rows the API created. It runs on every
 * database leg, which is where those paths differ.
 */
@Tag("compose")
class ProvisionedIdentityComposeTest {

    /** A Gitea account the seed creates and no profile maps. Linking it is the test. */
    private static final String UPSTREAM_LOGIN = "idp-user";

    private static ComposeStack stack;
    private static String token;

    /** Named for the shape rather than a directory: provisioning is the same whoever the identity provider is. */
    private static final String PROXY_USER = "idp-provisioned";

    @BeforeAll
    static void resolveStack() throws Exception {
        stack = ComposeStack.requireRunning();
        token = stack.accessToken(UPSTREAM_LOGIN, ComposeStack.TEST_USER_PASSWORD);
    }

    @Test
    void provisionedUser_resolvesAPushFromTheDatabase() throws Exception {
        // An SCM identity binds to one user and a permission to one path per user; both refuse a second claim.
        // Deleting the user releases both, so a re-run against a long-lived stack starts clean.
        stack.apiDelete("/api/users/" + PROXY_USER);

        var provisioned = stack.apiPost("/api/users/provision", "{\"username\":\"" + PROXY_USER + "\"}");
        assertTrue(
                provisioned.statusCode() < 300,
                "provisioning should create the row: " + provisioned.statusCode() + " " + provisioned.body());

        var linked = stack.apiPost(
                "/api/users/" + PROXY_USER + "/identities",
                "{\"provider\":\"gitea\",\"scmUsername\":\"" + UPSTREAM_LOGIN + "\"}");
        assertTrue(
                linked.statusCode() < 300,
                "linking the SCM identity should succeed: " + linked.statusCode() + " " + linked.body());

        var granted = stack.apiPost(
                "/api/users/" + PROXY_USER + "/permissions",
                "{\"provider\":\"gitea\",\"target\":\"SLUG\",\"value\":\"/" + ComposeStack.TEST_ORG + "/"
                        + ComposeStack.TEST_REPO + "\",\"matchType\":\"LITERAL\",\"grant\":\"PUSH\"}");
        assertTrue(
                granted.statusCode() < 300,
                "granting push should succeed: " + granted.statusCode() + " " + granted.body());

        String branch = "compose-provisioned-" + UUID.randomUUID().toString().substring(0, 8);
        Path workspace = Files.createTempDirectory("fogwall-provisioned-");
        var git = new Git(workspace);
        Path repo = git.clone(
                stack.proxyUrl("proxy", UPSTREAM_LOGIN, token, ComposeStack.TEST_ORG, ComposeStack.TEST_REPO),
                "provisioned");
        git.branch(repo, branch);
        git.commit(
                repo, "provisioned.txt", "linked at runtime " + Instant.now() + "\n", "feat: from a linked identity");

        Cli.Result push = git.push(repo);

        assertFalse(
                push.mentions("Identity Not Linked"),
                "the token's login should have resolved through the identity the API linked, not been rejected as"
                        + " unknown. Output:\n" + push.output());

        JsonNode record = recordFor(branch);
        assertNotNull(record, "fogwall should have recorded the push for " + branch);
        assertEquals(
                PROXY_USER,
                record.path("resolvedUser").asText(),
                "the push should resolve to the provisioned user, which exists only in the database");
    }

    private JsonNode recordFor(String branch) throws Exception {
        JsonNode records =
                new JsonMapper().readTree(stack.api("/api/push?limit=50").body());
        for (JsonNode record : records) {
            if (record.path("branch").asText().endsWith(branch)) {
                return record;
            }
        }
        return null;
    }
}
