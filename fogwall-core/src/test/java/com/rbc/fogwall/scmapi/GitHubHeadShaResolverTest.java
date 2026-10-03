package com.rbc.fogwall.scmapi;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.provider.GitHubProvider;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exercises {@link GitHubHeadShaResolver} against a local stub GraphQL endpoint that answers each request with the next
 * queued response. The head is resolved by GitHub's own {@code Ref.compare} against the base branch, so each case
 * asserts the base ref and head ref the compare query carries, and that anything GitHub cannot resolve is refused.
 */
class GitHubHeadShaResolverTest {

    private static final JsonMapper MAPPER = new JsonMapper();
    private static final OwnerRepo BASE = new OwnerRepo("upstream-owner", "widgets");

    private record Stub(int status, String body) {}

    private HttpServer server;
    private GitHubProvider provider;
    private final Deque<Stub> responses = new ArrayDeque<>();
    private final List<JsonNode> requests = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/graphql", exchange -> {
            requests.add(MAPPER.readTree(exchange.getRequestBody().readAllBytes()));
            Stub stub = responses.isEmpty() ? new Stub(500, "{}") : responses.poll();
            byte[] response = stub.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(stub.status(), response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        provider = GitHubProvider.builder()
                .apiUri(URI.create("http://localhost:" + server.getAddress().getPort()))
                .build();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void respond(String body) {
        responses.add(new Stub(200, body));
    }

    private JsonNode variables(int request) {
        return requests.get(request).get("variables");
    }

    private Optional<String> resolve(String headRefName) {
        return new GitHubHeadShaResolver()
                .resolveHeadSha(provider, BASE, Optional.of("main"), headRefName, Optional.empty(), "token");
    }

    private Optional<String> resolveWithRepositoryId(String headRefName, String headRepositoryId) {
        return new GitHubHeadShaResolver()
                .resolveHeadSha(
                        provider, BASE, Optional.of("main"), headRefName, Optional.of(headRepositoryId), "token");
    }

    private static String compared(String oid) {
        return "{\"data\":{\"repository\":{\"ref\":{\"compare\":{\"headTarget\":{\"oid\":\"" + oid + "\"}}}}}}";
    }

    private static String repositoryRef(String login, String oid) {
        return "{\"data\":{\"node\":{\"owner\":{\"login\":\"" + login + "\"},\"ref\":{\"target\":{\"oid\":\"" + oid
                + "\"}}}}}";
    }

    private void assertCompared(int request, String headRef) {
        assertTrue(requests.get(request).get("query").asString().contains("compare(headRef: $headRef)"));
        assertEquals("upstream-owner", variables(request).get("owner").asString());
        assertEquals("widgets", variables(request).get("name").asString());
        assertEquals("refs/heads/main", variables(request).get("baseRef").asString());
        assertEquals(headRef, variables(request).get("headRef").asString());
    }

    @Test
    void renamedFork_resolvesThroughCompare() {
        respond(compared("fork-sha"));

        assertEquals(Optional.of("fork-sha"), resolve("forker:their-branch"));
        assertEquals(1, requests.size());
        assertCompared(0, "forker:their-branch");
    }

    @Test
    void upstreamIntoFork_resolvesThroughCompare() {
        respond(compared("parent-sha"));

        assertEquals(Optional.of("parent-sha"), resolve("origin:main"));
        assertEquals(1, requests.size());
        assertCompared(0, "origin:main");
    }

    @Test
    void sameRepoHeadRef_resolvesThroughCompare() {
        respond(compared("abc123"));

        assertEquals(Optional.of("abc123"), resolve("feature-branch"));
        assertEquals(1, requests.size());
        assertCompared(0, "feature-branch");
    }

    /**
     * An owner may hold several forks of the base, and compare pairs {@code owner:branch} with only one of them, so the
     * branch must come from the exact repository {@code headRepositoryId} names, never from compare.
     */
    @Test
    void headRepositoryId_readsTheBranchFromThatRepository() {
        respond(repositoryRef("forker", "node-sha"));

        assertEquals(Optional.of("node-sha"), resolveWithRepositoryId("forker:their-branch", "R_fork"));
        assertEquals(1, requests.size());
        assertFalse(requests.get(0).get("query").asString().contains("compare"));
        assertEquals("R_fork", variables(0).get("id").asString());
        assertEquals(
                "refs/heads/their-branch", variables(0).get("qualifiedName").asString());
    }

    @Test
    void headRepositoryId_withBareBranch_readsTheBranchFromThatRepository() {
        respond(repositoryRef("forker", "node-sha"));

        assertEquals(Optional.of("node-sha"), resolveWithRepositoryId("their-branch", "R_fork"));
        assertEquals(
                "refs/heads/their-branch", variables(0).get("qualifiedName").asString());
    }

    @Test
    void headRepositoryId_ownedBySomeoneElseThanThePrefix_resolvesEmpty() {
        respond(repositoryRef("other", "other-sha"));

        assertTrue(resolveWithRepositoryId("forker:their-branch", "R_other").isEmpty());
    }

    @Test
    void headRepositoryId_missingBranch_resolvesEmpty() {
        respond("""
                {"data":{"node":{"owner":{"login":"forker"},"ref":null}}}
                """);

        assertTrue(resolveWithRepositoryId("forker:deleted-branch", "R_fork").isEmpty());
    }

    @Test
    void repositorySegmentInHeadRefName_resolvesEmptyWithoutCallingUpstream() {
        assertTrue(resolve("forker:my-widgets:their-branch").isEmpty());
        assertTrue(resolveWithRepositoryId("forker:my-widgets:their-branch", "R_fork")
                .isEmpty());
        assertTrue(requests.isEmpty());
    }

    @Test
    void headRepositoryId_notARepository_resolvesEmpty() {
        respond("""
                {"data":{"node":null}}
                """);

        assertTrue(resolveWithRepositoryId("their-branch", "I_issue").isEmpty());
        assertEquals(1, requests.size());
    }

    @Test
    void notFound_resolvesEmpty() {
        respond("""
                {"data":{"repository":{"ref":{"compare":null}}},
                 "errors":[{"type":"NOT_FOUND","message":"Could not resolve head ref nobody:their-branch"}]}
                """);

        assertTrue(resolve("nobody:their-branch").isEmpty());
        assertCompared(0, "nobody:their-branch");
    }

    @Test
    void nullCompare_resolvesEmpty() {
        respond("""
                {"data":{"repository":{"ref":{"compare":null}}}}
                """);

        assertTrue(resolve("forker:deleted-branch").isEmpty());
    }

    @Test
    void missingBaseRef_resolvesEmpty() {
        respond("""
                {"data":{"repository":{"ref":null}}}
                """);

        assertTrue(resolve("forker:their-branch").isEmpty());
    }

    @Test
    void errorsAlongsideAHeadTarget_resolvesEmpty() {
        respond("""
                {"data":{"repository":{"ref":{"compare":{"headTarget":{"oid":"partial-sha"}}}}},
                 "errors":[{"type":"FORBIDDEN","message":"partial"}]}
                """);

        assertTrue(resolve("forker:their-branch").isEmpty());
    }

    @Test
    void missingBaseRefName_resolvesEmptyWithoutCallingUpstream() {
        Optional<String> sha = new GitHubHeadShaResolver()
                .resolveHeadSha(provider, BASE, Optional.empty(), "forker:their-branch", Optional.empty(), "token");

        assertTrue(sha.isEmpty());
        assertTrue(requests.isEmpty());
    }

    @Test
    void qualifiedBaseRefName_isComparedOnce() {
        respond(compared("fork-sha"));

        Optional<String> sha = new GitHubHeadShaResolver()
                .resolveHeadSha(
                        provider,
                        BASE,
                        Optional.of("refs/heads/main"),
                        "forker:their-branch",
                        Optional.empty(),
                        "token");

        assertEquals(Optional.of("fork-sha"), sha);
        assertCompared(0, "forker:their-branch");
    }

    @Test
    void upstreamFailure_resolvesEmpty() {
        responses.add(new Stub(502, "bad gateway"));

        assertTrue(resolve("forker:their-branch").isEmpty());
        assertEquals(1, requests.size());
    }

    @Test
    void malformedResponse_resolvesEmpty() {
        respond("not json");

        assertTrue(resolve("feature-branch").isEmpty());
    }

    @Test
    void resolvePullRequestHeadSha_readsNodeQueryDirectly() {
        respond("""
                {"data":{"node":{"headRefOid":"merge-time-sha"}}}
                """);

        Optional<String> sha = new GitHubHeadShaResolver().resolvePullRequestHeadSha(provider, "PR_kwDO1", "token");

        assertEquals(Optional.of("merge-time-sha"), sha);
        assertEquals("PR_kwDO1", variables(0).get("id").asString());
    }
}
