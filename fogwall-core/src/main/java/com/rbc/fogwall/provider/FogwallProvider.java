package com.rbc.fogwall.provider;

import java.net.URI;
import java.util.Optional;

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

    /**
     * Builds a browsable web URL for the given repository on this provider's platform, e.g.
     * {@code https://github.com/owner/repo}. Returns {@link Optional#empty()} for providers with no stable public repo
     * URL shape (generic bare-git providers).
     */
    default Optional<String> buildRepoUrl(String owner, String repo) {
        return Optional.empty();
    }

    /**
     * Builds a browsable web URL for a specific commit within the given repository, e.g.
     * {@code https://github.com/owner/repo/commit/<sha>}. Returns {@link Optional#empty()} for providers with no stable
     * public repo URL shape (generic bare-git providers).
     */
    default Optional<String> buildCommitUrl(String owner, String repo, String sha) {
        return Optional.empty();
    }
}
