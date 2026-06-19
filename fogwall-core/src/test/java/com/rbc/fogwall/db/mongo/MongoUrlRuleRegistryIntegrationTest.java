package com.rbc.fogwall.db.mongo;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.MongoClients;
import com.rbc.fogwall.db.model.AccessRule;
import com.rbc.fogwall.db.model.MatchTarget;
import com.rbc.fogwall.db.model.MatchType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@Tag("integration")
class MongoUrlRuleRegistryIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    MongoUrlRuleRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new MongoUrlRuleRegistry(
                MongoClients.create(MONGO.getConnectionString()),
                "testdb_" + UUID.randomUUID().toString().replace("-", ""));
        registry.initialize();
    }

    private AccessRule rule(String provider, String value) {
        return AccessRule.builder()
                .provider(provider)
                .target(MatchTarget.OWNER)
                .value(value)
                .matchType(MatchType.GLOB)
                .access(AccessRule.Access.ALLOW)
                .operations(AccessRule.Operations.BOTH)
                .enabled(true)
                .build();
    }

    @Test
    void save_andFindById_roundTripsAllFields() {
        AccessRule r = AccessRule.builder()
                .provider("github")
                .target(MatchTarget.SLUG)
                .value("org/repo")
                .matchType(MatchType.LITERAL)
                .access(AccessRule.Access.DENY)
                .operations(AccessRule.Operations.PUSH)
                .description("block pushes")
                .enabled(false)
                .ruleOrder(42)
                .source(AccessRule.Source.CONFIG)
                .build();
        registry.save(r);

        AccessRule found = registry.findById(r.getId()).orElseThrow();
        assertEquals(r.getId(), found.getId());
        assertEquals("github", found.getProvider());
        assertEquals(MatchTarget.SLUG, found.getTarget());
        assertEquals("org/repo", found.getValue());
        assertEquals(MatchType.LITERAL, found.getMatchType());
        assertEquals(AccessRule.Access.DENY, found.getAccess());
        assertEquals(AccessRule.Operations.PUSH, found.getOperations());
        assertEquals("block pushes", found.getDescription());
        assertFalse(found.isEnabled());
        assertEquals(42, found.getRuleOrder());
        assertEquals(AccessRule.Source.CONFIG, found.getSource());
    }

    @Test
    void findById_unknownId_returnsEmpty() {
        assertTrue(registry.findById("no-such-id").isEmpty());
    }

    @Test
    void findAll_returnsSortedByRuleOrderThenId() {
        AccessRule high = AccessRule.builder()
                .id("aaa")
                .target(MatchTarget.OWNER)
                .value("org")
                .matchType(MatchType.GLOB)
                .ruleOrder(10)
                .access(AccessRule.Access.ALLOW)
                .operations(AccessRule.Operations.BOTH)
                .build();
        AccessRule low = AccessRule.builder()
                .id("zzz")
                .target(MatchTarget.OWNER)
                .value("org")
                .matchType(MatchType.GLOB)
                .ruleOrder(200)
                .access(AccessRule.Access.ALLOW)
                .operations(AccessRule.Operations.BOTH)
                .build();
        registry.save(low);
        registry.save(high);

        List<AccessRule> all = registry.findAll();
        assertEquals(2, all.size());
        assertEquals("aaa", all.get(0).getId());
        assertEquals("zzz", all.get(1).getId());
    }

    @Test
    void update_replacesExistingDocument() {
        AccessRule r = rule("github", "org");
        registry.save(r);

        AccessRule updated = AccessRule.builder()
                .id(r.getId())
                .provider("gitlab")
                .target(MatchTarget.OWNER)
                .value("org")
                .matchType(MatchType.GLOB)
                .access(AccessRule.Access.DENY)
                .operations(AccessRule.Operations.FETCH)
                .enabled(false)
                .build();
        registry.update(updated);

        AccessRule found = registry.findById(r.getId()).orElseThrow();
        assertEquals("gitlab", found.getProvider());
        assertEquals(AccessRule.Access.DENY, found.getAccess());
        assertFalse(found.isEnabled());
    }

    @Test
    void delete_removesDocument() {
        AccessRule r = rule("github", "org");
        registry.save(r);
        registry.delete(r.getId());
        assertTrue(registry.findById(r.getId()).isEmpty());
    }

    @Test
    void findEnabledForProvider_returnsMatchingAndNullProviderRules() {
        AccessRule githubRule = rule("github", "gh-org");
        AccessRule gitlabRule = rule("gitlab", "gl-org");
        AccessRule globalRule = AccessRule.builder()
                .target(MatchTarget.OWNER)
                .value("global-org")
                .matchType(MatchType.GLOB)
                .access(AccessRule.Access.ALLOW)
                .operations(AccessRule.Operations.BOTH)
                .enabled(true)
                .build();
        AccessRule disabledRule = AccessRule.builder()
                .provider("github")
                .target(MatchTarget.OWNER)
                .value("disabled-org")
                .matchType(MatchType.GLOB)
                .access(AccessRule.Access.DENY)
                .operations(AccessRule.Operations.BOTH)
                .enabled(false)
                .build();
        registry.save(githubRule);
        registry.save(gitlabRule);
        registry.save(globalRule);
        registry.save(disabledRule);

        List<AccessRule> results = registry.findEnabledForProvider("github");
        assertTrue(results.stream().anyMatch(r -> "gh-org".equals(r.getValue())));
        assertTrue(results.stream().anyMatch(r -> "global-org".equals(r.getValue())));
        assertTrue(results.stream().noneMatch(r -> "gl-org".equals(r.getValue())));
        assertTrue(results.stream().noneMatch(r -> "disabled-org".equals(r.getValue())));
    }

    @Test
    void findEnabledForProvider_emptyStore_returnsEmpty() {
        assertTrue(registry.findEnabledForProvider("github").isEmpty());
    }
}
