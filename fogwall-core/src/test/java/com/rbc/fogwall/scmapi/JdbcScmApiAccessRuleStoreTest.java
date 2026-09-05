package com.rbc.fogwall.scmapi;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.jdbc.DataSourceFactory;
import com.rbc.fogwall.db.jdbc.JdbcPushStore;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Integration tests for {@link JdbcScmApiAccessRuleStore} backed by an H2 in-memory database. */
class JdbcScmApiAccessRuleStoreTest {

    JdbcScmApiAccessRuleStore store;

    @BeforeEach
    void setUp() {
        DataSource ds = DataSourceFactory.h2InMemory("scm-api-access-rule-test-" + UUID.randomUUID());
        new JdbcPushStore(ds).initialize();
        store = new JdbcScmApiAccessRuleStore(ds);
    }

    @Test
    void findAll_empty_returnsEmptyList() {
        assertTrue(store.findAll().isEmpty());
    }

    @Test
    void save_thenFindByProvider_roundTrips() {
        ScmApiAccessRule rule = ScmApiAccessRule.builder()
                .provider("github")
                .operation(ScmApiAccessRule.Operation.READ)
                .access(ScmApiAccessRule.Access.ALLOW)
                .source(ScmApiAccessRule.Source.DB)
                .build();
        store.save(rule);

        List<ScmApiAccessRule> found = store.findByProvider("github");
        assertEquals(1, found.size());
        assertEquals(rule.getId(), found.get(0).getId());
        assertEquals(ScmApiAccessRule.Operation.READ, found.get(0).getOperation());
        assertEquals(ScmApiAccessRule.Access.ALLOW, found.get(0).getAccess());
    }

    @Test
    void findByProvider_otherProvider_returnsEmpty() {
        store.save(ScmApiAccessRule.builder().provider("github").build());
        assertTrue(store.findByProvider("gitlab").isEmpty());
    }

    @Test
    void delete_removesRule() {
        ScmApiAccessRule rule = ScmApiAccessRule.builder().provider("github").build();
        store.save(rule);
        store.delete(rule.getId());
        assertTrue(store.findAll().isEmpty());
    }

    @Test
    void save_sameId_replacesExisting() {
        ScmApiAccessRule rule = ScmApiAccessRule.builder()
                .provider("github")
                .access(ScmApiAccessRule.Access.ALLOW)
                .build();
        store.save(rule);
        store.save(ScmApiAccessRule.builder()
                .id(rule.getId())
                .provider("github")
                .access(ScmApiAccessRule.Access.DENY)
                .build());

        List<ScmApiAccessRule> found = store.findAll();
        assertEquals(1, found.size());
        assertEquals(ScmApiAccessRule.Access.DENY, found.get(0).getAccess());
    }

    @Test
    void seedFromConfig_clearsOnlyConfigSourcedRows() {
        ScmApiAccessRule dbRule = ScmApiAccessRule.builder()
                .provider("github")
                .source(ScmApiAccessRule.Source.DB)
                .build();
        ScmApiAccessRule configRule = ScmApiAccessRule.builder()
                .provider("github")
                .source(ScmApiAccessRule.Source.CONFIG)
                .build();
        store.save(dbRule);
        store.save(configRule);

        store.seedFromConfig(List.of(ScmApiAccessRule.builder()
                .provider("gitlab")
                .source(ScmApiAccessRule.Source.CONFIG)
                .build()));

        List<ScmApiAccessRule> found = store.findAll();
        assertEquals(2, found.size());
        assertTrue(found.stream().anyMatch(r -> r.getId().equals(dbRule.getId())));
        assertTrue(found.stream().anyMatch(r -> r.getProvider().equals("gitlab")));
        assertFalse(found.stream().anyMatch(r -> r.getId().equals(configRule.getId())));
    }
}
