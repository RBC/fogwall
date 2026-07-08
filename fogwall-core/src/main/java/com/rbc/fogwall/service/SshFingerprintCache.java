package com.rbc.fogwall.service;

import java.util.Set;

/** Persistent cache for SSH public key fingerprints fetched from upstream SCM providers. */
public interface SshFingerprintCache {

    /** Returns the cached fingerprint set for {@code (provider, scmLogin)}, or empty set if absent or expired. */
    Set<String> lookup(String provider, String scmLogin);

    /** Stores or refreshes the fingerprint set for {@code (provider, scmLogin)}. Only call with non-empty sets. */
    void store(String provider, String scmLogin, Set<String> fingerprints);

    /**
     * Evicts the cache entry for {@code (provider, scmLogin)}. Call when a user adds or removes an SSH key on their SCM
     * account so the next lookup re-fetches immediately.
     */
    void evict(String provider, String scmLogin);
}
