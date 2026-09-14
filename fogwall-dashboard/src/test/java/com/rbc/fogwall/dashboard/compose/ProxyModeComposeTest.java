package com.rbc.fogwall.dashboard.compose;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The transparent proxy against the packaged stack: one push it should accept, one it should refuse.
 *
 * <p>Which content is refused, and why, is settled by unit tests over the filters and the e2e suite over the assembled
 * chain. What only this layer shows is that the image booted on the configured database, read the shipped config,
 * resolved an identity against a real Gitea and recorded its decision — that a dialect or a driver has not silently
 * stopped working.
 *
 * <p>Under {@code approval-mode: ui} "accept" means held for review, not forwarded: a clean push comes back refused
 * with a record to approve. Reaching that hold is the pass — every check ran and none objected — and approving it
 * forwards it upstream.
 */
@Tag("compose")
class ProxyModeComposeTest {

    private static ComposeStack stack;
    private static String token;

    private Path workspace;

    @BeforeAll
    static void resolveStack() throws Exception {
        stack = ComposeStack.requireRunning();
        token = stack.accessToken(ComposeStack.TEST_USER, ComposeStack.TEST_USER_PASSWORD);
    }

    @BeforeEach
    void createWorkspace() throws Exception {
        workspace = Files.createTempDirectory("fogwall-compose-");
    }

    @Test
    void cleanPush_isHeldForReview_thenForwardedOnceApproved() throws Exception {
        var git = new Git(workspace);
        Path repo = git.clone(pushUrl(), "clean");
        git.commit(repo, "compose.txt", "clean push at " + Instant.now() + "\n", "feat: a push the stack should hold");

        Cli.Result held = git.push(repo);

        assertFalse(
                held.succeeded(), "a clean push should be held for review, not forwarded. Output:\n" + held.output());
        assertTrue(
                held.mentions("View push record"),
                "the hold should point the developer at the record to approve. Output:\n" + held.output());

        JsonNode record = latestPending();
        assertNotNull(record, "fogwall should have recorded the held push");
        assertEquals(
                ComposeStack.TEST_USER,
                record.get("resolvedUser").asText(),
                "the Gitea token should have resolved to the fogwall user the shipped config maps it to");

        var response = stack.apiPost("/api/push/" + record.get("id").asText() + "/authorise", "{}");
        assertTrue(
                response.statusCode() < 300,
                "approving the held push should be accepted: " + response.statusCode() + " " + response.body());

        assertTrue(git.push(repo).succeeded(), "an approved push should reach Gitea when the developer pushes again");
    }

    @Test
    void secretInDiff_isRefused() throws Exception {
        var git = new Git(workspace);
        Path repo = git.clone(pushUrl(), "secret");
        // A PEM header, which gitleaks matches on its own — the payload below is not a key. An unambiguous rule, so
        // this fails when scanning stops working, not when a rule is retuned.
        git.commit(
                repo,
                "deploy-key.pem",
                "-----BEGIN RSA PRIVATE KEY-----\n"
                        + "MIIEowIBAAKCAQEAx7Xn0000000000000000000000000000000000000000000\n"
                        + "-----END RSA PRIVATE KEY-----\n",
                "chore: add a deployment key");

        Cli.Result result = git.push(repo);

        assertFalse(result.succeeded(), "a push carrying a secret should be refused. Output:\n" + result.output());
        assertFalse(
                result.mentions("no secrets detected"),
                "the refusal has to come from secret scanning, not the review hold that refuses clean pushes too."
                        + " Output:\n" + result.output());
    }

    /** The most recent push record still waiting on a reviewer, or null if there is none. */
    private JsonNode latestPending() throws Exception {
        JsonNode records =
                new JsonMapper().readTree(stack.api("/api/push?limit=20").body());
        for (JsonNode record : records) {
            if ("PENDING".equals(record.path("status").asText())) {
                return record;
            }
        }
        return null;
    }

    private String pushUrl() {
        return stack.proxyUrl("proxy", ComposeStack.TEST_USER, token, ComposeStack.TEST_ORG, ComposeStack.TEST_REPO);
    }
}
