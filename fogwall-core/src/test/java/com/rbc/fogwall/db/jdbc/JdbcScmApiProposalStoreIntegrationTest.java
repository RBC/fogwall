package com.rbc.fogwall.db.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rbc.fogwall.db.model.ScmApiProposalRecord;
import com.rbc.fogwall.db.model.ScmApiProposalRecord.Kind;
import com.rbc.fogwall.db.model.ScmApiProposalRecord.State;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Integration tests for {@link JdbcScmApiProposalStore} backed by an H2 in-memory database. */
class JdbcScmApiProposalStoreIntegrationTest {

    JdbcScmApiProposalStore store;

    @BeforeEach
    void setUp() {
        DataSource ds = DataSourceFactory.h2InMemory("scm-api-proposal-test-" + UUID.randomUUID());
        store = new JdbcScmApiProposalStore(ds);
        store.initialize();
    }

    private static ScmApiProposalRecord row(String provider, Kind kind, int number, String nodeId) {
        return ScmApiProposalRecord.builder()
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
        ScmApiProposalRecord r = row("github", Kind.PULL_REQUEST, 7, "PR_1");
        store.save(r);
        ScmApiProposalRecord found = store.findByTarget("github", "acme", "widgets", Kind.PULL_REQUEST, 7)
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
        ScmApiProposalRecord r = row("gitea", Kind.ISSUE, 2, null);
        store.save(r);
        r.setState(State.CLOSED);
        r.setTitle("edited");
        r.setLastActionId("a2");
        store.update(r);
        ScmApiProposalRecord found = store.findById(r.getId()).orElseThrow();
        assertEquals(State.CLOSED, found.getState());
        assertEquals("edited", found.getTitle());
        assertEquals("a2", found.getLastActionId());
        assertEquals("a1", found.getCreatedActionId());
    }

    @Test
    void findByIds_returnsOnlyWhatExists() {
        ScmApiProposalRecord a = row("gitlab", Kind.ISSUE, 1, null);
        ScmApiProposalRecord b = row("gitlab", Kind.PULL_REQUEST, 1, null);
        store.save(a);
        store.save(b);
        List<ScmApiProposalRecord> found = store.findByIds(List.of(a.getId(), b.getId(), "missing"));
        assertEquals(2, found.size());
        assertTrue(store.findByIds(List.of()).isEmpty());
    }
}
