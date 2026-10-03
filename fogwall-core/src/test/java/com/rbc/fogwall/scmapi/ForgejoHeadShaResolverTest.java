package com.rbc.fogwall.scmapi;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.provider.ForgejoProvider;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link ForgejoHeadShaResolver} against a local stub of the Gitea/Forgejo API that answers by request path
 * (404 for anything unstubbed), covering every way the head repository is identified: same repo, a same-name fork, the
 * base's own parent, and the bounded search of the head owner's forks.
 */
class ForgejoHeadShaResolverTest {

    private static final OwnerRepo BASE = new OwnerRepo("upstream-owner", "widgets");
    private static final String API = "/api/v1";

    private record Stub(int status, String body) {}

    private HttpServer server;
    private ForgejoProvider provider;
    private final Map<String, Stub> routes = new HashMap<>();
    private final List<String> requested = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(API, exchange -> {
            URI uri = exchange.getRequestURI();
            String key = uri.getPath() + (uri.getQuery() == null ? "" : "?" + uri.getQuery());
            requested.add(key);
            Stub stub = routes.getOrDefault(key, new Stub(404, "{\"message\":\"not found\"}"));
            byte[] response = stub.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(stub.status(), response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        provider = ForgejoProvider.builder()
                .apiUri(URI.create("http://localhost:" + server.getAddress().getPort()))
                .build();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void route(String path, String body) {
        routes.put(API + path, new Stub(200, body));
    }

    private static String searchPath(long uid, int page) {
        return "/repos/search?uid=" + uid + "&exclusive=true&mode=fork&sort=updated&order=desc&limit="
                + ForgejoHeadShaResolver.FORK_PAGE_SIZE + "&page=" + page;
    }

    private static String repo(String owner, String name, String parentFullName) {
        String parent = parentFullName == null ? "null" : "{\"full_name\":\"" + parentFullName + "\"}";
        return "{\"name\":\"" + name + "\",\"owner\":{\"login\":\"" + owner + "\"},\"fork\":" + (parentFullName != null)
                + ",\"parent\":" + parent + "}";
    }

    private static String branch(String sha) {
        return "{\"name\":\"b\",\"commit\":{\"id\":\"" + sha + "\"}}";
    }

    private Optional<String> resolve(String head) {
        return new ForgejoHeadShaResolver().resolveHeadSha(provider, BASE, head, "token");
    }

    @Test
    void sameRepoHead_resolvesAgainstBaseRepo() {
        route("/repos/upstream-owner/widgets/branches/feature", branch("same-repo-sha"));

        assertEquals(Optional.of("same-repo-sha"), resolve("feature"));
        assertEquals(List.of(API + "/repos/upstream-owner/widgets/branches/feature"), requested);
    }

    @Test
    void sameNameForkOfBase_resolvesAgainstIt() {
        route("/repos/forker/widgets", repo("forker", "widgets", "upstream-owner/widgets"));
        route("/repos/forker/widgets/branches/their-branch", branch("fork-sha"));

        assertEquals(Optional.of("fork-sha"), resolve("forker:their-branch"));
        assertEquals(2, requested.size());
    }

    @Test
    void sameNameNonFork_isNotUsed_renamedForkIsFoundInOwnerForks() {
        route("/repos/forker/widgets", repo("forker", "widgets", null));
        route("/repos/forker/widgets/branches/their-branch", branch("non-fork-sha"));
        route("/repos/upstream-owner/widgets", repo("upstream-owner", "widgets", null));
        route("/users/forker", "{\"id\":42,\"login\":\"forker\"}");
        route(
                searchPath(42, 1),
                "{\"ok\":true,\"data\":[" + repo("forker", "unrelated", "someone/else") + ","
                        + repo("forker", "upstream-owner-widgets", "upstream-owner/widgets") + "]}");
        route("/repos/forker/upstream-owner-widgets/branches/their-branch", branch("renamed-sha"));

        assertEquals(Optional.of("renamed-sha"), resolve("forker:their-branch"));
        assertFalse(requested.contains(API + "/repos/forker/widgets/branches/their-branch"));
    }

    @Test
    void sameNameNonFork_withNoForkOfBase_resolvesEmpty() {
        route("/repos/forker/widgets", repo("forker", "widgets", null));
        route("/repos/forker/widgets/branches/their-branch", branch("non-fork-sha"));
        route("/repos/upstream-owner/widgets", repo("upstream-owner", "widgets", null));
        route("/users/forker", "{\"id\":42,\"login\":\"forker\"}");
        route(searchPath(42, 1), "{\"ok\":true,\"data\":[" + repo("forker", "unrelated", "someone/else") + "]}");
        route(searchPath(42, 2), "{\"ok\":true,\"data\":[]}");

        assertTrue(resolve("forker:their-branch").isEmpty());
        assertFalse(requested.contains(API + "/repos/forker/widgets/branches/their-branch"));
    }

    @Test
    void baseParentOwnedByHeadOwner_resolvesAgainstParent() {
        route(
                "/repos/upstream-owner/widgets",
                "{\"name\":\"widgets\",\"owner\":{\"login\":\"upstream-owner\"},"
                        + "\"fork\":true,\"parent\":{\"name\":\"gadgets\",\"full_name\":\"origin/gadgets\","
                        + "\"owner\":{\"login\":\"origin\"}}}");
        route("/repos/origin/gadgets/branches/main", branch("parent-sha"));

        assertEquals(Optional.of("parent-sha"), resolve("origin:main"));
    }

    @Test
    void ownerForkSearch_stopsAtThePageCap() {
        route("/repos/upstream-owner/widgets", repo("upstream-owner", "widgets", null));
        route("/users/forker", "{\"id\":42,\"login\":\"forker\"}");
        for (int page = 1; page <= ForgejoHeadShaResolver.MAX_FORK_PAGES; page++) {
            route(searchPath(42, page), "{\"ok\":true,\"data\":[" + repo("forker", "x" + page, "someone/else") + "]}");
        }
        int beyondCap = ForgejoHeadShaResolver.MAX_FORK_PAGES + 1;
        route(
                searchPath(42, beyondCap),
                "{\"ok\":true,\"data\":[" + repo("forker", "my-widgets", "upstream-owner/widgets") + "]}");

        assertTrue(resolve("forker:their-branch").isEmpty());
        assertFalse(requested.contains(API + searchPath(42, beyondCap)));
        assertTrue(requested.contains(API + searchPath(42, ForgejoHeadShaResolver.MAX_FORK_PAGES)));
    }

    @Test
    void unknownHeadOwner_resolvesEmpty() {
        route("/repos/upstream-owner/widgets", repo("upstream-owner", "widgets", null));

        assertTrue(resolve("nobody:their-branch").isEmpty());
        assertTrue(requested.contains(API + "/users/nobody"));
    }

    @Test
    void upstreamFailure_stopsResolvingAndResolvesEmpty() {
        routes.put(API + "/repos/forker/widgets", new Stub(502, "bad gateway"));

        assertTrue(resolve("forker:their-branch").isEmpty());
        assertEquals(1, requested.size());
    }

    @Test
    void notFound_resolvesEmpty() {
        assertTrue(resolve("missing").isEmpty());
    }
}
