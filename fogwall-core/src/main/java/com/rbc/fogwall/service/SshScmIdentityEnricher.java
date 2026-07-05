package com.rbc.fogwall.service;

import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.provider.SshKeyFingerprintLookup;
import com.rbc.fogwall.user.UserEntry;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the SCM login of a user who has authenticated via SSH public key, using the provider's public SSH key
 * listing API. Results are cached to avoid repeated API calls for the same user.
 *
 * <h2>How it works</h2>
 *
 * <p>HTTP pushes prove identity via token — the token is exchanged for a canonical SCM login by
 * {@link TokenPushIdentityResolver}. SSH pushes have no token; instead, the connecting key's fingerprint is used. This
 * service iterates the SCM logins already linked to the proxy user (from {@code user_scm_identities}), fetches each
 * login's registered SSH key fingerprints from the provider via {@link SshKeyFingerprintLookup}, and returns the
 * matching SCM login if found.
 *
 * <h2>Enrichment, not gating</h2>
 *
 * <p>This is additive — it does not gate the push. If no match is found (e.g. the user has not registered their SSH key
 * on the SCM, or the provider does not implement {@link SshKeyFingerprintLookup}), the push proceeds without a resolved
 * {@code scmUsername}. A match binds the push record's {@code scmUsername} field for attribution in the dashboard and
 * audit log.
 *
 * <h2>Caching</h2>
 *
 * <p>SSH keys change infrequently. Results are cached by {@code (providerId, scmLogin)} with a configurable TTL
 * (default: 7 days) to avoid hammering provider APIs. In corporate environments with shared egress IPs, unauthenticated
 * API rate limits apply to the whole enterprise — a long TTL is the primary mitigation.
 *
 * <p>Only non-empty results are cached. An empty set (user not found, API error) is never stored, so transient failures
 * do not block future lookups.
 */
@Slf4j
public class SshScmIdentityEnricher {

    public static final Duration DEFAULT_TTL = Duration.ofDays(7);

    private record CacheKey(String providerId, String login) {}

    private record CacheEntry(Set<String> fingerprints, long fetchedAt) {
        boolean isExpired(long ttlMs) {
            return System.currentTimeMillis() - fetchedAt > ttlMs;
        }
    }

    private final Map<CacheKey, CacheEntry> cache = new ConcurrentHashMap<>();
    private final long ttlMs;

    public SshScmIdentityEnricher() {
        this(DEFAULT_TTL);
    }

    public SshScmIdentityEnricher(Duration ttl) {
        this.ttlMs = ttl.toMillis();
    }

    /**
     * Attempts to resolve the SCM login of {@code user} by matching {@code connectingFingerprint} against the SSH keys
     * registered for each of the user's linked SCM identities on {@code provider}.
     *
     * @param user the proxy user resolved at SSH connection time by public-key auth
     * @param provider the fogwall provider for this push — must implement {@link SshKeyFingerprintLookup}
     * @param connectingFingerprint SHA-256 fingerprint of the key the client connected with
     * @return the SCM login whose registered keys include {@code connectingFingerprint}, or empty if not found
     */
    public Optional<String> resolveScmLogin(UserEntry user, FogwallProvider provider, String connectingFingerprint) {
        if (!(provider instanceof SshKeyFingerprintLookup lookup)) {
            log.debug("Provider '{}' does not support SSH fingerprint lookup", provider.getName());
            return Optional.empty();
        }
        if (user.getScmIdentities() == null || user.getScmIdentities().isEmpty()) {
            log.debug(
                    "Proxy user '{}' has no linked SCM identities for provider '{}'",
                    user.getUsername(),
                    provider.getProviderId());
            return Optional.empty();
        }

        return user.getScmIdentities().stream()
                .filter(id -> provider.getProviderId().equals(id.getProvider()))
                .map(id -> id.getUsername())
                .filter(scmLogin ->
                        fingerprints(provider.getProviderId(), scmLogin, lookup).contains(connectingFingerprint))
                .findFirst()
                .map(scmLogin -> {
                    log.debug(
                            "SSH fingerprint matched SCM login '{}' on provider '{}'",
                            scmLogin,
                            provider.getProviderId());
                    return scmLogin;
                });
    }

    /**
     * Evicts the cached fingerprint set for {@code (providerId, login)}. Call when a user adds or removes an SSH key so
     * the next lookup re-fetches immediately rather than waiting for TTL expiry.
     */
    public void evict(String providerId, String login) {
        cache.remove(new CacheKey(providerId, login));
    }

    private Set<String> fingerprints(String providerId, String login, SshKeyFingerprintLookup lookup) {
        var key = new CacheKey(providerId, login);
        var entry = cache.get(key);
        if (entry != null && !entry.isExpired(ttlMs)) {
            log.debug("SSH fingerprint cache hit for {}/{}", providerId, login);
            return entry.fingerprints();
        }
        log.debug("SSH fingerprint cache miss for {}/{} — fetching from provider", providerId, login);
        Set<String> result = lookup.fetchSshFingerprints(login);
        if (!result.isEmpty()) {
            cache.put(key, new CacheEntry(result, System.currentTimeMillis()));
        }
        return result;
    }
}
