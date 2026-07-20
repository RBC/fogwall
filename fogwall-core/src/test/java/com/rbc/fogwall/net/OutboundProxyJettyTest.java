package com.rbc.fogwall.net;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.util.Set;
import org.eclipse.jetty.client.Authentication;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.http.HttpHeader;
import org.junit.jupiter.api.Test;

class OutboundProxyJettyTest {

    @Test
    void configure_nullProxy_doesNotTouchClient() {
        var client = new HttpClient();
        assertDoesNotThrow(() -> OutboundProxyJetty.configure(client, null));
        assertTrue(client.getProxyConfiguration().getProxies().isEmpty());
    }

    @Test
    void configure_unconfiguredProxy_doesNotAddProxy() {
        var client = new HttpClient();
        OutboundProxyJetty.configure(client, ResolvedOutboundProxy.NONE);
        assertTrue(client.getProxyConfiguration().getProxies().isEmpty());
    }

    @Test
    void configure_basicAuthProxy_addsProxyAndAuthentication() {
        var client = new HttpClient();
        var proxy = new ResolvedOutboundProxy(
                null,
                0,
                "proxy.example.com",
                8080,
                Set.of(),
                ResolvedOutboundProxy.AuthType.BASIC,
                "user",
                "pass",
                null,
                null);

        OutboundProxyJetty.configure(client, proxy);

        assertEquals(1, client.getProxyConfiguration().getProxies().size());
        assertNotNull(client.getAuthenticationStore()
                .findAuthentication("Basic", URI.create("http://proxy.example.com:8080"), Authentication.ANY_REALM));
    }

    @Test
    void configure_kerberosProxy_addsProxyAndAuthentication() {
        var client = new HttpClient();
        var proxy = new ResolvedOutboundProxy(
                null,
                0,
                "proxy.example.com",
                8080,
                Set.of(),
                ResolvedOutboundProxy.AuthType.KERBEROS,
                null,
                null,
                null,
                null);

        OutboundProxyJetty.configure(client, proxy);

        assertEquals(1, client.getProxyConfiguration().getProxies().size());
        assertNotNull(client.getAuthenticationStore()
                .findAuthentication(
                        HttpHeader.NEGOTIATE.asString(),
                        URI.create("http://proxy.example.com:8080"),
                        Authentication.ANY_REALM));
    }
}
