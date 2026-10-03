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
 * queued response, covering every way the head repository is identified: same repo, {@code headRepositoryId}, a
 * same-name fork, the base's own parent, and the bounded search of the head owner's forks.
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
        return new GitHubHeadShaResolver().resolveHeadSha(provider, BASE, headRefName, Optional.empty(), "token");
    }

    private static String ref(String oid) {
        return "{\"data\":{\"repository\":{\"ref\":{\"target\":{\"oid\":\"" + oid + "\"}}}}}";
    }

    private static String candidates(String sameName, String baseParent) {
        return "{\"data\":{\"base\":{\"parent\":" + baseParent + "},\"sameName\":" + sameName + "}}";
    }

    private static String forkPage(boolean hasNextPage, String cursor, String... nodes) {
        return "{\"data\":{\"repositoryOwner\":{\"repositories\":{\"nodes\":[" + String.join(",", nodes)
                + "],\"pageInfo\":{\"hasNextPage\":" + hasNextPage + ",\"endCursor\":\"" + cursor + "\"}}}}}";
    }

    private static String repo(String nameWithOwner, String parent) {
        String parentJson = parent == null ? "null" : "{\"nameWithOwner\":\"" + parent + "\"}";
        return "{\"nameWithOwner\":\"" + nameWithOwner + "\",\"parent\":" + parentJson + "}";
    }

    @Test
    void sameRepoHeadRef_resolvesAgainstBaseRepo() {
        respond(ref("abc123"));

        assertEquals(Optional.of("abc123"), resolve("feature-branch"));
        assertEquals(1, requests.size());
        assertEquals("upstream-owner", variables(0).get("owner").asString());
        assertEquals("widgets", variables(0).get("name").asString());
        assertEquals(
                "refs/heads/feature-branch", variables(0).get("qualifiedName").asString());
    }

    @Test
    void ownerPrefixNamingBaseOwner_resolvesAgainstBaseRepo() {
        respond(ref("abc123"));

        assertEquals(Optional.of("abc123"), resolve("Upstream-Owner:feature-branch"));
        assertEquals(1, requests.size());
        assertEquals("widgets", variables(0).get("name").asString());
    }

    @Test
    void headRepositoryId_resolvesThroughThatRepository() {
        respond("""
                {"data":{"node":{"owner":{"login":"forker"},"ref":{"target":{"oid":"node-sha"}}}}}
                """);

        Optional<String> sha = new GitHubHeadShaResolver()
                .resolveHeadSha(provider, BASE, "forker:their-branch", Optional.of("R_fork"), "token");

        assertEquals(Optional.of("node-sha"), sha);
        assertEquals(1, requests.size());
        assertEquals("R_fork", variables(0).get("id").asString());
        assertEquals(
                "refs/heads/their-branch", variables(0).get("qualifiedName").asString());
    }

    @Test
    void headRepositoryId_ownedBySomeoneElseThanThePrefix_resolvesEmpty() {
        respond("""
                {"data":{"node":{"owner":{"login":"other"},"ref":{"target":{"oid":"node-sha"}}}}}
                """);

        Optional<String> sha = new GitHubHeadShaResolver()
                .resolveHeadSha(provider, BASE, "forker:their-branch", Optional.of("R_other"), "token");

        assertTrue(sha.isEmpty());
    }

    @Test
    void headRepositoryId_notARepository_resolvesEmpty() {
        respond("""
                {"data":{"node":null}}
                """);

        Optional<String> sha = new GitHubHeadShaResolver()
                .resolveHeadSha(provider, BASE, "their-branch", Optional.of("I_issue"), "token");

        assertTrue(sha.isEmpty());
    }

    @Test
    void sameNameForkOfBase_resolvesAgainstIt() {
        respond(candidates(repo("forker/widgets", "upstream-owner/widgets"), "null"));
        respond(ref("fork-sha"));

        assertEquals(Optional.of("fork-sha"), resolve("forker:their-branch"));
        assertEquals("forker", variables(0).get("headOwner").asString());
        assertEquals("forker", variables(1).get("owner").asString());
        assertEquals("widgets", variables(1).get("name").asString());
        assertEquals(
                "refs/heads/their-branch", variables(1).get("qualifiedName").asString());
    }

    @Test
    void sameNameNonFork_isNotUsed_renamedForkIsFoundInOwnerForks() {
        respond(candidates(repo("forker/widgets", null), "null"));
        respond(forkPage(
                false,
                "c1",
                repo("forker/unrelated", "someone/else"),
                repo("forker/upstream-owner-widgets", "upstream-owner/widgets")));
        respond(ref("renamed-sha"));

        assertEquals(Optional.of("renamed-sha"), resolve("forker:their-branch"));
        assertEquals("forker", variables(1).get("owner").asString());
        assertEquals(
                GitHubHeadShaResolver.FORK_PAGE_SIZE, variables(1).get("first").asInt());
        assertFalse(variables(1).has("after"));
        assertEquals("forker", variables(2).get("owner").asString());
        assertEquals("upstream-owner-widgets", variables(2).get("name").asString());
    }

    @Test
    void sameNameNonFork_withNoForkOfBase_resolvesEmpty() {
        respond(candidates(repo("forker/widgets", null), "null"));
        respond(forkPage(false, "c1", repo("forker/unrelated", "someone/else")));

        assertTrue(resolve("forker:their-branch").isEmpty());
        assertEquals(2, requests.size(), "no ref lookup against the same-name non-fork");
    }

    @Test
    void sameNameForkOfAnotherRepo_isNotUsed() {
        respond(candidates(repo("forker/widgets", "someone/widgets"), "null"));
        respond(forkPage(false, "c1"));

        assertTrue(resolve("forker:their-branch").isEmpty());
        assertEquals(2, requests.size());
    }

    @Test
    void baseParentOwnedByHeadOwner_resolvesAgainstParent() {
        respond(candidates("null", "{\"nameWithOwner\":\"origin/gadgets\",\"owner\":{\"login\":\"origin\"}}"));
        respond(ref("parent-sha"));

        assertEquals(Optional.of("parent-sha"), resolve("origin:main"));
        assertEquals("origin", variables(1).get("owner").asString());
        assertEquals("gadgets", variables(1).get("name").asString());
    }

    @Test
    void renamedForkOnALaterPage_followsTheCursor() {
        respond(candidates("null", "null"));
        respond(forkPage(true, "cursor-1", repo("forker/unrelated", "someone/else")));
        respond(forkPage(false, "cursor-2", repo("forker/my-widgets", "upstream-owner/widgets")));
        respond(ref("page-two-sha"));

        assertEquals(Optional.of("page-two-sha"), resolve("forker:their-branch"));
        assertEquals("cursor-1", variables(2).get("after").asString());
        assertEquals("my-widgets", variables(3).get("name").asString());
    }

    @Test
    void ownerForkSearch_stopsAtThePageCap() {
        respond(candidates("null", "null"));
        for (int page = 0; page < GitHubHeadShaResolver.MAX_FORK_PAGES; page++) {
            respond(forkPage(true, "cursor-" + page, repo("forker/unrelated-" + page, "someone/else")));
        }
        respond(forkPage(false, "beyond-cap", repo("forker/my-widgets", "upstream-owner/widgets")));

        assertTrue(resolve("forker:their-branch").isEmpty());
        assertEquals(1 + GitHubHeadShaResolver.MAX_FORK_PAGES, requests.size());
    }

    @Test
    void unknownHeadOwner_resolvesEmpty() {
        respond(candidates("null", "null"));
        respond("""
                {"data":{"repositoryOwner":null}}
                """);

        assertTrue(resolve("nobody:their-branch").isEmpty());
        assertEquals(2, requests.size());
    }

    @Test
    void upstreamFailure_stopsResolvingAndResolvesEmpty() {
        responses.add(new Stub(502, "bad gateway"));

        assertTrue(resolve("forker:their-branch").isEmpty());
        assertEquals(1, requests.size());
    }

    @Test
    void missingRef_resolvesEmpty() {
        respond("""
                {"data":{"repository":{"ref":null}}}
                """);

        assertTrue(resolve("deleted-branch").isEmpty());
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

    @Test
    void malformedResponse_resolvesEmpty() {
        respond("not json");

        assertTrue(resolve("feature-branch").isEmpty());
    }
}
