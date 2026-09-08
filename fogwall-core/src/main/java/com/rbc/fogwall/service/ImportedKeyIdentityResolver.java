package com.rbc.fogwall.service;

import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.user.ScmIdentity;
import com.rbc.fogwall.user.SshKeyEntry;
import com.rbc.fogwall.user.UserEntry;
import java.util.List;
import java.util.Optional;

/**
 * Resolves the SCM login behind an SSH connection from what OAuth linking already proved, without calling the provider.
 *
 * <p>This is the strict-mode counterpart to {@link SshScmIdentityEnricher}. The enricher asks the provider which keys a
 * login has registered; this asks fogwall's own database whether the connecting key is one that an authenticated OAuth
 * session imported for this user, on this provider. Both answer the same question — which SCM login is pushing — from
 * different evidence.
 *
 * <p>Two conditions, and both are required:
 *
 * <ul>
 *   <li>The connecting key carries a source naming this provider. A source is only ever recorded on the import path, so
 *       it means an authenticated OAuth session listed this key on the linked account.
 *   <li>The user holds a verified identity for that provider, so the account the key came from is one fogwall watched
 *       an OAuth flow prove.
 * </ul>
 *
 * <p>A key with no provider source resolves nothing here even when the provider would confirm it upstream. That is the
 * point: the live lookup reads a public endpoint and answers "this key is on that account", which is a weaker claim
 * than "we watched this user authenticate as that account and took these keys from it".
 *
 * <p>The key's {@code locked} flag is not part of the test. A user can add a key by hand and link the account later,
 * and import then records the provider source on the row it already has without relocking it — the same key, proven the
 * same way, so requiring {@code locked} would refuse it.
 */
public final class ImportedKeyIdentityResolver implements SshScmLoginResolver {

    @Override
    public Optional<String> resolveScmLogin(UserEntry user, FogwallProvider provider, String connectingFingerprint) {
        return resolve(user, provider != null ? provider.getProviderId() : null, connectingFingerprint);
    }

    /**
     * Returns the verified SCM login proven by {@code connectingFingerprint}, or empty when the key was not imported
     * from {@code providerId} or the user holds no verified identity there.
     */
    public static Optional<String> resolve(UserEntry user, String providerId, String connectingFingerprint) {
        if (user == null || providerId == null || connectingFingerprint == null) {
            return Optional.empty();
        }
        if (!wasImportedFrom(user.getSshKeys(), providerId, connectingFingerprint)) {
            return Optional.empty();
        }
        return verifiedLoginFor(user.getScmIdentities(), providerId);
    }

    /** Whether the connecting key is one this provider's OAuth linking imported, rather than a self-asserted one. */
    private static boolean wasImportedFrom(List<SshKeyEntry> keys, String providerId, String connectingFingerprint) {
        return keys.stream()
                .anyMatch(key -> connectingFingerprint.equals(key.getFingerprint()) && key.isVouchedForBy(providerId));
    }

    private static Optional<String> verifiedLoginFor(List<ScmIdentity> identities, String providerId) {
        return identities.stream()
                .filter(identity -> providerId.equalsIgnoreCase(identity.getProvider()))
                .filter(ScmIdentity::isVerified)
                .map(ScmIdentity::getUsername)
                .findFirst();
    }
}
