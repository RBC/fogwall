package com.rbc.fogwall.db.memory;

import com.rbc.fogwall.db.UrlRuleRegistry;
import com.rbc.fogwall.db.model.AccessRule;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link UrlRuleRegistry}. Data is lost on restart. */
public class InMemoryUrlRuleRegistry implements UrlRuleRegistry {

    private final Map<String, AccessRule> store = new ConcurrentHashMap<>();

    @Override
    public void initialize() {}

    @Override
    public void save(AccessRule rule) {
        store.put(rule.getId(), rule);
    }

    @Override
    public void update(AccessRule rule) {
        store.put(rule.getId(), rule);
    }

    @Override
    public void delete(String id) {
        store.remove(id);
    }

    @Override
    public Optional<AccessRule> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<AccessRule> findAll() {
        return store.values().stream()
                .sorted(Comparator.comparingInt(AccessRule::getRuleOrder).thenComparing(AccessRule::getId))
                .toList();
    }

    @Override
    public List<AccessRule> findEnabledForProvider(String provider) {
        return store.values().stream()
                .filter(AccessRule::isEnabled)
                .filter(r -> r.getProvider() == null || r.getProvider().equals(provider))
                .sorted(Comparator.comparingInt(AccessRule::getRuleOrder).thenComparing(AccessRule::getId))
                .toList();
    }
}
