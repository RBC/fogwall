package com.rbc.fogwall.config;

import lombok.Data;

/** Binds a single entry under {@code providers:} in fogwall.yml. */
@Data
public class ProviderConfig {

    private boolean enabled = true;

    /**
     * Custom URL suffix (appended after {@link com.rbc.fogwall.jetty.FogwallServletRegistrar#PROXY_PATH_PREFIX} and
     * {@link com.rbc.fogwall.jetty.FogwallServletRegistrar#PUSH_PATH_PREFIX}) for this provider's servlet path. Allows
     * operators to set a specific path to listen for git requests. By default, this is computed from the provider's
     * hostname & optional port (80/443 are implied for http/https respectively)
     */
    private String pathSuffix = "";

    /** Upstream base URI. Required for custom providers; omit for built-ins (github, gitlab, bitbucket). */
    private String uri = "";

    /**
     * Provider type. Required for providers configured with custom names or providers with no canonical URI (Forgejo,
     * generic); this field can only be unset for the built-in default names when a canonical hostname is known (i.e.
     * {@code name=github}, implies {@code type=github,uri=https://github.com}). Supported values: {@code github},
     * {@code gitlab}, {@code bitbucket}, {@code codeberg}, {@code gitea}.
     */
    private String type = "";

    /**
     * HTTP status returned to the git client when a {@code /info/refs} discovery request is blocked by URL rules.
     * {@code 403} (default) is unambiguous — clients see a clear denial. Use {@code 404} to obscure whether a
     * repository exists at all (security by obscurity for sensitive environments).
     */
    private int blockedInfoRefsStatus = 403;
}
