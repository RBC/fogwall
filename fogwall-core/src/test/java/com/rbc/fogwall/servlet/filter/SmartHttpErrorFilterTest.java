package com.rbc.fogwall.servlet.filter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.servlet.GitDenialResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jgit.http.server.GitSmartHttpTools;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * git displays a server's message only in particular shapes (see {@link GitDenialResponse}); these tests pin the shape
 * of what reaches the client, with JGit's own error rendering run through the filter.
 */
class SmartHttpErrorFilterTest {

    /** What the client receives: status, headers, body, and whether the response was committed. */
    private static final class ClientResponse {
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        final Map<String, String> headers = new HashMap<>();
        final HttpServletResponse mock = mock(HttpServletResponse.class);
        int status = 200;
        String contentType;
        boolean committed;
        Integer sentError;

        ClientResponse() throws IOException {
            doAnswer(inv -> status = inv.getArgument(0)).when(mock).setStatus(anyInt());
            doAnswer(inv -> contentType = inv.getArgument(0)).when(mock).setContentType(anyString());
            doAnswer(inv -> headers.put(inv.getArgument(0), inv.getArgument(1)))
                    .when(mock)
                    .setHeader(anyString(), anyString());
            when(mock.getHeader(anyString())).thenAnswer(inv -> headers.get(inv.<String>getArgument(0)));
            doAnswer(inv -> committed = true).when(mock).flushBuffer();
            doAnswer(inv -> {
                        sentError = inv.getArgument(0);
                        committed = true;
                        return null;
                    })
                    .when(mock)
                    .sendError(anyInt());
            when(mock.isCommitted()).thenAnswer(inv -> committed);
            when(mock.getOutputStream()).thenReturn(new ServletOutputStream() {
                @Override
                public void write(int b) {
                    body.write(b);
                    committed = true;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener l) {}
            });
        }

        String text() {
            return body.toString(StandardCharsets.UTF_8);
        }
    }

    private static ServletInputStream emptyBody() {
        return new ServletInputStream() {
            @Override
            public int read() {
                return -1;
            }

            @Override
            public boolean isFinished() {
                return true;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener l) {}
        };
    }

    private static HttpServletRequest infoRefsRequest() throws IOException {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("GET");
        when(req.getRequestURI()).thenReturn("/server/github.com/owner/repo.git/info/refs");
        when(req.getParameter("service")).thenReturn("git-upload-pack");
        when(req.getInputStream()).thenReturn(emptyBody());
        return req;
    }

    private static HttpServletRequest receivePackRequest() throws IOException {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getContentType()).thenReturn("application/x-git-receive-pack-request");
        when(req.getRequestURI()).thenReturn("/server/github.com/owner/repo.git/git-receive-pack");
        when(req.getMethod()).thenReturn("POST");
        when(req.getInputStream()).thenReturn(emptyBody());
        return req;
    }

    private static ClientResponse run(HttpServletRequest req, FilterChain chain) throws Exception {
        ClientResponse resp = new ClientResponse();
        new SmartHttpErrorFilter().doFilter(req, resp.mock, chain);
        return resp;
    }

    // ---- discovery (/info/refs) ---- //

    /** JGit writes a resolver's refusal as an ERR packet on the status, which git would not print. */
    @Test
    void infoRefs_jgitErrorKeepsItsStatus_andBecomesText() throws Exception {
        HttpServletRequest req = infoRefsRequest();

        ClientResponse resp = run(
                req, (r, w) -> GitSmartHttpTools.sendError(req, (HttpServletResponse) w, 404, "not found upstream"));

        assertEquals(404, resp.status);
        assertEquals("text/plain; charset=UTF-8", resp.contentType);
        assertEquals("not found upstream\n", resp.text());
    }

    @Test
    void infoRefs_jgit401_carriesAChallenge() throws Exception {
        HttpServletRequest req = infoRefsRequest();

        ClientResponse resp = run(
                req, (r, w) -> GitSmartHttpTools.sendError(req, (HttpServletResponse) w, 401, "credential rejected"));

        assertEquals(401, resp.status);
        assertEquals("Basic realm=\"fogwall\"", resp.headers.get("WWW-Authenticate"));
        assertEquals("credential rejected\n", resp.text());
    }

    /**
     * A filter that refuses discovery with {@code sendError} must end the request there. When the refusal was swallowed
     * instead, the request went on into the GitServlet, which fetched the refused repository and listed its refs.
     */
    @ParameterizedTest
    @ValueSource(ints = {403, 404})
    void infoRefs_sendError_writesTheMessageAndCommits(int status) throws Exception {
        ClientResponse resp = run(
                infoRefsRequest(), (r, w) -> ((HttpServletResponse) w).sendError(status, "Repository access denied"));

        assertTrue(resp.committed);
        assertEquals(status, resp.status);
        assertEquals("Repository access denied\n", resp.text());
    }

    @Test
    void infoRefs_textDenialPassesThroughUntouched() throws Exception {
        HttpServletRequest req = infoRefsRequest();

        ClientResponse resp =
                run(req, (r, w) -> GitDenialResponse.send(req, (HttpServletResponse) w, 403, "not in the allow list"));

        assertTrue(resp.committed);
        assertEquals(403, resp.status);
        assertEquals("not in the allow list\n", resp.text());
    }

    @Test
    void infoRefs_successIsUntouched() throws Exception {
        byte[] advertisement = "001e# service=git-upload-pack\n0000".getBytes(StandardCharsets.UTF_8);

        ClientResponse resp = run(infoRefsRequest(), (r, w) -> {
            var response = (HttpServletResponse) w;
            response.setStatus(200);
            response.setContentType("application/x-git-upload-pack-advertisement");
            response.getOutputStream().write(advertisement);
        });

        assertEquals(200, resp.status);
        assertEquals("application/x-git-upload-pack-advertisement", resp.contentType);
        assertArrayEquals(advertisement, resp.body.toByteArray());
    }

    // ---- fetch and push (upload-pack / receive-pack) ---- //

    @ParameterizedTest
    @ValueSource(ints = {400, 403, 404, 500, 503})
    void pack_errorStatusBecomes200(int statusCode) throws Exception {
        ClientResponse resp = run(receivePackRequest(), (r, w) -> ((HttpServletResponse) w).setStatus(statusCode));

        assertEquals(200, resp.status);
    }

    @Test
    void pack_sendError_writesTheMessageOn200AndCommits() throws Exception {
        ClientResponse resp = run(receivePackRequest(), (r, w) -> ((HttpServletResponse) w).sendError(403, "refused"));

        assertTrue(resp.committed);
        assertEquals(200, resp.status);
        assertTrue(resp.text().contains("refused"), resp.text());
    }

    @Test
    void pack_401PassesThrough() throws Exception {
        ClientResponse resp = run(receivePackRequest(), (r, w) -> ((HttpServletResponse) w).sendError(401));

        assertEquals(401, resp.sentError);
    }

    @Test
    void nonGitRequest_getsTheOriginalResponse() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/api/some-endpoint");
        when(req.getMethod()).thenReturn("GET");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        new SmartHttpErrorFilter().doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
    }
}
