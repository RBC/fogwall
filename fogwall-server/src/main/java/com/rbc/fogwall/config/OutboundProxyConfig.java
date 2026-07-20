package com.rbc.fogwall.config;

import lombok.Data;

/**
 * Binds the {@code server.outbound-proxy:} block in fogwall.yml.
 *
 * <p>Covers outbound connections fogwall itself makes: store-and-forward upstream pushes (JGit Transport),
 * transparent-proxy forwarding (Jetty HttpClient), and provider REST API calls (Apache HttpClient 5). All three are
 * wired from the single resolved proxy here rather than configured independently.
 *
 * <p>{@link #httpProxy}, {@link #httpsProxy}, and {@link #noProxy} fall back to the {@code HTTP_PROXY},
 * {@code HTTPS_PROXY}, and {@code NO_PROXY} environment variables (respected by most CLI tooling) when left unset here.
 * An explicit YAML value always takes precedence over the environment variable.
 *
 * <p>Example (authenticating proxy):
 *
 * <pre>{@code
 * server:
 *   outbound-proxy:
 *     https-proxy: http://proxy.example.com:8080
 *     no-proxy: localhost,*.internal.example.com
 *     auth:
 *       type: basic
 *       username: ${PROXY_USER}
 *       password: ${PROXY_PASS}
 * }</pre>
 *
 * <p>Example (proxy that doesn't require fogwall to authenticate itself — env vars only, no YAML needed):
 *
 * <pre>{@code
 * # HTTP_PROXY=http://proxy.example.com:8080
 * # HTTPS_PROXY=http://proxy.example.com:8080
 * server:
 *   outbound-proxy:
 *     auth:
 *       type: none   # default
 * }</pre>
 */
@Data
public class OutboundProxyConfig {

    /** Proxy for plain HTTP requests. Falls back to the {@code HTTP_PROXY} env var when unset. */
    private String httpProxy;

    /** Proxy for HTTPS requests. Falls back to the {@code HTTPS_PROXY} env var when unset. */
    private String httpsProxy;

    /** Comma-separated hosts to bypass the proxy for. Falls back to the {@code NO_PROXY} env var when unset. */
    private String noProxy;

    /** Authentication against the proxy itself. Defaults to no auth. */
    private AuthConfig auth = new AuthConfig();

    /** Binds the {@code server.outbound-proxy.auth:} block. */
    @Data
    public static class AuthConfig {

        /**
         * Proxy authentication scheme. One of:
         *
         * <ul>
         *   <li>{@code none} (default) — no auth sent by fogwall; use when the configured proxy doesn't require fogwall
         *       to authenticate itself
         *   <li>{@code basic} — HTTP Basic auth against the proxy
         *   <li>{@code kerberos} — SPNEGO/Kerberos (Negotiate) against the proxy
         * </ul>
         */
        private String type = "none";

        /** Proxy username. Required when {@link #type} is {@code basic}. */
        private String username;

        /** Proxy password. Required when {@link #type} is {@code basic}. */
        private String password;

        /**
         * Path to a Kerberos keytab file. Only used when {@link #type} is {@code kerberos}. When unset (the default),
         * fogwall authenticates using an existing OS Kerberos ticket cache instead of a keytab — the common case when
         * fogwall runs on a machine that already carries a ticket from domain/SSO login, rather than as a headless
         * service with its own identity.
         */
        private String keytabPath;

        /**
         * Kerberos principal (e.g. {@code fogwall/host@CORP.EXAMPLE.COM}). Required when {@link #keytabPath} is set;
         * ignored in ticket-cache mode, where the cache's own principal is used.
         */
        private String principal;

        /** Returns true if a keytab-based service identity is configured, rather than the default ticket-cache mode. */
        public boolean isKeytabMode() {
            return keytabPath != null && !keytabPath.isBlank();
        }
    }
}
