package com.rbc.fogwall.servlet.filter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LfsRejectionFilterTest {

    private static HttpServletRequest request(String method, String uri) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn(method);
        when(req.getRequestURI()).thenReturn(uri);
        return req;
    }

    private static ServletOutputStream capturing(ByteArrayOutputStream sink) {
        return new ServletOutputStream() {
            @Override
            public void write(int b) {
                sink.write(b);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener listener) {}
        };
    }

    @Test
    void runsBeforeTheBodyIsBuffered() {
        assertEquals(
                Integer.MIN_VALUE,
                new LfsRejectionFilter().getOrder(),
                "Must precede ParseGitRequestFilter at MIN_VALUE + 1, which buffers the body first");
    }

    @Test
    void batchUploadRequestIsRejected() throws Exception {
        var out = new ByteArrayOutputStream();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getOutputStream()).thenReturn(capturing(out));
        FilterChain chain = mock(FilterChain.class);

        new LfsRejectionFilter()
                .doFilter(request("POST", "/proxy/github.com/acme/app.git/info/lfs/objects/batch"), resp, chain);

        verify(resp).setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
        verify(resp).setContentType("application/vnd.git-lfs+json");
        verify(chain, never()).doFilter(any(), any());
        String body = out.toString(StandardCharsets.UTF_8);
        assertTrue(body.contains("\"message\""), "git-lfs surfaces the message field: " + body);
        assertTrue(body.contains("not supported"), body);
    }

    @Test
    void objectUploadPutIsRejected() throws Exception {
        var out = new ByteArrayOutputStream();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getOutputStream()).thenReturn(capturing(out));
        FilterChain chain = mock(FilterChain.class);

        new LfsRejectionFilter()
                .doFilter(request("PUT", "/proxy/github.com/acme/app.git/info/lfs/objects/abc123"), resp, chain);

        verify(resp).setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
        verify(chain, never()).doFilter(any(), any());
    }

    /** Downloads carry a small JSON body and are not content-validated, so refusing them would only break clones. */
    @Test
    void lfsDownloadIsAllowedThrough() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);

        new LfsRejectionFilter()
                .doFilter(request("GET", "/proxy/github.com/acme/app.git/info/lfs/objects/abc123"), resp, chain);

        verify(chain).doFilter(any(), any());
        verify(resp, never()).setStatus(anyInt());
    }

    @Test
    void ordinaryGitRequestsPassThroughUntouched() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);

        new LfsRejectionFilter()
                .doFilter(request("POST", "/proxy/github.com/acme/app.git/git-receive-pack"), resp, chain);

        verify(chain).doFilter(any(), any());
        verify(resp, never()).setStatus(anyInt());
    }

    /** The filter must not consult determineOperation, which throws for any non-smart-HTTP request. */
    @Test
    void infoRefsIsNotMistakenForLfs() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        new LfsRejectionFilter()
                .doFilter(
                        request("GET", "/proxy/github.com/acme/app.git/info/refs"),
                        mock(HttpServletResponse.class),
                        chain);

        verify(chain).doFilter(any(), any());
    }
}
