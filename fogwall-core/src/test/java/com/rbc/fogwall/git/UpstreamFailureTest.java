package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Classifies what JGit really throws when a mirror clone meets each upstream answer, from a local HTTP server, so a
 * JGit upgrade that changes its exceptions or messages fails here rather than telling developers the wrong thing.
 */
class UpstreamFailureTest {

    @TempDir
    Path cacheDir;

    private HttpServer upstream;
    private LocalRepositoryCache cache;

    @BeforeEach
    void setUp() throws IOException {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/", exchange -> {
            int status = Integer.parseInt(exchange.getRequestURI().getPath().split("/")[1]);
            if (status == 401) {
                exchange.getResponseHeaders().add("WWW-Authenticate", "Basic realm=\"upstream\"");
            }
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        upstream.start();
        cache = new LocalRepositoryCache(cacheDir, false);
    }

    @AfterEach
    void tearDown() {
        upstream.stop(0);
    }

    private UpstreamFailure cloneFailure(String url) {
        return cloneFailure(url, new UsernamePasswordCredentialsProvider("dev", "token"));
    }

    private UpstreamFailure cloneFailure(String url, CredentialsProvider credentials) {
        Exception failure = assertThrows(Exception.class, () -> cache.getOrClone(url, credentials, null, "dev"));
        return UpstreamFailure.classify(failure);
    }

    private String upstreamUrl(int status) {
        return "http://127.0.0.1:" + upstream.getAddress().getPort() + "/" + status + "/owner/repo.git";
    }

    /** GitHub answers an anonymous request for a private or missing repository with 401, not 404. */
    @Test
    void noCredentialSent_isCredentialRequired() {
        assertInstanceOf(UpstreamFailure.CredentialRequired.class, cloneFailure(upstreamUrl(401), null));
    }

    @Test
    void rejectedCredential_isNotAuthorized() {
        assertInstanceOf(UpstreamFailure.NotAuthorized.class, cloneFailure(upstreamUrl(401)));
    }

    @Test
    void forbidden_isForbidden() {
        assertInstanceOf(UpstreamFailure.Forbidden.class, cloneFailure(upstreamUrl(403)));
    }

    @Test
    void missingRepository_isNotFound() {
        assertInstanceOf(UpstreamFailure.NotFound.class, cloneFailure(upstreamUrl(404)));
    }

    @Test
    void upstreamServerError_isUnavailable() {
        assertInstanceOf(UpstreamFailure.Unavailable.class, cloneFailure(upstreamUrl(500)));
    }

    @Test
    void nothingListening_isUnavailable() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        assertInstanceOf(
                UpstreamFailure.Unavailable.class, cloneFailure("http://127.0.0.1:" + port + "/owner/repo.git"));
    }

    @Test
    void failureWithNoTransportCause_isInternal() {
        assertInstanceOf(UpstreamFailure.Internal.class, UpstreamFailure.classify(new IOException("disk full")));
    }
}
