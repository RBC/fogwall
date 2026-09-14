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
 * A contribution made the way a developer makes one: {@code git push} through the proxy, then {@code tea pr create}
 * through the SCM API listener. The real binaries, against the packaged stack.
 *
 * <p>A handwritten request cannot stand in for this. {@code tea pr create} is a sequence — the client resolves the
 * repository and compares branches before it creates anything — and every request in it has to be allowlisted, routed
 * and forwarded. The proxy has broken on that sequence before.
 *
 * <p>CI pins the {@code tea} version, so a failure here is a regression in fogwall, not a client that changed
 * underneath it.
 */
@Tag("compose")
class ScmApiCliComposeTest {

    private static ComposeStack stack;
    private static String token;

    @BeforeAll
    static void resolveStack() throws Exception {
        stack = ComposeStack.requireRunning();
        token = stack.scmApiToken(ComposeStack.TEST_USER, ComposeStack.TEST_USER_PASSWORD);
        assumeTrue(
                stack.scmApiReachable(),
                "no SCM API listener on " + stack.scmApiUrl() + " — run: bash compose.sh --contributions -- up -d");
        assumeTrue(Tea.available(), "tea is not installed; CI pins a version, a developer may not have one");
    }

    @Test
    void teaOpensAPullRequestThroughTheListener() throws Exception {
        Path workspace = Files.createTempDirectory("fogwall-tea-");
        String branch = "compose-tea-" + UUID.randomUUID().toString().substring(0, 8);

        var git = new Git(workspace);
        Path repo = git.clone(
                stack.proxyUrl("proxy", ComposeStack.TEST_USER, token, ComposeStack.TEST_ORG, ComposeStack.TEST_REPO),
                "contribution");
        git.branch(repo, branch);
        git.commit(repo, "tea.txt", "opened with tea at " + Instant.now() + "\n", "feat: a contribution from tea");

        // Under approval-mode: ui a clean push is held for review; approve it so Gitea actually receives the branch.
        assertFalse(git.push(repo).succeeded(), "the first push should be held for review");
        JsonNode record = pendingFor(branch);
        assertNotNull(record, "fogwall should have recorded the held push for " + branch);
        assertTrue(
                stack.apiPost("/api/push/" + record.get("id").asText() + "/authorise", "{}")
                                .statusCode()
                        < 300,
                "approving the held push should be accepted");
        assertTrue(git.push(repo).succeeded(), "the approved push should reach Gitea");

        var tea = new Tea(workspace);
        Cli.Result login = tea.login(stack.scmApiUrl(), token);
        assertTrue(login.succeeded(), "tea should log in against the listener. Output:\n" + login.output());

        Cli.Result created = tea.createPullRequest(
                ComposeStack.TEST_ORG + "/" + ComposeStack.TEST_REPO,
                branch,
                "main",
                "A contribution opened with tea through fogwall");

        assertTrue(
                created.succeeded(),
                "every request tea makes to open a pull request should pass through the listener. Output:\n"
                        + created.output());
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
