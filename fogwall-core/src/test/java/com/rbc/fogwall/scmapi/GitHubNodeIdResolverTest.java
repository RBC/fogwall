package com.rbc.fogwall.scmapi;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.build.BuildInfo;
import com.rbc.fogwall.provider.GitHubProvider;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link GitHubNodeIdResolver} against a local stub GraphQL endpoint, covering the response-parsing and
 * per-request resolution.
 */
class GitHubNodeIdResolverTest {

    private HttpServer server;
    private GitHubProvider provider;
    private String lastResponseBody;
    private final List<String> seenUserAgents = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/graphql", exchange -> {
            seenUserAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
            byte[] response = lastResponseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
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

    @Test
    void resolvesRepositoryShapeResponse() {
        lastResponseBody = """
                {"data":{"node":{"name":"widgets","owner":{"login":"acme"}}}}
                """;

        Optional<OwnerRepo> result =
                new GitHubNodeIdResolver().resolve(provider, new MutationNodeIdRef("R_1", null), "token");

        assertEquals(Optional.of(new OwnerRepo("acme", "widgets")), result);
    }

    /**
     * Repository, issue and pull request node IDs all survive a rename or transfer, so a repeat lookup must see where
     * the repository lives now.
     */
    @Test
    void resolvesEveryRequestUpstream_andSeesAMove() {
        GitHubNodeIdResolver resolver = new GitHubNodeIdResolver();
        respondWith("""
                {"data":{"node":{"repository":{"name":"widgets","owner":{"login":"acme"}}}}}
                """);
        assertEquals(
                Optional.of(new OwnerRepo("acme", "widgets")),
                resolver.resolve(provider, new MutationNodeIdRef("I_1", null), "token"));

        respondWith("""
                {"data":{"node":{"repository":{"name":"widgets-renamed","owner":{"login":"other-org"}}}}}
                """);
        assertEquals(
                Optional.of(new OwnerRepo("other-org", "widgets-renamed")),
                resolver.resolve(provider, new MutationNodeIdRef("I_1", null), "token"));

        assertEquals(2, seenUserAgents.size());
    }

    @Test
    void resolvesIssueShapeResponse_nestedUnderRepository() {
        lastResponseBody = """
                {"data":{"node":{"repository":{"name":"widgets","owner":{"login":"acme"}}}}}
                """;

        Optional<OwnerRepo> result =
                new GitHubNodeIdResolver().resolve(provider, new MutationNodeIdRef("I_1", null), "token");

        assertEquals(Optional.of(new OwnerRepo("acme", "widgets")), result);
    }

    @Test
    void malformedResponse_returnsEmpty() {
        lastResponseBody = "not json";

        Optional<OwnerRepo> result =
                new GitHubNodeIdResolver().resolve(provider, new MutationNodeIdRef("R_1", null), "token");

        assertTrue(result.isEmpty());
    }

    @Test
    void nullNode_returnsEmpty() {
        lastResponseBody = """
                {"data":{"node":null}}
                """;

        Optional<OwnerRepo> result =
                new GitHubNodeIdResolver().resolve(provider, new MutationNodeIdRef("R_1", null), "token");

        assertTrue(result.isEmpty());
    }

    /**
     * fogwall originates this lookup rather than brokering it, so it identifies itself. Presenting the caller's CLI
     * would attribute to that CLI a request it never sent; presenting the HTTP client library's default would name the
     * library and the Java runtime.
     */
    @Test
    void identifiesItselfOnItsOwnLookup() {
        lastResponseBody = """
                {"data":{"node":{"name":"widgets","owner":{"login":"acme"}}}}
                """;

        new GitHubNodeIdResolver().resolve(provider, new MutationNodeIdRef("R_1", null), "token");

        // Pinned to BuildInfo rather than a literal version, so a release bump does not break the assertion.
        assertEquals(List.of("fogwall/" + BuildInfo.get().version()), seenUserAgents);
    }

    /** Sets what the stub upstream answers next; a method so a sequence of answers reads as one. */
    private void respondWith(String body) {
        lastResponseBody = body;
    }
}
