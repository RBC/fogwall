package com.rbc.fogwall.provider;

import com.rbc.fogwall.db.UrlRuleRegistry;
import java.util.List;
import java.util.Optional;

/**
 * Registry of configured git proxy providers. Keyed by the user-configured name (the YAML config map key, e.g.
 * {@code github}, {@code internal-gitlab}), which is also the canonical provider ID stored in the database.
 *
 * <p>Consistent with {@link UrlRuleRegistry} — a lookup/discovery mechanism, not a CRUD store.
 */
public interface ProviderRegistry {

    /**
     * Look up a provider by name (the YAML map key, e.g. {@code "github"}).
     *
     * @return the provider, or {@link Optional#empty()} if not found
     */
    Optional<FogwallProvider> getProvider(String name);

    /** Returns all registered providers. */
    List<FogwallProvider> getProviders();

    /**
     * Resolves a provider by name (the YAML config map key, e.g. {@code "github"}).
     *
     * @return the provider, or {@link Optional#empty()} if not found
     */
    default Optional<FogwallProvider> resolveProvider(String name) {
        return getProvider(name);
    }
}
