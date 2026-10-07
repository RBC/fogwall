package com.rbc.fogwall.scmapi;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.provider.ForgejoProvider;
import com.rbc.fogwall.testing.MutableClock;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link ForgejoHeadShaResolver} against a local stub of the Gitea/Forgejo API that answers by request path
 * (404 for anything unstubbed), asserting the exact requests each way of identifying the head repository makes: same
 * repo, the base's own parent, the fork list of a small base, the same-name fork and bounded owner search of a large
 * base, and a cached fork re-verified by ID.
 */
class ForgejoHeadShaResolverTest {

    private static final OwnerRepo BASE = new OwnerRepo("upstream-owner", "widgets");
    private static final String BASE_FULL_NAME = "upstream-owner/widgets";
    private static final String API = "/api/v1";
    private static final String FORK_LIST =
            "/repos/upstream-owner/widgets/forks?limit=" + ForgejoHeadShaResolver.FORK_PAGE_SIZE + "&page=1";
    private static final int LARGE = ForgejoHeadShaResolver.FORK_PAGE_SIZE + 1;

    private record Stub(int status, String body) {}

    private HttpServer server;
    private ForgejoProvider provider;
    private final Map<String, Stub> routes = new HashMap<>();
    private final List<String> requested = new ArrayList<>();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-01T12:00:00Z"));
    private final ForkLocationCache cache = new ForkLocationCache(clock, ForkLocationCache.MAX_ENTRIES);
    private final ForgejoHeadShaResolver resolver = new ForgejoHeadShaResolver(cache);

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(API, exchange -> {
            URI uri = exchange.getRequestURI();
            String key = uri.getPath() + (uri.getQuery() == null ? "" : "?" + uri.getQuery());
            requested.add(key.substring(API.length()));
            Stub stub = routes.getOrDefault(key, new Stub(404, "{\"message\":\"not found\"}"));
            byte[] response = stub.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(stub.status(), response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        provider = ForgejoProvider.builder()
                .name("forgejo")
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

    private static String repo(long id, String owner, String name, String parentFullName) {
        String parent = parentFullName == null ? "null" : "{\"full_name\":\"" + parentFullName + "\"}";
        return "{\"id\":" + id + ",\"name\":\"" + name + "\",\"full_name\":\"" + owner + "/" + name
                + "\",\"owner\":{\"login\":\"" + owner + "\"},\"fork\":" + (parentFullName != null) + ",\"parent\":"
                + parent + "}";
    }

    private void routeBase(int forksCount) {
        route(
                "/repos/upstream-owner/widgets",
                "{\"id\":1,\"name\":\"widgets\",\"full_name\":\"upstream-owner/widgets\","
                        + "\"owner\":{\"login\":\"upstream-owner\"},\"fork\":false,\"parent\":null,\"forks_count\":"
                        + forksCount + "}");
    }

    private static String branch(String sha) {
        return "{\"name\":\"b\",\"commit\":{\"id\":\"" + sha + "\"}}";
    }

    private Optional<String> resolve(String head) {
        return resolver.resolveHeadSha(provider, BASE, head, "token");
    }

    private OptionalLong cachedFork(String headOwner) {
        return cache.get(ForkLocationCache.Key.of(provider.getProviderId(), BASE, headOwner));
    }

    /** A small base whose fork list holds forker's renamed fork, id 7; resolved once to seed the cache. */
    private void seedRenamedForkOnSmallBase() {
        routeBase(2);
        route(
                FORK_LIST,
                "[" + repo(6, "someone", "widgets", BASE_FULL_NAME) + ","
                        + repo(7, "forker", "upstream-owner-widgets", BASE_FULL_NAME) + "]");
        route("/repos/forker/upstream-owner-widgets/branches/their-branch", branch("renamed-sha"));
        assertEquals(Optional.of("renamed-sha"), resolve("forker:their-branch"));
        requested.clear();
    }

    @Test
    void sameRepoHead_resolvesAgainstBaseRepo() {
        route("/repos/upstream-owner/widgets/branches/feature", branch("same-repo-sha"));

        assertEquals(Optional.of("same-repo-sha"), resolve("feature"));
        assertEquals(List.of("/repos/upstream-owner/widgets/branches/feature"), requested);
    }

    @Test
    void renamedForkOfSmallBase_isFoundInTheForkList() {
        routeBase(2);
        route(
                FORK_LIST,
                "[" + repo(6, "someone", "widgets", BASE_FULL_NAME) + ","
                        + repo(7, "forker", "upstream-owner-widgets", BASE_FULL_NAME) + "]");
        route("/repos/forker/upstream-owner-widgets/branches/their-branch", branch("renamed-sha"));

        assertEquals(Optional.of("renamed-sha"), resolve("forker:their-branch"));
        assertEquals(
                List.of(
                        "/repos/upstream-owner/widgets",
                        FORK_LIST,
                        "/repos/forker/upstream-owner-widgets/branches/their-branch"),
                requested);
        assertEquals(OptionalLong.of(7), cachedFork("forker"));
    }

    @Test
    void smallBase_withNoForkOwnedByHeadOwner_isRefusedWithoutSearching() {
        routeBase(1);
        route(FORK_LIST, "[" + repo(6, "someone", "widgets", BASE_FULL_NAME) + "]");
        route("/repos/forker/widgets", repo(9, "forker", "widgets", BASE_FULL_NAME));

        assertTrue(resolve("forker:their-branch").isEmpty());
        assertEquals(List.of("/repos/upstream-owner/widgets", FORK_LIST), requested);
        assertTrue(cachedFork("forker").isEmpty());
    }

    @Test
    void smallBase_forkListShorterThanForkCount_fallsThroughToTheSameNameFork() {
        routeBase(3);
        route(FORK_LIST, "[" + repo(6, "someone", "widgets", BASE_FULL_NAME) + "]");
        route("/repos/forker/widgets", repo(9, "forker", "widgets", BASE_FULL_NAME));
        route("/repos/forker/widgets/branches/their-branch", branch("fork-sha"));

        assertEquals(Optional.of("fork-sha"), resolve("forker:their-branch"));
        assertEquals(
                List.of(
                        "/repos/upstream-owner/widgets",
                        FORK_LIST,
                        "/repos/forker/widgets",
                        "/repos/forker/widgets/branches/their-branch"),
                requested);
    }

    @Test
    void sameNameForkOfLargeBase_isFoundByName() {
        routeBase(LARGE);
        route("/repos/forker/widgets", repo(9, "forker", "widgets", BASE_FULL_NAME));
        route("/repos/forker/widgets/branches/their-branch", branch("fork-sha"));

        assertEquals(Optional.of("fork-sha"), resolve("forker:their-branch"));
        assertEquals(
                List.of(
                        "/repos/upstream-owner/widgets",
                        "/repos/forker/widgets",
                        "/repos/forker/widgets/branches/their-branch"),
                requested);
        assertEquals(OptionalLong.of(9), cachedFork("forker"));
    }

    @Test
    void renamedForkOfLargeBase_isFoundInTheOwnerSearch() {
        routeBase(LARGE);
        route("/repos/forker/widgets", repo(8, "forker", "widgets", null));
        route("/repos/forker/widgets/branches/their-branch", branch("non-fork-sha"));
        route("/users/forker", "{\"id\":42,\"login\":\"forker\"}");
        route(
                searchPath(42, 1),
                "{\"ok\":true,\"data\":[" + repo(5, "forker", "unrelated", "someone/else") + ","
                        + repo(7, "forker", "upstream-owner-widgets", BASE_FULL_NAME) + "]}");
        route("/repos/forker/upstream-owner-widgets/branches/their-branch", branch("renamed-sha"));

        assertEquals(Optional.of("renamed-sha"), resolve("forker:their-branch"));
        assertEquals(
                List.of(
                        "/repos/upstream-owner/widgets",
                        "/repos/forker/widgets",
                        "/users/forker",
                        searchPath(42, 1),
                        "/repos/forker/upstream-owner-widgets/branches/their-branch"),
                requested);
        assertEquals(OptionalLong.of(7), cachedFork("forker"));
    }

    @Test
    void sameNameRepositoryOwnedBySomeoneElse_isNotUsed() {
        routeBase(LARGE);
        route("/repos/forker/widgets", repo(9, "renamed-owner", "widgets", BASE_FULL_NAME));
        route("/users/forker", "{\"id\":42,\"login\":\"forker\"}");
        route(searchPath(42, 1), "{\"ok\":true,\"data\":[]}");

        assertTrue(resolve("forker:their-branch").isEmpty());
        assertFalse(requested.contains("/repos/forker/widgets/branches/their-branch"));
    }

    @Test
    void largeBase_withNoForkOfBase_isRefused() {
        routeBase(LARGE);
        route("/repos/forker/widgets", repo(8, "forker", "widgets", null));
        route("/users/forker", "{\"id\":42,\"login\":\"forker\"}");
        route(searchPath(42, 1), "{\"ok\":true,\"data\":[" + repo(5, "forker", "unrelated", "someone/else") + "]}");
        route(searchPath(42, 2), "{\"ok\":true,\"data\":[]}");

        assertTrue(resolve("forker:their-branch").isEmpty());
        assertEquals(
                List.of(
                        "/repos/upstream-owner/widgets",
                        "/repos/forker/widgets",
                        "/users/forker",
                        searchPath(42, 1),
                        searchPath(42, 2)),
                requested);
        assertTrue(cachedFork("forker").isEmpty());
    }

    @Test
    void ownerForkSearch_stopsAtThePageCap() {
        routeBase(LARGE);
        route("/users/forker", "{\"id\":42,\"login\":\"forker\"}");
        for (int page = 1; page <= ForgejoHeadShaResolver.MAX_FORK_PAGES; page++) {
            route(
                    searchPath(42, page),
                    "{\"ok\":true,\"data\":[" + repo(page, "forker", "x" + page, "someone/else") + "]}");
        }
        int beyondCap = ForgejoHeadShaResolver.MAX_FORK_PAGES + 1;
        route(
                searchPath(42, beyondCap),
                "{\"ok\":true,\"data\":[" + repo(7, "forker", "my-widgets", BASE_FULL_NAME) + "]}");

        assertTrue(resolve("forker:their-branch").isEmpty());
        assertFalse(requested.contains(searchPath(42, beyondCap)));
        assertTrue(requested.contains(searchPath(42, ForgejoHeadShaResolver.MAX_FORK_PAGES)));
    }

    @Test
    void unknownHeadOwner_isRefused() {
        routeBase(LARGE);

        assertTrue(resolve("nobody:their-branch").isEmpty());
        assertEquals(List.of("/repos/upstream-owner/widgets", "/repos/nobody/widgets", "/users/nobody"), requested);
    }

    @Test
    void baseParentOwnedByHeadOwner_resolvesAgainstParentWithoutCaching() {
        route(
                "/repos/upstream-owner/widgets",
                "{\"id\":1,\"name\":\"widgets\",\"owner\":{\"login\":\"upstream-owner\"},\"forks_count\":0,"
                        + "\"fork\":true,\"parent\":{\"id\":3,\"name\":\"gadgets\",\"full_name\":\"origin/gadgets\","
                        + "\"owner\":{\"login\":\"origin\"}}}");
        route("/repos/origin/gadgets/branches/main", branch("parent-sha"));

        assertEquals(Optional.of("parent-sha"), resolve("origin:main"));
        assertEquals(List.of("/repos/upstream-owner/widgets", "/repos/origin/gadgets/branches/main"), requested);
        assertEquals(0, cache.size());
    }

    @Test
    void cachedFork_resolvesInTwoCalls_underItsCurrentName() {
        seedRenamedForkOnSmallBase();
        route("/repositories/7", repo(7, "forker", "renamed-again", BASE_FULL_NAME));
        route("/repos/forker/renamed-again/branches/their-branch", branch("renamed-again-sha"));

        assertEquals(Optional.of("renamed-again-sha"), resolve("Forker:their-branch"));
        assertEquals(List.of("/repositories/7", "/repos/forker/renamed-again/branches/their-branch"), requested);
    }

    @Test
    void cachedFork_withAnotherParent_isReResolvedAndTheEntryRefreshed() {
        seedRenamedForkOnSmallBase();
        route("/repositories/7", repo(7, "forker", "upstream-owner-widgets", "someone/else"));
        route(FORK_LIST, "[" + repo(11, "forker", "new-fork", BASE_FULL_NAME) + "]");
        route("/repos/forker/new-fork/branches/their-branch", branch("new-fork-sha"));
        route("/repositories/11", repo(11, "forker", "new-fork", BASE_FULL_NAME));

        assertEquals(Optional.of("new-fork-sha"), resolve("forker:their-branch"));
        assertEquals(
                List.of(
                        "/repositories/7",
                        "/repos/upstream-owner/widgets",
                        FORK_LIST,
                        "/repos/forker/new-fork/branches/their-branch"),
                requested);
        assertEquals(OptionalLong.of(11), cachedFork("forker"));

        requested.clear();
        assertEquals(Optional.of("new-fork-sha"), resolve("forker:their-branch"));
        assertEquals(List.of("/repositories/11", "/repos/forker/new-fork/branches/their-branch"), requested);
    }

    @Test
    void cachedFork_nowOwnedBySomeoneElse_isNotUsed() {
        seedRenamedForkOnSmallBase();
        route("/repositories/7", repo(7, "new-owner", "upstream-owner-widgets", BASE_FULL_NAME));
        routeBase(1);
        route(FORK_LIST, "[" + repo(7, "new-owner", "upstream-owner-widgets", BASE_FULL_NAME) + "]");

        assertTrue(resolve("forker:their-branch").isEmpty());
        assertEquals(List.of("/repositories/7", "/repos/upstream-owner/widgets", FORK_LIST), requested);
        assertTrue(cachedFork("forker").isEmpty());
    }

    @Test
    void cachedForkGone_isReResolved() {
        seedRenamedForkOnSmallBase();
        route(FORK_LIST, "[]");
        routeBase(0);

        assertTrue(resolve("forker:their-branch").isEmpty());
        assertEquals(List.of("/repositories/7", "/repos/upstream-owner/widgets", FORK_LIST), requested);
        assertTrue(cachedFork("forker").isEmpty());
    }

    @Test
    void cachedFork_expiresAfterTheTtl() {
        seedRenamedForkOnSmallBase();
        route("/repositories/7", repo(7, "forker", "upstream-owner-widgets", BASE_FULL_NAME));
        clock.advance(ForkLocationCache.TTL);

        assertEquals(Optional.of("renamed-sha"), resolve("forker:their-branch"));
        assertEquals(
                List.of(
                        "/repos/upstream-owner/widgets",
                        FORK_LIST,
                        "/repos/forker/upstream-owner-widgets/branches/their-branch"),
                requested);
    }

    @Test
    void cachedFork_upstreamFailure_stopsResolvingAndKeepsTheEntry() {
        seedRenamedForkOnSmallBase();
        routes.put(API + "/repositories/7", new Stub(502, "bad gateway"));

        assertTrue(resolve("forker:their-branch").isEmpty());
        assertEquals(List.of("/repositories/7"), requested);
        assertEquals(OptionalLong.of(7), cachedFork("forker"));
    }

    @Test
    void upstreamFailure_stopsResolvingAndResolvesEmpty() {
        routes.put(API + "/repos/upstream-owner/widgets", new Stub(502, "bad gateway"));

        assertTrue(resolve("forker:their-branch").isEmpty());
        assertEquals(List.of("/repos/upstream-owner/widgets"), requested);
    }

    @Test
    void forkListFailure_stopsResolvingAndResolvesEmpty() {
        routeBase(2);
        routes.put(API + FORK_LIST, new Stub(500, "boom"));

        assertTrue(resolve("forker:their-branch").isEmpty());
        assertEquals(List.of("/repos/upstream-owner/widgets", FORK_LIST), requested);
    }

    @Test
    void baseNotFound_isRefused() {
        assertTrue(resolve("forker:their-branch").isEmpty());
        assertEquals(List.of("/repos/upstream-owner/widgets"), requested);
    }

    @Test
    void notFound_resolvesEmpty() {
        assertTrue(resolve("missing").isEmpty());
    }
}
