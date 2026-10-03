package com.rbc.fogwall.e2e;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.model.PushQuery;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStatus;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;

/**
 * End-to-end tests for {@code providers.<name>.scm-api.require-validated-head} on the Gitea/Forgejo dialect, through
 * the SCM API listener the production config mounts.
 *
 * <p>Every pull request comes from a fork whose name differs from the upstream's, so the head repository can only be
 * found by its fork relationship. The proxy runs on auto-approve, so a clean push through it is recorded as approved.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ScmApiHeadValidationE2ETest {

    private static final String FORK_NAME = "renamed-fork";
    private static final String BLOCKED_TERM = "internal.corp.example.com";
    private static final String NOT_LET_THROUGH = "has not let through";
    private static final JsonMapper MAPPER = new JsonMapper();

    private static GiteaContainer gitea;
    private static JettyProxyFixture proxy;
    private static String token;
    private static int scmApiPort;
    private static Path tempDir;
    private static HttpClient client;

    @BeforeAll
    static void startInfrastructure() throws Exception {
        gitea = new GiteaContainer();
        gitea.start();
        gitea.createAdminUser();
        gitea.createTestRepo();
        gitea.createTestUser();
        gitea.forkRepoAsTestUser(GiteaContainer.TEST_ORG, GiteaContainer.TEST_REPO, FORK_NAME);
        token = gitea.generateScmApiToken();

        try (ServerSocket socket = new ServerSocket(0)) {
            scmApiPort = socket.getLocalPort();
        }
        proxy = JettyProxyFixture.scmApi(
                gitea.getBaseUri(),
                scmApiPort,
                List.of(new JettyProxyFixture.TestUser(
                        GiteaContainer.TEST_USER, GiteaContainer.VALID_AUTHOR_EMAIL, GiteaContainer.TEST_USER)),
                BLOCKED_TERM);
        tempDir = Files.createTempDirectory("fogwall-scm-api-head-e2e-");
        client = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stopInfrastructure() throws Exception {
        if (proxy != null) proxy.close();
        if (gitea != null) gitea.stop();
    }

    @Test
    @Order(1)
    void renamedForkBranchPushedThroughFogwall_opensPullRequest() throws Exception {
        GitHelper git = new GitHelper(tempDir);
        Path repo = cloneForkOnBranch(git, "through-fogwall", "through-fogwall");
        git.writeAndStage(repo, "feature.txt", "a change pushed through fogwall");
        git.commit(repo, "feat: add a feature through fogwall");
        String sha = git.headSha(repo);

        git.setRemoteUrl(repo, "origin", proxyForkUrl());
        var push = git.pushWithResult(repo);
        assertTrue(push.succeeded(), "clean push through fogwall should be let through. Output:\n" + push.output());
        assertEquals(List.of(PushStatus.APPROVED), statusesFor(sha));

        var response = openPullRequest("through-fogwall");
        assertEquals(201, response.statusCode(), response.body());
        assertEquals(
                sha, MAPPER.readTree(response.body()).path("head").path("sha").asString());
    }

    @Test
    @Order(2)
    void commitBlockedByFogwallThenPushedDirectly_isRefused() throws Exception {
        GitHelper git = new GitHelper(tempDir);
        Path repo = cloneForkOnBranch(git, "blocked-then-direct", "blocked-then-direct");
        git.writeAndStage(repo, "config.txt", "endpoint=https://" + BLOCKED_TERM + "/api");
        git.commit(repo, "feat: point at the service endpoint");
        String sha = git.headSha(repo);

        git.setRemoteUrl(repo, "origin", proxyForkUrl());
        var blocked = git.pushWithResult(repo);
        assertFalse(blocked.succeeded(), "push adding a blocked term should be refused");
        assertTrue(
                blocked.output().contains(BLOCKED_TERM),
                "refusal should name the blocked content. Output:\n" + blocked.output());
        List<PushStatus> recorded = statusesFor(sha);
        assertFalse(recorded.isEmpty(), "fogwall should have recorded the blocked push");
        assertTrue(
                recorded.stream().noneMatch(s -> s == PushStatus.APPROVED || s == PushStatus.FORWARDED),
                "blocked push should not be recorded as let through: " + recorded);

        git.setRemoteUrl(repo, "origin", directForkUrl());
        var direct = git.pushWithResult(repo);
        assertTrue(direct.succeeded(), "direct push to Gitea should succeed. Output:\n" + direct.output());

        var response = openPullRequest("blocked-then-direct");
        assertEquals(403, response.statusCode(), response.body());
        assertTrue(response.body().contains(NOT_LET_THROUGH), response.body());
    }

    @Test
    @Order(3)
    void commitNeverPushedThroughFogwall_isRefused() throws Exception {
        GitHelper git = new GitHelper(tempDir);
        Path repo = cloneForkOnBranch(git, "direct-only", "direct-only");
        git.writeAndStage(repo, "direct.txt", "a change pushed straight to the fork");
        git.commit(repo, "feat: add a change outside fogwall");
        String sha = git.headSha(repo);

        var direct = git.pushWithResult(repo);
        assertTrue(direct.succeeded(), "direct push to Gitea should succeed. Output:\n" + direct.output());
        assertEquals(List.of(), statusesFor(sha));

        var response = openPullRequest("direct-only");
        assertEquals(403, response.statusCode(), response.body());
        assertTrue(response.body().contains(NOT_LET_THROUGH), response.body());
    }

    /** Clones the fork straight from Gitea and checks out a new branch, with an author fogwall's policy accepts. */
    private static Path cloneForkOnBranch(GitHelper git, String dirName, String branch) throws Exception {
        Path repo = git.clone(directForkUrl(), dirName);
        git.setAuthor(repo, GiteaContainer.VALID_AUTHOR_NAME, GiteaContainer.VALID_AUTHOR_EMAIL);
        git.createAndCheckoutBranch(repo, branch);
        return repo;
    }

    private static List<PushStatus> statusesFor(String sha) {
        return proxy.getPushStore().find(PushQuery.builder().commitTo(sha).build()).stream()
                .map(PushRecord::getStatus)
                .toList();
    }

    private static HttpResponse<String> openPullRequest(String branch) throws Exception {
        String body = MAPPER.writeValueAsString(
                Map.of("head", GiteaContainer.TEST_USER + ":" + branch, "base", "main", "title", "e2e " + branch));
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + scmApiPort + "/api/v1/repos/"
                        + GiteaContainer.TEST_ORG + "/" + GiteaContainer.TEST_REPO + "/pulls"))
                .header("Authorization", "token " + token)
                .header("Content-Type", "application/json")
                .header("User-Agent", "tea/0.15.1")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String credentials() {
        return URLEncoder.encode(GiteaContainer.TEST_USER, StandardCharsets.UTF_8) + ":"
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private static String forkPath() {
        return "/" + GiteaContainer.TEST_USER + "/" + FORK_NAME + ".git";
    }

    private static String proxyForkUrl() {
        return proxy.getProxyBase().replace("http://", "http://" + credentials() + "@") + forkPath();
    }

    private static String directForkUrl() {
        return gitea.getBaseUrl().replace("http://", "http://" + credentials() + "@") + forkPath();
    }
}
