package com.rbc.fogwall.scmapi;

import java.util.Optional;

/**
 * Persistent cache mapping a GitLab numeric project ID to the {@code owner/repo} it names.
 *
 * <p>Deliberately separate from {@link NodeIdCache} rather than a generalisation of it. A GraphQL node ID and a GitLab
 * project ID are different identifiers from different APIs that happen to share a resolution shape; collapsing them
 * would leave one abstraction whose name is wrong for at least one of its users, and a schema column to match.
 */
public interface GitLabProjectIdCache {

    /** Returns the cached {@code owner/repo} for {@code (provider, projectId)}, or empty if absent or expired. */
    Optional<OwnerRepo> lookup(String provider, String projectId);

    /** Stores or refreshes the {@code owner/repo} resolution for {@code (provider, projectId)}. */
    void store(String provider, String projectId, OwnerRepo ownerRepo);
}
