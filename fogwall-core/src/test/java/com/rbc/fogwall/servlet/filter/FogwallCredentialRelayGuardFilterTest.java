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
import java.util.Base64;
import org.junit.jupiter.api.Test;

class FogwallCredentialRelayGuardFilterTest {

    private static final String VALUE = "fgw_abcdefghijklmnop_secret";

    private static HttpServletRequest request(String header, String value) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("GET");
        when(req.getRequestURI()).thenReturn("/proxy/github.com/owner/repo.git/info/refs");
        when(req.getParameter("service")).thenReturn("git-upload-pack");
        when(req.getHeader(header)).thenReturn(value);
        return req;
    }

    private static String basicAuth(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void fogwallCredentialAsThePassword_isRefusedWithoutRelaying() throws Exception {
        HttpServletRequest req = request("Authorization", basicAuth("me", VALUE));
        ByteArrayOutputStream body = new ByteArrayOutputStream();
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
        FilterChain chain = mock(FilterChain.class);

        new FogwallCredentialRelayGuardFilter().doFilter(req, resp, chain);

        verifyNoInteractions(chain);
        verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
        assertTrue(body.toString(StandardCharsets.UTF_8).contains("was not sent upstream"));
    }

    @Test
    void scmCredential_passesThrough() throws Exception {
        HttpServletRequest req = request("Authorization", basicAuth("me", "ghp_token"));
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        new FogwallCredentialRelayGuardFilter().doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
    }

    @Test
    void noCredential_passesThrough() throws Exception {
        HttpServletRequest req = request("Authorization", null);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        new FogwallCredentialRelayGuardFilter().doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
    }

    @Test
    void carriesFogwallCredential_inEveryHeaderShape() {
        assertTrue(FogwallCredentialRelayGuardFilter.carriesFogwallCredential(
                request("Authorization", basicAuth(VALUE, "x-oauth-basic"))));
        assertTrue(FogwallCredentialRelayGuardFilter.carriesFogwallCredential(
                request("Authorization", basicAuth(VALUE, ""))));
        assertTrue(FogwallCredentialRelayGuardFilter.carriesFogwallCredential(
                request("Authorization", "Bearer " + VALUE)));
        assertTrue(
                FogwallCredentialRelayGuardFilter.carriesFogwallCredential(request("Authorization", "token " + VALUE)));
        assertTrue(FogwallCredentialRelayGuardFilter.carriesFogwallCredential(request("PRIVATE-TOKEN", VALUE)));
        assertFalse(FogwallCredentialRelayGuardFilter.carriesFogwallCredential(
                request("Authorization", "Basic not-base64!")));
        assertFalse(FogwallCredentialRelayGuardFilter.carriesFogwallCredential(
                request("Authorization", "Bearer ghp_token")));
    }
}
