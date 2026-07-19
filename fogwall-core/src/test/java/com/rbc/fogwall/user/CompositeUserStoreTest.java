package com.rbc.fogwall.user;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.jdbc.DataSourceFactory;
import com.rbc.fogwall.db.jdbc.JdbcPushStore;
import com.rbc.fogwall.service.JdbcScmTokenCache;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CompositeUserStore}: config-first reads, JDBC-only writes, merged findAll.
 *
 * <p>Each test gets its own isolated H2 database to prevent state leakage.
 */
class CompositeUserStoreTest {

    CompositeUserStore store;
    JdbcUserStore jdbcStore;

    private static final UserEntry CONFIG_ALICE = UserEntry.builder()
            .username("alice")
            .passwordHash("{noop}config-pw")
            .emails(List.of("alice@config.com"))
            .scmIdentities(List.of(ScmIdentity.builder()
                    .provider("github")
                    .username("alice-config")
                    .build()))
            .roles(List.of("ADMIN"))
            .build();

    @BeforeEach
    void setUp() {
        DataSource ds = DataSourceFactory.h2InMemory("composite-test-" + UUID.randomUUID());
        JdbcPushStore pushStore = new JdbcPushStore(ds);
        pushStore.initialize();
        jdbcStore = new JdbcUserStore(ds, new JdbcScmTokenCache(ds, Duration.ofDays(1)));
        StaticUserStore configStore = new StaticUserStore(List.of(CONFIG_ALICE));
        store = new CompositeUserStore(configStore, jdbcStore);
    }

    // ── config-first reads ───────────────────────────────────────────────────────

    @Test
    void findByUsername_configUser_returnsConfigVersion() {
        // alice also exists in DB with different data — config must win
        jdbcStore.createUser("alice", "{noop}db-pw", "USER");
        var result = store.findByUsername("alice");
        assertTrue(result.isPresent());
        assertEquals("{noop}config-pw", result.get().getPasswordHash());
        assertEquals(List.of("ADMIN"), result.get().getRoles());
    }

    @Test
    void findByUsername_dbOnlyUser_returnsDbVersion() {
        jdbcStore.createUser("bob", "{noop}bob-pw", "USER");
        var result = store.findByUsername("bob");
        assertTrue(result.isPresent());
        assertEquals("{noop}bob-pw", result.get().getPasswordHash());
    }

    @Test
    void findByUsername_unknown_returnsEmpty() {
        assertTrue(store.findByUsername("nobody").isEmpty());
    }

    @Test
    void findByEmail_configUser_found() {
        assertTrue(store.findByEmail("alice@config.com").isPresent());
    }

    @Test
    void findByEmail_dbUser_found() {
        jdbcStore.createUser("bob", "{noop}pw", "USER");
        jdbcStore.addEmail("bob", "bob@example.com");
        assertTrue(store.findByEmail("bob@example.com").isPresent());
    }

    @Test
    void findByScmIdentity_configUser_found() {
        assertTrue(store.findByScmIdentity("github", "alice-config").isPresent());
    }

    @Test
    void findByScmIdentity_dbUser_found() {
        jdbcStore.createUser("bob", "{noop}pw", "USER");
        jdbcStore.addScmIdentity("bob", "github", "bob-gh");
        assertTrue(store.findByScmIdentity("github", "bob-gh").isPresent());
    }

    @Test
    void findBySshFingerprint_unknown_returnsEmpty() {
        assertTrue(store.findBySshFingerprint("SHA256:nope").isEmpty());
    }

    @Test
    void findBySshFingerprint_dbOnlyUser_returnsDbVersion() {
        jdbcStore.createUser("bob", "{noop}pw", "USER");
        jdbcStore.addSshKey("bob", "SHA256:bobkey", "ssh-ed25519 AAAAbob", "bob's key");
        var result = store.findBySshFingerprint("SHA256:bobkey");
        assertTrue(result.isPresent());
        assertEquals("bob", result.get().getUsername());
    }

    @Test
    void findBySshFingerprint_configUserWithDbAddedKey_mergesConfigScmIdentities() {
        // alice is config-defined with a config scmIdentity, but adds her SSH key via the dashboard (DB-only) —
        // resolving her by that key's fingerprint must still surface her config-declared scmIdentities (#429).
        store.addSshKey("alice", "SHA256:alicekey", "ssh-ed25519 AAAAalice", "alice's key");

        var result = store.findBySshFingerprint("SHA256:alicekey");

        assertTrue(result.isPresent());
        assertEquals("alice", result.get().getUsername());
        assertEquals(1, result.get().getScmIdentities().size());
        assertEquals("alice-config", result.get().getScmIdentities().get(0).getUsername());
        assertEquals(List.of("alice@config.com"), result.get().getEmails());
    }

    // ── findAll merges both ──────────────────────────────────────────────────────

    @Test
    void findAll_mergesBothStores_configTakesPrecedenceOnCollision() {
        jdbcStore.createUser("alice", "{noop}db-pw", "USER"); // collision with config alice
        jdbcStore.createUser("bob", "{noop}bob-pw", "USER");

        var all = store.findAll();
        assertEquals(2, all.size());
        var aliceResult = all.stream()
                .filter(u -> "alice".equals(u.getUsername()))
                .findFirst()
                .orElseThrow();
        // config alice wins
        assertEquals("{noop}config-pw", aliceResult.getPasswordHash());
    }

    // ── enriched queries ────────────────────────────────────────────────────────

