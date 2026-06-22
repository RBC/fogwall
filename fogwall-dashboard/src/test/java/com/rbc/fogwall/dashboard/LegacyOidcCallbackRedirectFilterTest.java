package com.rbc.fogwall.dashboard;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LegacyOidcCallbackRedirectFilterTest {

    LegacyOidcCallbackRedirectFilter filter;
    HttpServletRequest request;
    HttpServletResponse response;
    FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new LegacyOidcCallbackRedirectFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
    }

    @Test
    void legacyPath_withQueryString_redirectsToCurrentPath() throws Exception {
        when(request.getServletPath()).thenReturn(LegacyOidcCallbackRedirectFilter.LEGACY_PATH);
        when(request.getQueryString()).thenReturn("code=abc123&state=xyz");

        filter.doFilterInternal(request, response, chain);

        verify(response).sendRedirect(LegacyOidcCallbackRedirectFilter.CURRENT_PATH + "?code=abc123&state=xyz");
        verifyNoInteractions(chain);
    }

    @Test
    void legacyPath_withoutQueryString_redirectsToCurrentPath() throws Exception {
        when(request.getServletPath()).thenReturn(LegacyOidcCallbackRedirectFilter.LEGACY_PATH);
        when(request.getQueryString()).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        verify(response).sendRedirect(LegacyOidcCallbackRedirectFilter.CURRENT_PATH);
        verifyNoInteractions(chain);
    }

    @Test
    void currentPath_passesThroughUnmodified() throws Exception {
        when(request.getServletPath()).thenReturn(LegacyOidcCallbackRedirectFilter.CURRENT_PATH);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(response);
    }

    @Test
    void unrelatedPath_passesThroughUnmodified() throws Exception {
        when(request.getServletPath()).thenReturn("/api/health");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(response);
    }
}
