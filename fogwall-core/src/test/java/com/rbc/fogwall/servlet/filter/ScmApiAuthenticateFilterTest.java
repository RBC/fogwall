package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.ScmApiRequestContext.SCM_API_REQUEST_ATTR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.provider.GitHubProvider;
import com.rbc.fogwall.service.PushIdentityResolver;
import com.rbc.fogwall.servlet.ScmApiRequestContext;
import com.rbc.fogwall.user.UserEntry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ScmApiAuthenticateFilterTest {

    private PushIdentityResolver resolver;
    private final GitHubProvider provider = new GitHubProvider("/scm-api/github.com");

    @BeforeEach
    void setUp() {
        resolver = mock(PushIdentityResolver.class);
    }

    private static HttpServletRequest mockRequest(String authHeader) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn(authHeader);
        return req;
    }

    private static HttpServletResponse mockResponse(ByteArrayOutputStream body) throws IOException {
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getOutputStream()).thenReturn(new ServletOutputStream() {
            @Override
            public void write(int b) {
                body.write(b);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener l) {}
        });
        return resp;
    }

    @Test
    void missingAuthorizationHeader_returns401() throws Exception {
        HttpServletRequest req = mockRequest(null);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        HttpServletResponse resp = mockResponse(body);
        FilterChain chain = mock(FilterChain.class);

        new ScmApiAuthenticateFilter(provider, resolver).doFilter(req, resp, chain);

        verify(resp).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verifyNoInteractions(chain);
    }

    @Test
    void nullResolver_returns401() throws Exception {
        HttpServletRequest req = mockRequest("Bearer sometoken");
        HttpServletResponse resp = mockResponse(new ByteArrayOutputStream());
        FilterChain chain = mock(FilterChain.class);

        new ScmApiAuthenticateFilter(provider, null).doFilter(req, resp, chain);

        verify(resp).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verifyNoInteractions(chain);
    }

    @Test
    void unresolvableToken_returns401() throws Exception {
        when(resolver.resolve(any(FogwallProvider.class), anyString(), eq("badtoken")))
                .thenReturn(Optional.empty());
        HttpServletRequest req = mockRequest("Bearer badtoken");
        HttpServletResponse resp = mockResponse(new ByteArrayOutputStream());
        FilterChain chain = mock(FilterChain.class);

        new ScmApiAuthenticateFilter(provider, resolver).doFilter(req, resp, chain);

        verify(resp).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verifyNoInteractions(chain);
    }

    @Test
    void resolvableToken_setsContextAndContinuesChain() throws Exception {
        UserEntry alice = UserEntry.builder()
                .username("alice")
                .emails(List.of())
                .scmIdentities(List.of())
                .build();
        when(resolver.resolve(any(FogwallProvider.class), anyString(), eq("goodtoken")))
                .thenReturn(Optional.of(alice));
        HttpServletRequest req = mockRequest("Bearer goodtoken");
        HttpServletResponse resp = mockResponse(new ByteArrayOutputStream());
        FilterChain chain = mock(FilterChain.class);

        new ScmApiAuthenticateFilter(provider, resolver).doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        ArgumentCaptor<ScmApiRequestContext> captor = ArgumentCaptor.forClass(ScmApiRequestContext.class);
        verify(req).setAttribute(eq(SCM_API_REQUEST_ATTR), captor.capture());
        assertEquals("alice", captor.getValue().getResolvedUser());
        assertEquals(provider.getProviderId(), captor.getValue().getProvider());
    }

    @Test
    void tokenSchemeIsStripped() throws Exception {
        when(resolver.resolve(any(FogwallProvider.class), anyString(), eq("raw-token")))
                .thenReturn(Optional.of(UserEntry.builder()
                        .username("bob")
                        .emails(List.of())
                        .scmIdentities(List.of())
                        .build()));
        HttpServletRequest req = mockRequest("token raw-token");
        HttpServletResponse resp = mockResponse(new ByteArrayOutputStream());
        FilterChain chain = mock(FilterChain.class);

        new ScmApiAuthenticateFilter(provider, resolver).doFilter(req, resp, chain);

        verify(resolver).resolve(any(FogwallProvider.class), anyString(), eq("raw-token"));
        verify(chain).doFilter(req, resp);
    }
}
