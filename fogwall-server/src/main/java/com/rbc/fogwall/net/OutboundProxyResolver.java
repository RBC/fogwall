package com.rbc.fogwall.net;

import com.rbc.fogwall.config.OutboundProxyConfig;
import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * Resolves {@link OutboundProxyConfig} — a YAML value always wins; an unset YAML value falls back to the corresponding
 * {@code HTTP_PROXY}/{@code HTTPS_PROXY}/{@code NO_PROXY} environment variable — into the fogwall-core-visible
 * {@link ResolvedOutboundProxy}.
 */
public final class OutboundProxyResolver {

    private OutboundProxyResolver() {}

    /** Resolves using real environment variables. */
    public static ResolvedOutboundProxy resolve(OutboundProxyConfig config) {
        return resolve(config, System::getenv);
    }

    /** Resolves using a supplied env-var lookup — the seam tests use to avoid depending on process environment. */
    public static ResolvedOutboundProxy resolve(OutboundProxyConfig config, Function<String, String> env) {
        String httpProxy = firstNonBlank(config.getHttpProxy(), env.apply("HTTP_PROXY"));
        String httpsProxy = firstNonBlank(config.getHttpsProxy(), env.apply("HTTPS_PROXY"));
        String noProxy = firstNonBlank(config.getNoProxy(), env.apply("NO_PROXY"));

        if (httpProxy == null && httpsProxy == null) {
            return ResolvedOutboundProxy.NONE;
        }

        URI http = parse(httpProxy, "HTTP_PROXY");
        URI https = parse(httpsProxy, "HTTPS_PROXY");
        Set<String> noProxyHosts = parseNoProxy(noProxy);

        var auth = config.getAuth();
        var authType = ResolvedOutboundProxy.AuthType.valueOf(auth.getType().toUpperCase());
        if (authType == ResolvedOutboundProxy.AuthType.BASIC
                && (auth.getUsername() == null || auth.getPassword() == null)) {
            throw new IllegalArgumentException(
                    "server.outbound-proxy.auth.type is 'basic' but username/password is not set");
        }
        if (authType == ResolvedOutboundProxy.AuthType.KERBEROS && auth.isKeytabMode() && auth.getPrincipal() == null) {
            throw new IllegalArgumentException(
                    "server.outbound-proxy.auth.keytab-path is set but principal is missing");
        }

        return new ResolvedOutboundProxy(
                http != null ? http.getHost() : null,
                http != null ? http.getPort() : 0,
                https != null ? https.getHost() : null,
                https != null ? https.getPort() : 0,
                noProxyHosts,
                authType,
                auth.getUsername(),
                auth.getPassword(),
                auth.getKeytabPath(),
                auth.getPrincipal());
    }

    private static String firstNonBlank(String yamlValue, String envValue) {
        if (yamlValue != null && !yamlValue.isBlank()) return yamlValue;
        if (envValue != null && !envValue.isBlank()) return envValue;
        return null;
    }

    private static URI parse(String value, String label) {
        if (value == null) return null;
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null || uri.getPort() < 0) {
                throw new IllegalArgumentException(label + " must include a host and port, got: " + value);
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(label + " is not a valid proxy URL: " + value, e);
        }
    }

    /**
     * Splits a comma-separated {@code NO_PROXY}-style value into JDK {@code nonProxyHosts} patterns. Entries without a
     * {@code *} wildcard are expanded to also match subdomains (the common {@code NO_PROXY} convention of a bare domain
     * meaning "this host and everything under it"), since the JDK's own matching is exact-or-wildcard only.
     */
    private static Set<String> parseNoProxy(String value) {
        if (value == null || value.isBlank()) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(host -> {
                    result.add(host);
                    if (!host.contains("*")) {
                        result.add("*." + host);
                    }
                });
        return result;
    }
}
