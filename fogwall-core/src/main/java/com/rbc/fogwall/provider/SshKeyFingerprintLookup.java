package com.rbc.fogwall.provider;

import java.util.Set;

/**
 * Optional capability implemented by {@link FogwallProvider}s that expose a public SSH key listing API.
 *
 * <p>This interface allows fogwall to resolve the SCM identity of a user who has pushed over SSH — without requiring
 * them to present an HTTP token. Instead, fogwall iterates the SCM logins already linked to a proxy user (from
 * {@code user_scm_identities}), fetches each login's registered SSH key fingerprints from the provider's public API,
 * and matches against the fingerprint of the connecting key.
 *
 * <p>This is the SSH analogue of {@link HttpTokenUserLookup}: both answer the question "which SCM user is this?", but
 * via different evidence — a token for HTTP pushes, a public key fingerprint for SSH pushes.
 *
 * <h2>Separation of concerns</h2>
 *
 * <p>Providers implement only the data-fetch primitive ({@code fetchSshFingerprints}). The matching logic — iterating
 * linked SCM logins, checking the cache, comparing fingerprints — lives in
 * {@link com.rbc.fogwall.service.SshScmIdentityEnricher}. Providers have no knowledge of proxy users or how
 * fingerprints are matched; they only know how to query their own API.
 *
 * <h2>Supported providers</h2>
 *
 * <ul>
 *   <li>{@link GitHubProvider} — {@code GET /users/{login}/keys} (unauthenticated, public)
 *   <li>{@link GitLabProvider} — {@code GET /api/v4/users?username={login}} then {@code GET /api/v4/users/{id}/keys}
 *       (unauthenticated for public accounts)
 *   <li>{@link ForgejoProvider} — {@code GET /api/v1/users/{login}/keys} (unauthenticated for public instances)
 * </ul>
 *
 * <h2>Rate limits and caching</h2>
 *
 * <p>All three providers allow unauthenticated reads of public SSH keys, but at lower rate limits than authenticated
 * requests. In corporate environments with shared egress IPs, the unauthenticated quota may be exhausted by other
 * processes on the same network. For this reason:
 *
 * <ul>
 *   <li>Results are cached by the {@link com.rbc.fogwall.service.SshFingerprintCache} with a configurable TTL (default:
 *       7 days). SSH keys change infrequently, so a long TTL is appropriate.
 *   <li>On any API error, {@code fetchSshFingerprints} returns an empty set and logs a warning — identity enrichment
 *       degrades gracefully without blocking the push.
 * </ul>
 */
public interface SshKeyFingerprintLookup {

    /**
     * Fetch the SHA-256 fingerprints of all SSH public keys registered by {@code login} on this provider.
     *
     * <p>Fingerprints are in OpenSSH format: {@code SHA256:<base64>}. Implementations compute fingerprints from the raw
     * public key strings returned by the API — raw key material is never stored or returned.
     *
     * <p>Returns an empty set (never throws) when the user is not found, the API is unreachable, or the response cannot
     * be parsed. Callers should log the absence and degrade gracefully.
     *
     * @param login the SCM username (e.g. {@code "coopernetes"}) — not the fogwall proxy username
     * @return SHA-256 fingerprints of all SSH public keys registered for this login, or empty if none found
     */
    Set<String> fetchSshFingerprints(String login);
}
