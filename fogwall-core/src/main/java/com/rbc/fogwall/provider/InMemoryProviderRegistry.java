package com.rbc.fogwall.provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.ToString;

/** In-memory {@link ProviderRegistry} backed by a {@link LinkedHashMap} keyed by provider name. */
@ToString
public class InMemoryProviderRegistry implements ProviderRegistry {

    private final Map<String, FogwallProvider> providers;

    /** Construct from a name → provider map. Insertion order is preserved. */
    public InMemoryProviderRegistry(Map<String, FogwallProvider> providers) {
        this.providers = new LinkedHashMap<>(providers);
    }

    /** Construct from a list of providers; each provider's {@link FogwallProvider#getName()} is used as the key. */
    public InMemoryProviderRegistry(List<FogwallProvider> providerList) {
        this.providers = new LinkedHashMap<>();
        for (FogwallProvider p : providerList) {
            providers.put(p.getName(), p);
        }
    }

    @Override
    public Optional<FogwallProvider> getProvider(String name) {
        return Optional.ofNullable(providers.get(name));
    }

    @Override
    public List<FogwallProvider> getProviders() {
        return new ArrayList<>(providers.values());
    }
}
