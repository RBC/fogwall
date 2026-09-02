package com.rbc.fogwall.service;

import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.provider.ProviderRegistry;
import com.rbc.fogwall.provider.SshKeyFingerprintLookup;
import com.rbc.fogwall.user.UserEntry;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
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
 * <h2>Resolver, not the gate</h2>
 *
 * <p>This service only resolves — it returns the matching SCM login or {@link Optional#empty()} and never blocks a push
 * itself. The gating decision lives in the caller: {@link com.rbc.fogwall.git.CheckUserPushPermissionHook} fails the
 * SSH push <em>closed</em> when no match is found (unregistered key, or a provider that does not implement
 * {@link SshKeyFingerprintLookup}), so on the SSH path a resolved login is effectively required. A match binds the push
 * record's {@code scmUsername} field for attribution in the dashboard and audit log.
 *
 * <h2>Caching</h2>
 *
 * <p>SSH keys change infrequently. Results are cached by {@code (providerId, scmLogin)} with a configurable TTL
 * (default: 7 days) to avoid hammering provider APIs. In corporate environments with shared egress IPs, unauthenticated
 * API rate limits apply to the whole enterprise — a long TTL is the primary mitigation.
 *
 * <p>When a {@link SshFingerprintCache} is provided, it is used as the primary cache (survives restarts and is shared
 * across nodes). A secondary in-memory cache avoids hitting the DB on every push for the same user within a JVM
 * lifetime. When no persistent cache is provided, only the in-memory cache is used.
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

    private final Map<CacheKey, CacheEntry> memCache = new ConcurrentHashMap<>();
    private final SshFingerprintCache persistentCache;
    private final long ttlMs;
    private final ProviderRegistry providerRegistry;

    public SshScmIdentityEnricher() {
        this(DEFAULT_TTL, null);
    }

    public SshScmIdentityEnricher(Duration ttl, SshFingerprintCache persistentCache) {
        this(ttl, persistentCache, null);
    }

    /**
     * @param providerRegistry used to recognize an SCM identity linked under a different provider config entry that
     *     shares the same type and host as {@code provider} in {@link #resolveScmLogin} — needed because HTTP and SSH
     *     access to the same upstream currently require two separate provider entries (see fogwall#531 for the
     *     follow-up to eliminate that duplication). {@code null} disables this and falls back to requiring an exact
     *     provider-name match, same as before this parameter existed.
     */
    public SshScmIdentityEnricher(
            Duration ttl, SshFingerprintCache persistentCache, ProviderRegistry providerRegistry) {
        this.ttlMs = ttl.toMillis();
        this.persistentCache = persistentCache;
        this.providerRegistry = providerRegistry;
    }

    /**
     * Attempts to resolve the SCM login of {@code user} by matching {@code connectingFingerprint} against the SSH keys
     * registered for each of the user's linked SCM identities on {@code provider}.
     *
     * <p>An identity is considered linked to {@code provider} if it's recorded under {@code provider}'s own name, OR
     * under any other configured provider that shares the same type and host — since HTTP and SSH access to the same
     * upstream currently require two separate provider config entries (e.g. {@code github} for OAuth linking,
     * {@code github-ssh} for the SSH transport), an identity linked via one must still be recognized on the other. See
     * fogwall#531 for the follow-up to remove that duplication so this same-host matching isn't needed at all.
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

        Set<String> compatibleProviderNames = compatibleProviderNames(provider);
        return user.getScmIdentities().stream()
                .filter(id -> compatibleProviderNames.contains(id.getProvider()))
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
     * Provider names whose SCM identities should be considered linked to {@code provider} for SSH resolution:
     * {@code provider}'s own name, plus any other configured provider sharing the same type and host. Falls back to
     * just {@code provider}'s own name when no registry was supplied.
     */
    private Set<String> compatibleProviderNames(FogwallProvider provider) {
        if (providerRegistry == null) {
            return Set.of(provider.getProviderId());
        }
        return providerRegistry.getProviders().stream()
                .filter(p -> p.getType().equals(provider.getType())
                        && p.getUri().getHost() != null
                        && p.getUri()
                                .getHost()
                                .equalsIgnoreCase(provider.getUri().getHost()))
                .map(FogwallProvider::getProviderId)
                .collect(Collectors.toSet());
    }

    /**
     * Evicts the cached fingerprint set for {@code (providerId, login)} from both caches. Call when a user adds or
     * removes an SSH key so the next lookup re-fetches immediately rather than waiting for TTL expiry.
     */
    public void evict(String providerId, String login) {
        memCache.remove(new CacheKey(providerId, login));
        if (persistentCache != null) {
            persistentCache.evict(providerId, login);
        }
    }

    private Set<String> fingerprints(String providerId, String login, SshKeyFingerprintLookup lookup) {
        var key = new CacheKey(providerId, login);

        // Check in-memory cache first
        var memEntry = memCache.get(key);
        if (memEntry != null && !memEntry.isExpired(ttlMs)) {
            log.debug("SSH fingerprint memory cache hit for {}/{}", providerId, login);
            return memEntry.fingerprints();
        }

        // Check persistent cache
        if (persistentCache != null) {
            Set<String> persisted = persistentCache.lookup(providerId, login);
            if (!persisted.isEmpty()) {
                log.debug("SSH fingerprint persistent cache hit for {}/{}", providerId, login);
                memCache.put(key, new CacheEntry(persisted, System.currentTimeMillis()));
                return persisted;
            }
        }

        log.debug("SSH fingerprint cache miss for {}/{} — fetching from provider", providerId, login);
        Set<String> result = lookup.fetchSshFingerprints(login);
        if (!result.isEmpty()) {
            // Normalise to a sorted set so storage order from the SCM API doesn't affect equality
            Set<String> normalised = Set.copyOf(new TreeSet<>(result));
            memCache.put(key, new CacheEntry(normalised, System.currentTimeMillis()));
            if (persistentCache != null) {
                persistentCache.store(providerId, login, normalised);
            }
            return normalised;
        }
        return result;
    }
}
