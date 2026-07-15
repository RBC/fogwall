package com.rbc.fogwall.db.memory;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.model.AccessRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryUrlRuleRegistryTest {

    InMemoryUrlRuleRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new InMemoryUrlRuleRegistry();
    }

    private AccessRule rule(String value, int order) {
        return AccessRule.builder().value(value).ruleOrder(order).build();
    }

    private AccessRule rule(String value, int order, String provider, boolean enabled) {
        return AccessRule.builder()
                .value(value)
                .ruleOrder(order)
                .provider(provider)
                .enabled(enabled)
                .build();
    }

    @Test
    void save_findById_roundTrip() {
        AccessRule r = rule("/acme/*", 10);
        registry.save(r);

        var found = registry.findById(r.getId());
        assertTrue(found.isPresent());
        assertEquals("/acme/*", found.get().getValue());
    }

    @Test
    void findById_unknownId_empty() {
        assertTrue(registry.findById("missing").isEmpty());
    }

    @Test
    void update_replacesRule() {
        AccessRule r = rule("/acme/*", 10);
        registry.save(r);

        AccessRule updated = AccessRule.builder()
                .id(r.getId())
                .value("/acme/**")
                .ruleOrder(20)
                .build();
        registry.update(updated);

        assertEquals("/acme/**", registry.findById(r.getId()).orElseThrow().getValue());
    }

    @Test
    void delete_removesRule() {
        AccessRule r = rule("/acme/*", 10);
        registry.save(r);
        registry.delete(r.getId());
        assertTrue(registry.findById(r.getId()).isEmpty());
    }

    @Test
    void findAll_sortedByOrderThenId() {
        AccessRule a = rule("/a", 20);
        AccessRule b = rule("/b", 10);
        AccessRule c = rule("/c", 20);
        registry.save(a);
        registry.save(b);
        registry.save(c);

        var all = registry.findAll();
        assertEquals(3, all.size());
        assertEquals("/b", all.get(0).getValue());
    }

    @Test
    void initialize_doesNotThrow() {
        assertDoesNotThrow(() -> registry.initialize());
    }

    @Test
    void findEnabledForProvider_filtersDisabledAndWrongProvider() {
        registry.save(rule("/allow-all", 10, null, true));
        registry.save(rule("/github-only", 20, "github", true));
        registry.save(rule("/gitlab-only", 30, "gitlab", true));
        registry.save(rule("/disabled", 5, null, false));

        var results = registry.findEnabledForProvider("github");
        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(r -> "/allow-all".equals(r.getValue())));
        assertTrue(results.stream().anyMatch(r -> "/github-only".equals(r.getValue())));
    }
}
