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
 * The SCM API listener against the packaged stack, and the one control that only this path can prove:
 * {@code require-validated-head}.
 *
 * <p>A pull request opened through fogwall's listener crosses TLS, the Gitea REST dialect and the audit path: a branch
 * pushed through the proxy, then proposed through the API, both decided by the same gateway.
 *
 * <p>The negative case: a branch pushed straight to Gitea is a head commit fogwall never saw, and
 * {@code require-validated-head} refuses a pull request for it however valid it looks. Nothing else in any suite covers
 * it.
 */
@Tag("compose")
class ScmApiComposeTest {

    private static ComposeStack stack;
    private static String token;

    @BeforeAll
    static void resolveStack() throws Exception {
        stack = ComposeStack.requireRunning();
        token = stack.scmApiToken(ComposeStack.TEST_USER, ComposeStack.TEST_USER_PASSWORD);
        assumeTrue(
                stack.scmApiReachable(),
                "no SCM API listener on " + stack.scmApiUrl() + " — run: bash compose.sh --contributions -- up -d"
                        + " (test/make-certs.sh first)");
    }

    @Test
    void branchPushedThroughFogwall_canBeProposed() throws Exception {
        Path workspace = Files.createTempDirectory("fogwall-scmapi-");
        String branch = "compose-scmapi-" + UUID.randomUUID().toString().substring(0, 8);

        pushThroughFogwall(workspace, branch);

        var response = stack.scmApiPost(
                "/api/v1/repos/" + ComposeStack.TEST_ORG + "/" + ComposeStack.TEST_REPO + "/pulls",
                token,
                pullRequestBody(branch, "A contribution fogwall carried end to end"));

        assertTrue(
                response.statusCode() < 300,
                "a pull request for a branch fogwall pushed should be allowed: " + response.statusCode() + " "
                        + response.body());
    }

    @Test
    void branchPushedBehindFogwall_isRefused() throws Exception {
        Path workspace = Files.createTempDirectory("fogwall-scmapi-direct-");
        String branch = "compose-direct-" + UUID.randomUUID().toString().substring(0, 8);

        // Straight to Gitea, bypassing the gateway entirely — the head commit fogwall has no record of.
        var git = new Git(workspace);
        Path repo = git.clone(directGiteaUrl(), "direct");
        git.branch(repo, branch);
        git.commit(repo, "direct.txt", "pushed behind fogwall at " + Instant.now() + "\n", "feat: bypass the gateway");
        assertTrue(git.push(repo).succeeded(), "the setup push should reach Gitea directly");

        var response = stack.scmApiPost(
                "/api/v1/repos/" + ComposeStack.TEST_ORG + "/" + ComposeStack.TEST_REPO + "/pulls",
                token,
                pullRequestBody(branch, "A contribution fogwall never saw"));

        assertTrue(
                response.statusCode() >= 400,
                "require-validated-head should refuse a pull request whose head fogwall has no push record for, but"
                        + " the listener answered " + response.statusCode() + " " + response.body());
    }

    /** Pushes a branch through the proxy and gets it past the review hold, so Gitea actually has it. */
    private void pushThroughFogwall(Path workspace, String branch) throws Exception {
        var git = new Git(workspace);
        Path repo = git.clone(
                stack.proxyUrl("proxy", ComposeStack.TEST_USER, token, ComposeStack.TEST_ORG, ComposeStack.TEST_REPO),
                "through");
        git.branch(repo, branch);
        git.commit(repo, "through.txt", "through fogwall at " + Instant.now() + "\n", "feat: a contribution");

        // Under approval-mode: ui a clean push is held for review; approve it so Gitea actually receives the branch.
        assertFalse(git.push(repo).succeeded(), "the first push should be held for review");
        JsonNode record = pendingFor(branch);
        assertNotNull(record, "fogwall should have recorded the held push for " + branch);
        var approval = stack.apiPost("/api/push/" + record.get("id").asText() + "/authorise", "{}");
        assertTrue(approval.statusCode() < 300, "approving should be accepted: " + approval.body());

        assertTrue(git.push(repo).succeeded(), "the approved push should reach Gitea");
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

    private String directGiteaUrl() {
        return stack.giteaUrl().replace("http://", "http://" + ComposeStack.TEST_USER + ":" + token + "@") + "/"
                + ComposeStack.TEST_ORG + "/" + ComposeStack.TEST_REPO + ".git";
    }

    private static String pullRequestBody(String branch, String title) {
        return "{\"head\":\"" + branch + "\",\"base\":\"main\",\"title\":\"" + title + "\"}";
    }
}
