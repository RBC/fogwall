package com.rbc.fogwall.servlet;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * The query-string rule on the REST dialects: a write may not carry a parameter that authenticates or changes the
 * acting identity.
 *
 * <p>The forwarder relays the query string upstream unchanged, and both REST providers accept a credential there —
 * GitLab tries {@code access_token} before the bearer header and falls back to {@code private_token}; Forgejo checks
 * {@code token} and {@code access_token} where query-token auth is enabled. A write is authorized and audited against
 * the header credential, so one that names a different token or {@code sudo} in the query would be acted on by the
 * upstream as someone fogwall never authorized. Any other parameter on a write is left to the CLI that sends it.
 *
 * <p>A read is not restricted. Reads are gated by authentication and the path allowlist alone, and a parameter on a
 * read can only filter, sort or page what the caller's own credential could already fetch. GitHub's GraphQL dialect
 * does not consult this: its forwarder posts to one fixed URL, so a query string the caller sends is dropped rather
 * than relayed.
 */
public final class ScmApiQueryPolicy {

    private static final Set<String> IDENTITY_PARAMETERS = Set.of("access_token", "private_token", "token", "sudo");

    private ScmApiQueryPolicy() {}

    /**
     * @param mutation whether the request is a write
     * @return the first credential or identity parameter on a write, or {@code null} when the request may proceed
     */
    public static String refusedParameter(String queryString, boolean mutation) {
        if (!mutation || queryString == null || queryString.isEmpty()) {
            return null;
        }
        for (String pair : queryString.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String rawName = eq < 0 ? pair : pair.substring(0, eq);
            // Percent-decoded and lower-cased before matching: %74oken and token name the same parameter upstream,
            // and a proxy should not depend on the provider's own case sensitivity.
            String name =
                    URLDecoder.decode(rawName, StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
            if (IDENTITY_PARAMETERS.contains(name)) {
                return name;
            }
        }
        return null;
    }
}
