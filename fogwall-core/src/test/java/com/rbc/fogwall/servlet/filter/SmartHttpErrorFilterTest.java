package com.rbc.fogwall.servlet.filter;

import static org.mockito.Mockito.*;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link SmartHttpErrorFilter}.
 *
 * <p>The filter wraps the response on git smart HTTP requests so that 4xx/5xx status codes are silently mapped to 200
 * (except 401). Non-git requests pass through unchanged.
 */
class SmartHttpErrorFilterTest {

    /** Build a request that looks like a git receive-pack POST. */
    private static HttpServletRequest receivePackRequest() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getContentType()).thenReturn("application/x-git-receive-pack-request");
        when(req.getRequestURI()).thenReturn("/push/github.com/owner/repo.git/git-receive-pack");
        when(req.getMethod()).thenReturn("POST");
        return req;
    }

    /** Build a request that looks like a regular (non-git) HTTP call. */
    private static HttpServletRequest plainRequest() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getContentType()).thenReturn("application/json");
        when(req.getRequestURI()).thenReturn("/api/some-endpoint");
        when(req.getMethod()).thenReturn("GET");
        return req;
    }

    // ---- tests ---- //

    @Test
    void gitSmartRequest_chainIsCalled() throws Exception {
        SmartHttpErrorFilter filter = new SmartHttpErrorFilter();
        HttpServletRequest req = receivePackRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        // chain must be called even when the wrapper is applied
        verify(chain).doFilter(eq(req), any());
    }

    @Test
    void nonGitRequest_chainCalledWithOriginalResponse() throws Exception {
        SmartHttpErrorFilter filter = new SmartHttpErrorFilter();
        HttpServletRequest req = plainRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        // For non-git requests the chain must be called with the original response, not a wrapper
        verify(chain).doFilter(req, resp);
    }

    @Test
    void gitSmartRequest_errorStatusMappedTo200ViaWrapper() throws Exception {
        // Verify that when a 403 is set during chain execution the wrapper intercepts it.
        // We capture the wrapper passed to the chain and call setStatus on it.
        SmartHttpErrorFilter filter = new SmartHttpErrorFilter();
        HttpServletRequest req = receivePackRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);

        FilterChain chain = (servletReq, servletResp) -> {
            // The response passed to the chain is the wrapper
            HttpServletResponse wrappedResp = (HttpServletResponse) servletResp;
            wrappedResp.setStatus(403);
            // The wrapper must have redirected this to 200
            verify(resp).setStatus(HttpServletResponse.SC_OK);
        };

        filter.doFilter(req, resp, chain);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 403, 404, 500, 503})
    void errorStatuses_mappedTo200(int statusCode) throws Exception {
        SmartHttpErrorFilter filter = new SmartHttpErrorFilter();
        HttpServletRequest req = receivePackRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);

        FilterChain chain = (servletReq, servletResp) -> {
            ((HttpServletResponse) servletResp).setStatus(statusCode);
        };

        filter.doFilter(req, resp, chain);

        verify(resp).setStatus(HttpServletResponse.SC_OK);
    }

    @Test
    void unauthorizedStatus_passesThroughUnchanged() throws Exception {
        SmartHttpErrorFilter filter = new SmartHttpErrorFilter();
        HttpServletRequest req = receivePackRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);

        FilterChain chain = (servletReq, servletResp) -> {
            ((HttpServletResponse) servletResp).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        };

        filter.doFilter(req, resp, chain);

        // 401 must pass through - git clients need it to prompt for credentials
        verify(resp).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void sendError_nonUnauthorized_mapTo200() throws Exception {
        SmartHttpErrorFilter filter = new SmartHttpErrorFilter();
        HttpServletRequest req = receivePackRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);

        FilterChain chain = (servletReq, servletResp) -> {
            ((HttpServletResponse) servletResp).sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
        };

        filter.doFilter(req, resp, chain);

        verify(resp).setStatus(HttpServletResponse.SC_OK);
    }

    @Test
    void sendError_unauthorized_passesThroughUnchanged() throws Exception {
        SmartHttpErrorFilter filter = new SmartHttpErrorFilter();
        HttpServletRequest req = receivePackRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);

        FilterChain chain = (servletReq, servletResp) -> {
            ((HttpServletResponse) servletResp).sendError(HttpServletResponse.SC_UNAUTHORIZED);
        };

        filter.doFilter(req, resp, chain);

        verify(resp).sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
