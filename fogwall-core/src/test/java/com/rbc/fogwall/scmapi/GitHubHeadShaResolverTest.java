package com.rbc.fogwall.scmapi;

import static org.junit.jupiter.api.Assertions.*;

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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exercises {@link GitHubHeadShaResolver} against a local stub GraphQL endpoint, covering both the same-repo and
 * {@code owner:branch} fork-encoded shapes of {@code headRefName}.
 */
class GitHubHeadShaResolverTest {

    private static final JsonMapper MAPPER = new JsonMapper();

    private HttpServer server;
    private GitHubProvider provider;
    private String responseBody;
    private final List<JsonNode> requestedVariables = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/graphql", exchange -> {
            byte[] requestBytes = exchange.getRequestBody().readAllBytes();
            requestedVariables.add(MAPPER.readTree(requestBytes).get("variables"));
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
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
    void sameRepoHeadRef_resolvesAgainstBaseRepo() {
        responseBody = """
                {"data":{"repository":{"ref":{"target":{"oid":"abc123"}}}}}
                """;

        Optional<String> sha = new GitHubHeadShaResolver()
                .resolveHeadSha(provider, new OwnerRepo("acme", "widgets"), "feature-branch", "token");

        assertEquals(Optional.of("abc123"), sha);
        assertEquals("acme", requestedVariables.get(0).get("owner").asString());
        assertEquals("widgets", requestedVariables.get(0).get("name").asString());
        assertEquals(
                "refs/heads/feature-branch",
                requestedVariables.get(0).get("qualifiedName").asString());
    }

    @Test
    void forkEncodedHeadRef_resolvesAgainstForkOwner_sameRepoName() {
        responseBody = """
                {"data":{"repository":{"ref":{"target":{"oid":"fork-sha"}}}}}
                """;

        Optional<String> sha = new GitHubHeadShaResolver()
                .resolveHeadSha(provider, new OwnerRepo("upstream-owner", "widgets"), "forker:their-branch", "token");

        assertEquals(Optional.of("fork-sha"), sha);
        assertEquals("forker", requestedVariables.get(0).get("owner").asString());
        assertEquals("widgets", requestedVariables.get(0).get("name").asString());
        assertEquals(
                "refs/heads/their-branch",
                requestedVariables.get(0).get("qualifiedName").asString());
    }

    @Test
    void missingRef_resolvesEmpty() {
        responseBody = """
                {"data":{"repository":{"ref":null}}}
                """;

        Optional<String> sha = new GitHubHeadShaResolver()
                .resolveHeadSha(provider, new OwnerRepo("acme", "widgets"), "deleted-branch", "token");

        assertTrue(sha.isEmpty());
    }

    @Test
    void malformedResponse_resolvesEmpty() {
        responseBody = "not json";

        Optional<String> sha = new GitHubHeadShaResolver()
                .resolveHeadSha(provider, new OwnerRepo("acme", "widgets"), "feature-branch", "token");

        assertTrue(sha.isEmpty());
    }
}
