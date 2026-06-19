package com.rbc.fogwall.permission;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.jdbc.DataSourceFactory;
import com.rbc.fogwall.db.jdbc.DatabaseMigrator;
import com.rbc.fogwall.db.model.MatchTarget;
import com.rbc.fogwall.db.model.MatchType;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Integration tests for {@link JdbcRepoPermissionStore} backed by an H2 in-memory database.
 *
 * <p>Each test gets its own isolated H2 database to prevent state leakage.
 */
class JdbcRepoPermissionStoreIntegrationTest {

    JdbcRepoPermissionStore store;
    NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        DataSource ds = DataSourceFactory.h2InMemory("perm-test-" + UUID.randomUUID());
        DatabaseMigrator.migrate(ds);
        store = new JdbcRepoPermissionStore(ds);
        jdbc = new NamedParameterJdbcTemplate(ds);
        seedUser("alice");
        seedUser("bob");
    }

    private void seedUser(String username) {
        jdbc.update("INSERT INTO proxy_users (username, roles) VALUES (:u, 'USER')", java.util.Map.of("u", username));
    }

    private RepoPermission perm(String username, String provider, String value) {
        return RepoPermission.builder()
                .username(username)
                .provider(provider)
                .target(MatchTarget.SLUG)
                .value(value)
                .matchType(MatchType.LITERAL)
                .operations(RepoPermission.Operations.PUSH_AND_REVIEW)
                .source(RepoPermission.Source.DB)
                .build();
    }

    // ---- save / findById ----

    @Test
    void save_findById_roundTrip() {
        RepoPermission p = perm("alice", "github", "/owner/repo");
        store.save(p);

        var found = store.findById(p.getId());
        assertTrue(found.isPresent());
        assertEquals("alice", found.get().getUsername());
        assertEquals("github", found.get().getProvider());
        assertEquals(MatchTarget.SLUG, found.get().getTarget());
        assertEquals("/owner/repo", found.get().getValue());
        assertEquals(MatchType.LITERAL, found.get().getMatchType());
        assertEquals(RepoPermission.Operations.PUSH_AND_REVIEW, found.get().getOperations());
        assertEquals(RepoPermission.Source.DB, found.get().getSource());
    }

    @Test
    void findById_notFound_returnsEmpty() {
        assertTrue(store.findById("does-not-exist").isEmpty());
    }

    // ---- delete ----

    @Test
    void delete_removesRow() {
        RepoPermission p = perm("alice", "github", "/owner/repo");
        store.save(p);
        store.delete(p.getId());
        assertTrue(store.findById(p.getId()).isEmpty());
    }

    // ---- findAll ----

    @Test
    void findAll_returnsAllRows() {
        store.save(perm("alice", "github", "/owner/a"));
        store.save(perm("bob", "github", "/owner/b"));
        assertEquals(2, store.findAll().size());
    }

    // ---- findByUsername ----

    @Test
    void findByUsername_filtersCorrectly() {
        store.save(perm("alice", "github", "/owner/a"));
        store.save(perm("alice", "github", "/owner/b"));
        store.save(perm("bob", "github", "/owner/a"));

        List<RepoPermission> alicePerms = store.findByUsername("alice");
        assertEquals(2, alicePerms.size());
        assertTrue(alicePerms.stream().allMatch(p -> "alice".equals(p.getUsername())));
    }

    // ---- findByProvider ----

    @Test
    void findByProvider_filtersCorrectly() {
        store.save(perm("alice", "github", "/owner/a"));
        store.save(perm("bob", "gitlab", "/owner/b"));

        List<RepoPermission> githubPerms = store.findByProvider("github");
        assertEquals(1, githubPerms.size());
        assertEquals("alice", githubPerms.get(0).getUsername());

        List<RepoPermission> gitlabPerms = store.findByProvider("gitlab");
        assertEquals(1, gitlabPerms.size());
        assertEquals("bob", gitlabPerms.get(0).getUsername());
    }

    // ---- operations and matchType round-trip ----

    @Test
    void pushOnlyOperation_persistedAndRetrieved() {
        RepoPermission p = RepoPermission.builder()
                .username("alice")
                .provider("github")
                .target(MatchTarget.OWNER)
                .value("myorg")
                .matchType(MatchType.GLOB)
                .operations(RepoPermission.Operations.PUSH)
                .source(RepoPermission.Source.CONFIG)
                .build();
        store.save(p);

        var found = store.findById(p.getId()).orElseThrow();
        assertEquals(RepoPermission.Operations.PUSH, found.getOperations());
        assertEquals(MatchTarget.OWNER, found.getTarget());
        assertEquals(MatchType.GLOB, found.getMatchType());
        assertEquals(RepoPermission.Source.CONFIG, found.getSource());
    }
}
