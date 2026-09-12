package com.rbc.fogwall.db.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rbc.fogwall.db.model.ScmApiEntityRecord;
import com.rbc.fogwall.db.model.ScmApiEntityRecord.Kind;
import com.rbc.fogwall.db.model.ScmApiEntityRecord.State;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Integration tests for {@link JdbcScmApiEntityStore} backed by an H2 in-memory database. */
class JdbcScmApiEntityStoreIntegrationTest {

    JdbcScmApiEntityStore store;

    @BeforeEach
    void setUp() {
        DataSource ds = DataSourceFactory.h2InMemory("scm-api-entity-test-" + UUID.randomUUID());
        store = new JdbcScmApiEntityStore(ds);
        store.initialize();
    }

    private static ScmApiEntityRecord row(String provider, Kind kind, int number, String nodeId) {
        return ScmApiEntityRecord.builder()
                .provider(provider)
                .repoOwner("acme")
                .repoName("widgets")
                .kind(kind)
                .number(number)
                .url("https://example.test/" + number)
                .nodeId(nodeId)
                .title("t")
                .state(State.OPEN)
                .createdBy("alice")
                .createdByScmUsername("alice-scm")
                .createdActionId("a1")
                .lastActionId("a1")
                .build();
    }

    @Test
    void save_thenFindByTargetAndNodeId_roundTrips() {
        ScmApiEntityRecord r = row("github", Kind.PULL_REQUEST, 7, "PR_1");
        store.save(r);
        ScmApiEntityRecord found = store.findByTarget("github", "acme", "widgets", Kind.PULL_REQUEST, 7)
                .orElseThrow();
        assertEquals(r.getId(), found.getId());
        assertEquals("alice-scm", found.getCreatedByScmUsername());
        assertEquals(State.OPEN, found.getState());
        assertEquals(
                r.getId(), store.findByNodeId("github", "PR_1").orElseThrow().getId());
        assertTrue(
                store.findByTarget("github", "acme", "widgets", Kind.ISSUE, 7).isEmpty(), "kind is part of the key");
    }

    @Test
    void update_replacesTheMutableFields() {
        ScmApiEntityRecord r = row("gitea", Kind.ISSUE, 2, null);
        store.save(r);
        r.setState(State.CLOSED);
        r.setTitle("edited");
        r.setLastActionId("a2");
        store.update(r);
        ScmApiEntityRecord found = store.findById(r.getId()).orElseThrow();
        assertEquals(State.CLOSED, found.getState());
        assertEquals("edited", found.getTitle());
        assertEquals("a2", found.getLastActionId());
        assertEquals("a1", found.getCreatedActionId());
    }

    @Test
    void findByIds_returnsOnlyWhatExists() {
        ScmApiEntityRecord a = row("gitlab", Kind.ISSUE, 1, null);
        ScmApiEntityRecord b = row("gitlab", Kind.PULL_REQUEST, 1, null);
        store.save(a);
        store.save(b);
        List<ScmApiEntityRecord> found = store.findByIds(List.of(a.getId(), b.getId(), "missing"));
        assertEquals(2, found.size());
        assertTrue(store.findByIds(List.of()).isEmpty());
    }
}
