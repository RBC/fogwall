package com.rbc.fogwall.provider;

import java.net.URI;

public interface FogwallProvider {

    /** User-facing label from the YAML config key. Used for display and logging. */
    String getName();

    /**
     * Provider type identifier (e.g. "github", "gitlab", "bitbucket", "forgejo"). Used for API behavior dispatch.
     * Multiple providers can share the same type with different URIs.
     */
    String getType();

    /**
     * Canonical provider identity — equals the user-configured name (the YAML config map key, e.g. {@code "github"},
     * {@code "internal"}). Used for SCM identity resolution, permission matching, token caching, and DB storage.
     * Provider names are unique by YAML map key constraint and stable across hostname changes.
     */
    default String getProviderId() {
        return getName();
    }

    URI getUri();

    String servletPath();

    String servletMapping();

    /**
     * HTTP status code to return when a {@code /info/refs} discovery request is blocked by URL rules. Defaults to
     * {@code 403 Forbidden} — unambiguous, helps clients distinguish a proxy denial from a missing repo. Operators may
     * configure {@code 404} to obscure whether a repository exists at all.
     */
    default int getBlockedInfoRefsStatus() {
        return 403;
    }
}
