package com.rbc.fogwall.servlet.filter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.git.UpstreamAuthProbe;
import com.rbc.fogwall.provider.FogwallProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BasicAuthChallengeFilterTest {

    BasicAuthChallengeFilter filter;

    @BeforeEach
    void setUp() {
        filter = new BasicAuthChallengeFilter();
    }

    private HttpServletRequest receivePackRequest(String authHeader) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("POST");
        when(req.getRequestURI()).thenReturn("/git-receive-pack");
        when(req.getContentType()).thenReturn("application/x-git-receive-pack-request");
        when(req.getHeader("Authorization")).thenReturn(authHeader);
        return req;
    }

    private HttpServletRequest infoRefsReceivePackRequest(String authHeader) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("GET");
        when(req.getRequestURI()).thenReturn("/info/refs");
        when(req.getParameter("service")).thenReturn("git-receive-pack");
        when(req.getHeader("Authorization")).thenReturn(authHeader);
        return req;
    }

    private HttpServletRequest fetchRequest(String authHeader) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("POST");
        when(req.getRequestURI()).thenReturn("/git-upload-pack");
        when(req.getContentType()).thenReturn("application/x-git-upload-pack-request");
        when(req.getHeader("Authorization")).thenReturn(authHeader);
        return req;
    }

    private HttpServletRequest infoRefsFetchRequest() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("GET");
        when(req.getRequestURI()).thenReturn("/info/refs");
        when(req.getParameter("service")).thenReturn("git-upload-pack");
        when(req.getHeader("Authorization")).thenReturn(null);
        return req;
    }

    /** A probe with a fixed answer that records how many times it was asked. */
    private static class StubProbe extends UpstreamAuthProbe {
        private final boolean requiresAuth;
        int calls;

        StubProbe(boolean requiresAuth) {
            this.requiresAuth = requiresAuth;
        }

        @Override
        public boolean requiresAuthentication(String upstreamRepoUrl) {
            calls++;
            return requiresAuth;
        }
    }

    private static BasicAuthChallengeFilter filterWith(UpstreamAuthProbe probe) {
        FogwallProvider provider = mock(FogwallProvider.class);
        lenient().when(provider.getUri()).thenReturn(URI.create("https://upstream.example"));
        return new BasicAuthChallengeFilter(provider, probe);
    }

    private static String basicAuth(String user, String token) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + token).getBytes());
    }

    // ---- receive-pack without auth → 401 + WWW-Authenticate ----

    @Test
    void receivePack_noAuth_challenges() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        HttpServletRequest req = receivePackRequest(null);
        HttpServletResponse resp = mock(HttpServletResponse.class);

        filter.doFilter(req, resp, chain);

        verify(resp).setHeader("WWW-Authenticate", "Basic realm=\"fogwall\"");
        verify(resp).sendError(HttpServletResponse.SC_UNAUTHORIZED);
        verifyNoInteractions(chain);
    }

    // ---- receive-pack with blank auth header → 401 ----

    @Test
    void receivePack_blankAuth_challenges() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        HttpServletRequest req = receivePackRequest("   ");
        HttpServletResponse resp = mock(HttpServletResponse.class);

        filter.doFilter(req, resp, chain);

        verify(resp).sendError(HttpServletResponse.SC_UNAUTHORIZED);
        verifyNoInteractions(chain);
    }

    // ---- receive-pack with valid Basic auth → passes through ----

    @Test
    void receivePack_withAuth_passesThrough() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        HttpServletRequest req = receivePackRequest(basicAuth("alice", "token123"));
        HttpServletResponse resp = mock(HttpServletResponse.class);

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        verify(resp, never()).sendError(anyInt());
    }

    // ---- fetch (upload-pack) without auth → 401 (server mode clones upstream on every open,
    // including fetches, so private repos need a chance to send credentials on this path too) ----

    @Test
    void fetch_noAuth_challenges() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        HttpServletRequest req = fetchRequest(null);
        HttpServletResponse resp = mock(HttpServletResponse.class);

        filter.doFilter(req, resp, chain);

        verify(resp).sendError(HttpServletResponse.SC_UNAUTHORIZED);
        verifyNoInteractions(chain);
    }

    // ---- fetch (upload-pack) with auth → passes through ----

    @Test
    void fetch_withAuth_passesThrough() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        HttpServletRequest req = fetchRequest(basicAuth("alice", "token123"));
        HttpServletResponse resp = mock(HttpServletResponse.class);

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        verify(resp, never()).sendError(anyInt());
    }

    // ---- info/refs for fetch (git-upload-pack) without auth → 401 ----

    @Test
    void infoRefs_fetch_noAuth_challenges() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        HttpServletRequest req = infoRefsFetchRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);

        filter.doFilter(req, resp, chain);

        verify(resp).sendError(HttpServletResponse.SC_UNAUTHORIZED);
        verifyNoInteractions(chain);
    }

    // ---- info/refs?service=git-receive-pack without auth → 401 ----

    @Test
    void infoRefs_receivePack_noAuth_challenges() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        HttpServletRequest req = infoRefsReceivePackRequest(null);
        HttpServletResponse resp = mock(HttpServletResponse.class);

        filter.doFilter(req, resp, chain);

        verify(resp).sendError(HttpServletResponse.SC_UNAUTHORIZED);
        verifyNoInteractions(chain);
    }

    // ---- info/refs?service=git-receive-pack with auth → passes through ----

    @Test
    void infoRefs_receivePack_withAuth_passesThrough() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        HttpServletRequest req = infoRefsReceivePackRequest(basicAuth("alice", "token123"));
        HttpServletResponse resp = mock(HttpServletResponse.class);

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        verify(resp, never()).sendError(anyInt());
    }

    // ---- WWW-Authenticate header uses correct realm ----

    @Test
    void challenge_headerContainsRealm() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        HttpServletRequest req = receivePackRequest(null);
        HttpServletResponse resp = mock(HttpServletResponse.class);

        filter.doFilter(req, resp, chain);

        ArgumentCaptor<String> headerValue = ArgumentCaptor.forClass(String.class);
        verify(resp).setHeader(eq("WWW-Authenticate"), headerValue.capture());
        assertTrue(headerValue.getValue().startsWith("Basic "));
        assertTrue(headerValue.getValue().contains("realm="));
    }

    // ---- upstream-driven challenge on the fetch path ----

    /**
     * A public upstream must stay anonymously clonable. Challenging here breaks every client that has no credential to
     * offer, and providers reject an unrelated or expired token on a repository they would have served anonymously — so
     * the challenge is worse than useless, it converts a working clone into a failure.
     */
    @Test
    void fetch_publicUpstream_isNotChallenged() throws Exception {
        StubProbe probe = new StubProbe(false);
        FilterChain chain = mock(FilterChain.class);
        HttpServletRequest req = fetchRequest(null);
        when(req.getPathInfo()).thenReturn("/owner/repo.git/git-upload-pack");
        HttpServletResponse resp = mock(HttpServletResponse.class);

        filterWith(probe).doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        verify(resp, never()).sendError(anyInt());
        assertEquals(1, probe.calls);
    }

    @Test
    void fetch_privateUpstream_isChallenged() throws Exception {
        StubProbe probe = new StubProbe(true);
        FilterChain chain = mock(FilterChain.class);
        HttpServletRequest req = fetchRequest(null);
        when(req.getPathInfo()).thenReturn("/owner/repo.git/git-upload-pack");
        HttpServletResponse resp = mock(HttpServletResponse.class);

        filterWith(probe).doFilter(req, resp, chain);

        verify(resp).sendError(HttpServletResponse.SC_UNAUTHORIZED);
        verifyNoInteractions(chain);
    }

    @Test
    void infoRefs_fetch_publicUpstream_isNotChallenged() throws Exception {
        StubProbe probe = new StubProbe(false);
        FilterChain chain = mock(FilterChain.class);
        HttpServletRequest req = infoRefsFetchRequest();
        when(req.getPathInfo()).thenReturn("/owner/repo.git/info/refs");
        HttpServletResponse resp = mock(HttpServletResponse.class);

        filterWith(probe).doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        verify(resp, never()).sendError(anyInt());
    }

    /** Push is forwarded upstream with the developer's own credentials, so it is always challenged. */
    @Test
    void push_isChallengedWhateverTheUpstreamAllows() throws Exception {
        StubProbe probe = new StubProbe(false);
        FilterChain chain = mock(FilterChain.class);
        HttpServletRequest req = receivePackRequest(null);
        HttpServletResponse resp = mock(HttpServletResponse.class);

        filterWith(probe).doFilter(req, resp, chain);

        verify(resp).sendError(HttpServletResponse.SC_UNAUTHORIZED);
        assertEquals(0, probe.calls, "a push must not cost an upstream round trip");
    }

    /** The probe is on the unauthenticated path only — an authenticated fetch must cost nothing extra. */
    @Test
    void fetch_withCredentials_neverProbes() throws Exception {
        StubProbe probe = new StubProbe(true);
        FilterChain chain = mock(FilterChain.class);
        HttpServletRequest req = fetchRequest(basicAuth("alice", "token123"));
        HttpServletResponse resp = mock(HttpServletResponse.class);

        filterWith(probe).doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        assertEquals(0, probe.calls);
    }

    /** An unparseable or invalid repository path must not become an anonymous read. */
    @Test
    void fetch_withUnusableRepositoryPath_isChallenged() throws Exception {
        StubProbe probe = new StubProbe(false);
        FilterChain chain = mock(FilterChain.class);
        HttpServletRequest req = fetchRequest(null);
        when(req.getPathInfo()).thenReturn("/owner/../elsewhere.git/git-upload-pack");
        HttpServletResponse resp = mock(HttpServletResponse.class);

        filterWith(probe).doFilter(req, resp, chain);

        verify(resp).sendError(HttpServletResponse.SC_UNAUTHORIZED);
        assertEquals(0, probe.calls);
    }
}
