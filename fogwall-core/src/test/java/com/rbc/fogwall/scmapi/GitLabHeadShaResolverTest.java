package com.rbc.fogwall.scmapi;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.provider.GitLabProvider;
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
 * Exercises {@link GitLabHeadShaResolver} against a local stub of GitLab's {@code GET
 * /projects/:path/repository/branches/:branch}.
 */
class GitLabHeadShaResolverTest {

    private HttpServer server;
    private GitLabProvider provider;
    private String responseBody;
    private int responseStatus = 200;
    private final List<String> requestedPaths = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/v4/projects", exchange -> {
            requestedPaths.add(exchange.getRequestURI().getPath());
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        provider = GitLabProvider.builder()
                .apiUri(URI.create("http://localhost:" + server.getAddress().getPort()))
                .build();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void resolvesBranchTipSha() {
        responseBody = """
                {"name":"feature","commit":{"id":"branch-sha"}}
                """;

        Optional<String> sha = new GitLabHeadShaResolver()
                .resolveHeadSha(provider, new OwnerRepo("acme", "widgets"), "feature", "PRIVATE-TOKEN", "secret-token");

        assertEquals(Optional.of("branch-sha"), sha);
        assertEquals("/api/v4/projects/acme/widgets/repository/branches/feature", requestedPaths.get(0));
    }

    @Test
    void notFound_resolvesEmpty() {
        responseStatus = 404;
        responseBody = "{\"message\":\"404 Branch Not Found\"}";

        Optional<String> sha = new GitLabHeadShaResolver()
                .resolveHeadSha(provider, new OwnerRepo("acme", "widgets"), "missing", "PRIVATE-TOKEN", "secret-token");

        assertTrue(sha.isEmpty());
    }
}
