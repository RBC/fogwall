package com.rbc.fogwall.user;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.jdbc.DataSourceFactory;
import com.rbc.fogwall.db.jdbc.JdbcPushStore;
import com.rbc.fogwall.service.JdbcScmTokenCache;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * {@link JdbcGitCredentialStore} on an H2 in-memory database. {@code user_git_credentials.username} has a foreign key
 * onto {@code proxy_users}, so the referenced users are created through {@link JdbcUserStore} first.
 */
class JdbcGitCredentialStoreTest {

    private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");

    JdbcGitCredentialStore store;
    JdbcUserStore userStore;

    @BeforeEach
    void setUp() {
        DataSource ds = DataSourceFactory.h2InMemory("git-credential-store-test-" + UUID.randomUUID());
        new JdbcPushStore(ds).initialize();
        userStore = new JdbcUserStore(ds, new JdbcScmTokenCache(ds, Duration.ofDays(1)));
        userStore.upsertAll(List.of(user("alice"), user("bob")));
        store = new JdbcGitCredentialStore(ds);
    }

    private static UserEntry user(String username) {
        return UserEntry.builder()
                .username(username)
                .passwordHash("{noop}pw")
                .emails(List.of())
                .scmIdentities(List.of())
                .build();
    }

    private static GitCredential credential(String id, String username, String name) {
        return new GitCredential(id, username, name, "{sha256}hash-" + id, NOW, NOW.plus(30, ChronoUnit.DAYS), null);
    }

    @Test
    void save_thenFindById_roundTripsEveryField() {
        Instant lastUsed = NOW.minus(1, ChronoUnit.HOURS);
        store.save(new GitCredential("id-1", "alice", "laptop", "{sha256}h", NOW, NOW.plusSeconds(60), lastUsed));

        GitCredential found = store.findById("id-1").orElseThrow();

        assertEquals("id-1", found.id());
        assertEquals("alice", found.username());
        assertEquals("laptop", found.name());
        assertEquals("{sha256}h", found.secretHash());
        assertEquals(NOW, found.createdAt());
        assertEquals(NOW.plusSeconds(60), found.expiresAt());
        assertEquals(lastUsed, found.lastUsedAt());
    }

    @Test
    void save_nullExpiryAndLastUsed_persistAsNull() {
        store.save(new GitCredential("id-1", "alice", "laptop", "{sha256}h", NOW, null, null));

        GitCredential found = store.findById("id-1").orElseThrow();
        assertNull(found.expiresAt());
        assertNull(found.lastUsedAt());
    }

    @Test
    void findById_unknown_isEmpty() {
        assertTrue(store.findById("nope").isEmpty());
    }

    @Test
    void findByUsername_isOrderedByName_andScopedToTheUser() {
        store.save(credential("id-1", "alice", "workstation"));
        store.save(credential("id-2", "alice", "ci"));
        store.save(credential("id-3", "alice", "laptop"));
        store.save(credential("id-4", "bob", "laptop"));

        List<GitCredential> found = store.findByUsername("alice");

        assertEquals(
                List.of("ci", "laptop", "workstation"),
                found.stream().map(GitCredential::name).toList());
        assertEquals(List.of(), store.findByUsername("nobody"));
    }

    @Test
    void save_duplicateNameForTheSameUser_conflicts() {
        store.save(credential("id-1", "alice", "laptop"));

        assertThrows(GitCredentialNameConflictException.class, () -> store.save(credential("id-2", "alice", "laptop")));
        assertEquals(1, store.findByUsername("alice").size());
    }

    @Test
    void save_sameNameForDifferentUsers_isAllowed() {
        store.save(credential("id-1", "alice", "laptop"));
        store.save(credential("id-2", "bob", "laptop"));

        assertEquals(1, store.findByUsername("alice").size());
        assertEquals(1, store.findByUsername("bob").size());
    }

    @Test
    void replaceSecret_replacesHashAndValidity_andClearsLastUsed() {
        store.save(new GitCredential("id-1", "alice", "laptop", "{sha256}old", NOW, NOW.plusSeconds(60), NOW));
        Instant later = NOW.plus(1, ChronoUnit.DAYS);

        assertTrue(store.replaceSecret("id-1", "{sha256}new", later, later.plusSeconds(60)));

        GitCredential found = store.findById("id-1").orElseThrow();
        assertEquals("{sha256}new", found.secretHash());
        assertEquals(later, found.createdAt());
        assertEquals(later.plusSeconds(60), found.expiresAt());
        assertNull(found.lastUsedAt());
        assertEquals("laptop", found.name());
    }

    @Test
    void replaceSecret_unknownId_isFalseAndCreatesNothing() {
        assertFalse(store.replaceSecret("nope", "{sha256}new", NOW, null));
        assertTrue(store.findById("nope").isEmpty());
    }

    @Test
    void recordUse_setsLastUsedAt() {
        store.save(credential("id-1", "alice", "laptop"));

        store.recordUse("id-1", NOW.plusSeconds(5));

        assertEquals(NOW.plusSeconds(5), store.findById("id-1").orElseThrow().lastUsedAt());
    }

    @Test
    void delete_removesOnlyThatCredential() {
        store.save(credential("id-1", "alice", "laptop"));
        store.save(credential("id-2", "alice", "ci"));

        assertTrue(store.delete("id-1"));

        assertTrue(store.findById("id-1").isEmpty());
        assertTrue(store.findById("id-2").isPresent());
        assertFalse(store.delete("id-1"));
    }

    @Test
    void deleteByUsername_removesTheUsersCredentials_andReportsHowMany() {
        store.save(credential("id-1", "alice", "laptop"));
        store.save(credential("id-2", "alice", "ci"));
        store.save(credential("id-3", "bob", "laptop"));

        assertEquals(2, store.deleteByUsername("alice"));

        assertEquals(List.of(), store.findByUsername("alice"));
        assertEquals(1, store.findByUsername("bob").size());
        assertEquals(0, store.deleteByUsername("alice"));
    }

    // ---- FK on proxy_users ----

    @Test
    void save_forUserNotInProxyUsers_failsTheForeignKey() {
        assertThrows(DataIntegrityViolationException.class, () -> store.save(credential("id-1", "carol", "laptop")));
    }

    @Test
    void deletingTheProxyUser_cascadesToTheirCredentials() {
        store.save(credential("id-1", "alice", "laptop"));
        store.save(credential("id-2", "bob", "laptop"));

        userStore.deleteUser("alice");

        assertTrue(store.findById("id-1").isEmpty());
        assertTrue(store.findById("id-2").isPresent());
    }
}
