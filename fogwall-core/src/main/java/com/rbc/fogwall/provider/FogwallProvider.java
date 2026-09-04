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

    /**
     * The SSH transport endpoint for this provider, if it serves SSH. A provider may serve HTTP (via
     * {@link #getUri()}), SSH (via this endpoint), or both from a single config entry (see fogwall#531). Returns
     * {@link Optional#empty()} for HTTP-only providers. The SSH server routes and forwards using this endpoint rather
     * than {@link #getUri()}.
     */
    default Optional<URI> getSshUri() {
        return Optional.empty();
    }

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
     * Whether server mode serves clone/fetch from the local mirror for this provider (fogwall#478). Defaults to
     * {@code true} — a developer whose remote is the fogwall URL expects {@code git pull} to work. When {@code false},
     * the {@code git-upload-pack} capability is not mounted on either transport (HTTP and SSH) and fetches are refused
     * with a clear git-side error; push (receive-pack) is unaffected. Resolved from {@code server.serve-fetch} with an
     * optional per-provider {@code providers.<name>.serve-fetch} override. Transparent proxy mode ignores this — it
     * forwards to upstream rather than serving a local mirror.
     */
    default boolean isServeFetch() {
        return true;
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
