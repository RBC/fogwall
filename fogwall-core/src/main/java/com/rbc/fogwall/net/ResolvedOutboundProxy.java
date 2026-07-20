package com.rbc.fogwall.net;

import java.util.Set;

/**
 * Fully-resolved outbound proxy configuration — YAML-vs-env-var precedence and URL parsing already applied — shared
 * across the three outbound paths (JGit Transport, Jetty HttpClient, Apache HC5). Kept as primitive fields rather than
 * exposing the fogwall-server {@code OutboundProxyConfig} YAML DTO directly, mirroring how upstream TLS trust is
 * threaded into fogwall-core as resolved trust managers rather than a config type.
 */
public record ResolvedOutboundProxy(
        String httpProxyHost,
        int httpProxyPort,
        String httpsProxyHost,
        int httpsProxyPort,
        Set<String> noProxyHosts,
        AuthType authType,
        String username,
        String password,
        String keytabPath,
        String principal) {

    public static final ResolvedOutboundProxy NONE =
            new ResolvedOutboundProxy(null, 0, null, 0, Set.of(), AuthType.NONE, null, null, null, null);

    /** Returns true if either an HTTP or HTTPS proxy host is configured. */
    public boolean isConfigured() {
        return httpProxyHost != null || httpsProxyHost != null;
    }

    /** Returns true if Kerberos auth is configured with a keytab, rather than the default ticket-cache mode. */
    public boolean isKeytabMode() {
        return keytabPath != null && !keytabPath.isBlank();
    }

    /** Proxy authentication scheme. */
    public enum AuthType {
        NONE,
        BASIC,
        KERBEROS
    }
}
