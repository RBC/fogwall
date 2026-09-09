package com.rbc.fogwall.dashboard.issues;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rbc.fogwall.provider.BitbucketProvider;
import com.rbc.fogwall.provider.ForgejoProvider;
import com.rbc.fogwall.provider.GitHubProvider;
import com.rbc.fogwall.provider.GitLabProvider;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the per-dialect issue request construction against a captured mock upstream — the outbound HTTP path is
 * otherwise only exercised by live testing. Asserts method, path, body and the bearer credential each dialect sends.
 */
class DashboardIssueClientTest {

    private HttpServer server;
    private String base;
    private final DashboardIssueClient client = new DashboardIssueClient();

    // Captured from the most recent request.
    private volatile String method;
    private volatile String path;
    private volatile String body;
    private volatile String auth;
    // Response the mock returns.
    private volatile int status = 201;
    private static final String RESPONSE =
            "{\"number\":42,\"iid\":42,\"html_url\":\"https://h/42\",\"web_url\":\"https://w/42\"}";
    private volatile String response = RESPONSE;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            method = exchange.getRequestMethod();
            path = exchange.getRequestURI().getRawPath();
            auth = exchange.getRequestHeaders().getFirst("Authorization");
            body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] out = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        base = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private GitHubProvider github() {
        return GitHubProvider.builder().name("github").apiUri(URI.create(base)).build();
    }

    private GitLabProvider gitlab() {
        return GitLabProvider.builder().name("gitlab").uri(URI.create(base)).build();
    }

    private ForgejoProvider forgejo() {
        return ForgejoProvider.builder().name("forgejo").uri(URI.create(base)).build();
    }

    @Test
    void github_create_postsToRestIssuesWithBearer() {
        var result = client.createIssue(github(), "octo", "hello", "Title", "Body", "tok");

        assertEquals("POST", method);
        assertEquals("/repos/octo/hello/issues", path);
        assertEquals("Bearer tok", auth);
        assertTrue(body.contains("\"title\":\"Title\""), body);
        assertTrue(body.contains("\"body\":\"Body\""), body);
        assertEquals(42, result.number());
        assertEquals("https://h/42", result.url());
    }

    @Test
    void gitlab_create_usesEncodedProjectAndDescriptionField() {
        var result = client.createIssue(gitlab(), "grp", "proj", "Title", "Desc", "tok");

        assertEquals("POST", method);
        assertEquals("/api/v4/projects/grp%2Fproj/issues", path);
        assertTrue(body.contains("\"description\":\"Desc\""), body);
        assertEquals(42, result.number()); // iid
        assertEquals("https://w/42", result.url()); // web_url
    }

    @Test
    void forgejo_create_postsToV1Repos() {
        client.createIssue(forgejo(), "org", "repo", "Title", "Body", "tok");

        assertEquals("POST", method);
        assertEquals("/api/v1/repos/org/repo/issues", path);
    }

    @Test
    void github_edit_patchesOnlyPresentFields() {
        client.editIssue(github(), "octo", "hello", 5, "New", null, "tok");

        assertEquals("PATCH", method);
        assertEquals("/repos/octo/hello/issues/5", path);
        assertTrue(body.contains("\"title\":\"New\""), body);
        assertTrue(!body.contains("\"body\""), body); // null field omitted
    }

    @Test
    void github_comment_postsToComments() {
        client.comment(github(), "octo", "hello", 5, "a comment", "tok");

        assertEquals("POST", method);
        assertEquals("/repos/octo/hello/issues/5/comments", path);
        assertTrue(body.contains("\"body\":\"a comment\""), body);
    }

    @Test
    void gitlab_close_putsStateEvent() {
        client.setState(gitlab(), "grp", "proj", 5, true, "tok");

        assertEquals("PUT", method);
        assertEquals("/api/v4/projects/grp%2Fproj/issues/5", path);
        assertTrue(body.contains("\"state_event\":\"close\""), body);
    }

    @Test
    void github_close_patchesStateClosed() {
        client.setState(github(), "octo", "hello", 5, true, "tok");

        assertEquals("PATCH", method);
        assertTrue(body.contains("\"state\":\"closed\""), body);
    }

    @Test
    void gitlab_edit_putsPresentFieldsWithDescription() {
        client.editIssue(gitlab(), "grp", "proj", 5, null, "New body", "tok");

        assertEquals("PUT", method);
        assertEquals("/api/v4/projects/grp%2Fproj/issues/5", path);
        assertTrue(body.contains("\"description\":\"New body\""), body);
        assertTrue(!body.contains("\"title\""), body); // null title omitted
    }

    @Test
    void forgejo_edit_patchesIssue() {
        client.editIssue(forgejo(), "org", "repo", 5, "New", "Body", "tok");

        assertEquals("PATCH", method);
        assertEquals("/api/v1/repos/org/repo/issues/5", path);
        assertTrue(body.contains("\"title\":\"New\""), body);
    }

    @Test
    void gitlab_comment_postsToNotesAndReadsWebUrl() {
        var result = client.comment(gitlab(), "grp", "proj", 5, "a note", "tok");

        assertEquals("POST", method);
        assertEquals("/api/v4/projects/grp%2Fproj/issues/5/notes", path);
        assertTrue(body.contains("\"body\":\"a note\""), body);
        assertEquals(5, result.number());
        assertEquals("https://w/42", result.url()); // web_url, not html_url
    }

    @Test
    void forgejo_comment_postsToComments() {
        client.comment(forgejo(), "org", "repo", 5, "a comment", "tok");

        assertEquals("POST", method);
        assertEquals("/api/v1/repos/org/repo/issues/5/comments", path);
    }

    @Test
    void gitlab_reopen_putsStateEventReopen() {
        client.setState(gitlab(), "grp", "proj", 5, false, "tok");

        assertEquals("PUT", method);
        assertTrue(body.contains("\"state_event\":\"reopen\""), body);
    }

    @Test
    void forgejo_close_patchesStateClosed() {
        client.setState(forgejo(), "org", "repo", 5, true, "tok");

        assertEquals("PATCH", method);
        assertEquals("/api/v1/repos/org/repo/issues/5", path);
        assertTrue(body.contains("\"state\":\"closed\""), body);
    }

    @Test
    void github_getIssue_readsTitleBodyStateWithGet() {
        response = "{\"number\":7,\"html_url\":\"https://h/7\",\"title\":\"T\",\"body\":\"B\",\"state\":\"closed\"}";

        var d = client.getIssue(github(), "octo", "hello", 7, "tok");

        assertEquals("GET", method);
        assertEquals("/repos/octo/hello/issues/7", path);
        assertEquals(7, d.number());
        assertEquals("T", d.title());
        assertEquals("B", d.body());
        assertEquals("closed", d.state());
        assertEquals("https://h/7", d.url());
    }

    @Test
    void gitlab_getIssue_usesDescriptionAndNormalizesOpened() {
        response = "{\"iid\":7,\"web_url\":\"https://w/7\",\"title\":\"T\",\"description\":\"D\",\"state\":\"opened\"}";

        var d = client.getIssue(gitlab(), "grp", "proj", 7, "tok");

        assertEquals("GET", method);
        assertEquals("/api/v4/projects/grp%2Fproj/issues/7", path);
        assertEquals("D", d.body()); // description, not body
        assertEquals("open", d.state()); // GitLab "opened" normalized to "open"
        assertEquals("https://w/7", d.url());
    }

    @Test
    void forgejo_getIssue_getsFromV1Repos() {
        response = "{\"number\":7,\"html_url\":\"https://h/7\",\"title\":\"T\",\"body\":\"B\",\"state\":\"open\"}";

        var d = client.getIssue(forgejo(), "org", "repo", 7, "tok");

        assertEquals("GET", method);
        assertEquals("/api/v1/repos/org/repo/issues/7", path);
        assertEquals("open", d.state());
    }

    @Test
    void upstreamError_throwsWithStatus() {
        status = 403;

        var ex = assertThrows(
                DashboardIssueClient.IssueApiException.class,
                () -> client.createIssue(github(), "octo", "hello", "t", "b", "tok"));
        assertEquals(403, ex.upstreamStatus());
    }

    @Test
    void unsupportedProvider_throwsStatusZero() {
        var bitbucket =
                BitbucketProvider.builder().name("bb").uri(URI.create(base)).build();

        var ex = assertThrows(
                DashboardIssueClient.IssueApiException.class,
                () -> client.createIssue(bitbucket, "o", "r", "t", "b", "tok"));
        assertEquals(0, ex.upstreamStatus());
    }

    @Test
    void unreachableProvider_throwsStatusZeroWithCause() {
        // Point at a port nothing is listening on so the request fails to connect.
        var dead = GitHubProvider.builder()
                .name("github")
                .apiUri(URI.create("http://localhost:1"))
                .build();

        var ex = assertThrows(
                DashboardIssueClient.IssueApiException.class,
                () -> client.createIssue(dead, "octo", "hello", "t", "b", "tok"));
        assertEquals(0, ex.upstreamStatus());
    }
}
