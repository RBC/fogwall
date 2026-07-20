package com.rbc.fogwall.net;

import java.net.URI;
import org.eclipse.jetty.client.BasicAuthentication;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.client.Origin;
import org.eclipse.jetty.client.SPNEGOAuthentication;

/**
 * Wires a resolved outbound proxy into a Jetty {@link HttpClient} — used by {@code FogwallServlet} for
 * transparent-proxy forwarding.
 *
 * <p>Jetty's client has no NTLM support (see the {@code server.outbound-proxy} docs for why); Basic and Kerberos/SPNEGO
 * are the two supported auth types here. As with {@link FogwallHttpExecutor}, Kerberos's ticket acquisition is a
 * JVM-wide JAAS concern configured once at startup, not here.
 */
public final class OutboundProxyJetty {

    private OutboundProxyJetty() {}

    /** No-op when {@code proxy} is null or unconfigured. */
    public static void configure(HttpClient client, ResolvedOutboundProxy proxy) {
        if (proxy == null || !proxy.isConfigured()) {
            return;
        }

        String host = proxy.httpsProxyHost() != null ? proxy.httpsProxyHost() : proxy.httpProxyHost();
        int port = proxy.httpsProxyHost() != null ? proxy.httpsProxyPort() : proxy.httpProxyPort();

        client.getProxyConfiguration().addProxy(new HttpProxy(new Origin.Address(host, port), false));

        URI proxyUri = URI.create("http://" + host + ":" + port);
        switch (proxy.authType()) {
            case BASIC ->
                client.getAuthenticationStore()
                        .addAuthentication(new BasicAuthentication(
                                proxyUri,
                                org.eclipse.jetty.client.Authentication.ANY_REALM,
                                proxy.username(),
                                proxy.password()));
            case KERBEROS -> client.getAuthenticationStore().addAuthentication(new SPNEGOAuthentication(proxyUri));
            case NONE -> {
                // no credentials — proxy is either open or handles auth upstream itself
            }
        }
    }
}
