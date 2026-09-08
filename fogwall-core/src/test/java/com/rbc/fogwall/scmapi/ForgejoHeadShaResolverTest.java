package com.rbc.fogwall.scmapi;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.provider.ForgejoProvider;
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
 * Exercises {@link ForgejoHeadShaResolver} against a local stub of Gitea/Forgejo's {@code GET
 * /repos/:owner/:repo/branches/:branch}, covering both the same-repo and {@code owner:branch} fork-encoded shapes of
 * {@code head}.
 */
class ForgejoHeadShaResolverTest {

    private HttpServer server;
    private ForgejoProvider provider;
    private String responseBody;
    private int responseStatus = 200;
    private final List<String> requestedPaths = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/v1/repos", exchange -> {
            requestedPaths.add(exchange.getRequestURI().getPath());
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus, response.length);
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

    @Test
    void sameRepoHead_resolvesAgainstBaseRepo() {
        responseBody = """
                {"name":"feature","commit":{"id":"same-repo-sha"}}
                """;

        Optional<String> sha = new ForgejoHeadShaResolver()
                .resolveHeadSha(provider, new OwnerRepo("acme", "widgets"), "feature", "token");

        assertEquals(Optional.of("same-repo-sha"), sha);
        assertEquals("/api/v1/repos/acme/widgets/branches/feature", requestedPaths.get(0));
    }

    @Test
    void forkEncodedHead_resolvesAgainstForkOwner_sameRepoName() {
        responseBody = """
                {"name":"their-branch","commit":{"id":"fork-sha"}}
                """;

        Optional<String> sha = new ForgejoHeadShaResolver()
                .resolveHeadSha(provider, new OwnerRepo("upstream-owner", "widgets"), "forker:their-branch", "token");

        assertEquals(Optional.of("fork-sha"), sha);
        assertEquals("/api/v1/repos/forker/widgets/branches/their-branch", requestedPaths.get(0));
    }

    @Test
    void notFound_resolvesEmpty() {
        responseStatus = 404;
        responseBody = "{\"message\":\"branch does not exist\"}";

        Optional<String> sha = new ForgejoHeadShaResolver()
                .resolveHeadSha(provider, new OwnerRepo("acme", "widgets"), "missing", "token");

        assertTrue(sha.isEmpty());
    }
}
