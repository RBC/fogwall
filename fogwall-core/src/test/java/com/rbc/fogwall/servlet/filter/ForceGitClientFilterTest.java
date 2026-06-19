package com.rbc.fogwall.servlet.filter;

import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

class ForceGitClientFilterTest {

    private final ForceGitClientFilter filter = new ForceGitClientFilter();

    @Test
    void gitClient_passesThrough() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("POST");
        when(req.getContentType()).thenReturn("application/x-git-receive-pack-request");
        when(req.getRequestURI()).thenReturn("/proxy/github.com/owner/repo.git/git-receive-pack");
        HttpServletResponse resp = mock(HttpServletResponse.class);

        filter.doHttpFilter(req, resp);

        verify(resp, never()).sendError(anyInt(), anyString());
    }

    @Test
    void nonGitClient_sendsError() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("GET");
        when(req.getContentType()).thenReturn("text/html");
        when(req.getRequestURI()).thenReturn("/some/page");
        HttpServletResponse resp = mock(HttpServletResponse.class);

        filter.doHttpFilter(req, resp);

        verify(resp).sendError(eq(HttpServletResponse.SC_BAD_REQUEST), anyString());
    }
}