    @Test
    void findEmailsWithVerified_configUser_returnsConfigEmails_lockedTrue() {
        var emails = store.findEmailsWithVerified("alice");
        assertEquals(1, emails.size());
        assertEquals("alice@config.com", emails.get(0).get("email"));
        assertEquals("config", emails.get(0).get("source"));
        assertEquals(true, emails.get(0).get("locked"));
    }

    @Test
    void findEmailsWithVerified_jdbcUser_returnsJdbcEmails() {
        jdbcStore.createUser("bob", "{noop}pw", "USER");
        jdbcStore.addEmail("bob", "bob@example.com");
        var emails = store.findEmailsWithVerified("bob");
        assertEquals(1, emails.size());
        assertEquals("bob@example.com", emails.get(0).get("email"));
    }

    @Test
    void findScmIdentitiesWithVerified_configUser_returnsConfigIdentities() {
        var ids = store.findScmIdentitiesWithVerified("alice");
        assertEquals(1, ids.size());
        assertEquals("github", ids.get(0).get("provider"));
        assertEquals("alice-config", ids.get(0).get("username"));
    }

    @Test
    void findScmIdentitiesWithVerified_jdbcUser_returnsJdbcIdentities() {
        jdbcStore.createUser("bob", "{noop}pw", "USER");
        jdbcStore.addScmIdentity("bob", "github", "bob-gh");
        var ids = store.findScmIdentitiesWithVerified("bob");
        assertEquals(1, ids.size());
        assertEquals("bob-gh", ids.get(0).get("username"));
    }

    // ── config users: supplemental emails/identities stored in JDBC ─────────────

    @Test
    void addEmail_configUser_storesInJdbc() {
        store.addEmail("alice", "alice-extra@example.com");
        assertTrue(store.findByEmail("alice-extra@example.com").isPresent());
    }

    @Test
    void addEmail_configUser_configEmailIsNoOp() {
        assertDoesNotThrow(() -> store.addEmail("alice", "alice@config.com"));
    }

    @Test
    void removeEmail_configUser_configEmail_throwsLockedEmailException() {
        assertThrows(LockedEmailException.class, () -> store.removeEmail("alice", "alice@config.com"));
    }

    @Test
    void removeEmail_configUser_supplementalEmail_succeeds() {
        store.addEmail("alice", "alice-extra@example.com");
        assertDoesNotThrow(() -> store.removeEmail("alice", "alice-extra@example.com"));
        assertTrue(store.findByEmail("alice-extra@example.com").isEmpty());
    }

    @Test
    void addScmIdentity_configUser_storesInJdbc() {
        store.addScmIdentity("alice", "gitlab", "alice-gl");
        assertTrue(store.findByScmIdentity("gitlab", "alice-gl").isPresent());
    }

    @Test
    void addScmIdentity_configUser_configIdentityIsNoOp() {
        assertDoesNotThrow(() -> store.addScmIdentity("alice", "github", "alice-config"));
    }

    @Test
    void removeScmIdentity_configUser_configIdentity_throwsLockedByConfigException() {
        assertThrows(LockedByConfigException.class, () -> store.removeScmIdentity("alice", "github", "alice-config"));
    }

    @Test
    void removeScmIdentity_configUser_supplementalIdentity_succeeds() {
        store.addScmIdentity("alice", "gitlab", "alice-gl");
        assertDoesNotThrow(() -> store.removeScmIdentity("alice", "gitlab", "alice-gl"));
    }

    @Test
    void findEmailsWithVerified_configUser_withSupplemental_returnsBoth() {
        store.addEmail("alice", "alice-extra@example.com");
        var emails = store.findEmailsWithVerified("alice");
        assertEquals(2, emails.size());
        assertTrue(emails.stream()
                .anyMatch(e -> "alice@config.com".equals(e.get("email")) && Boolean.TRUE.equals(e.get("locked"))));
        assertTrue(emails.stream().anyMatch(e -> "alice-extra@example.com".equals(e.get("email"))));
    }

    @Test
    void findScmIdentitiesWithVerified_configUser_withSupplemental_returnsBoth() {
        store.addScmIdentity("alice", "gitlab", "alice-gl");
        var ids = store.findScmIdentitiesWithVerified("alice");
        assertEquals(2, ids.size());
        assertTrue(ids.stream()
                .anyMatch(id -> "github".equals(id.get("provider")) && "alice-config".equals(id.get("username"))));
        assertTrue(ids.stream()
                .anyMatch(id -> "gitlab".equals(id.get("provider")) && "alice-gl".equals(id.get("username"))));
    }

    // ── writes delegate to JDBC ──────────────────────────────────────────────────

    @Test
    void createUser_createsInJdbc() {
        store.createUser("charlie", "{noop}pw", "USER");
        assertTrue(jdbcStore.findByUsername("charlie").isPresent());
    }

    @Test
    void upsertUser_createsInJdbc() {
        store.upsertUser("oidcuser");
        assertTrue(jdbcStore.findByUsername("oidcuser").isPresent());
    }

    @Test
    void addEmail_addsToJdbc() {
        jdbcStore.createUser("bob", "{noop}pw", "USER");
        store.addEmail("bob", "bob@example.com");
        assertTrue(jdbcStore.findByEmail("bob@example.com").isPresent());
    }

    @Test
    void deleteUser_removesFromJdbc() {
        jdbcStore.createUser("bob", "{noop}pw", "USER");
        store.deleteUser("bob");
        assertTrue(jdbcStore.findByUsername("bob").isEmpty());
    }
}
