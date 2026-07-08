package com.rbc.fogwall.service;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.jdbc.DataSourceFactory;
import com.rbc.fogwall.db.jdbc.JdbcPushStore;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcSshFingerprintCacheTest {

    JdbcSshFingerprintCache cache;

    @BeforeEach
    void setUp() {
        DataSource ds = DataSourceFactory.h2InMemory("ssh-fp-test-" + UUID.randomUUID());
        new JdbcPushStore(ds).initialize();
        cache = new JdbcSshFingerprintCache(ds, Duration.ofMinutes(30));
    }

    @Test
    void lookup_emptyCache_returnsEmptySet() {
        assertTrue(cache.lookup("github", "alice").isEmpty());
    }

    @Test
    void store_thenLookup_returnsCachedFingerprints() {
        cache.store("github", "alice", Set.of("SHA256:abc", "SHA256:def"));

        Set<String> result = cache.lookup("github", "alice");
        assertEquals(Set.of("SHA256:abc", "SHA256:def"), result);
    }

    @Test
    void lookup_differentProvider_returnsEmpty() {
        cache.store("github", "alice", Set.of("SHA256:abc"));

        assertTrue(cache.lookup("gitlab", "alice").isEmpty());
    }

    @Test
    void lookup_differentLogin_returnsEmpty() {
        cache.store("github", "alice", Set.of("SHA256:abc"));

        assertTrue(cache.lookup("github", "bob").isEmpty());
    }

    @Test
    void store_expiredEntry_returnsEmpty() {
        DataSource ds = DataSourceFactory.h2InMemory("ssh-fp-expired-" + UUID.randomUUID());
        new JdbcPushStore(ds).initialize();
        JdbcSshFingerprintCache expiredCache = new JdbcSshFingerprintCache(ds, Duration.ZERO);
        expiredCache.store("github", "alice", Set.of("SHA256:abc"));

        assertTrue(expiredCache.lookup("github", "alice").isEmpty());
    }

    @Test
    void store_overwritesExistingEntry() {
        cache.store("github", "alice", Set.of("SHA256:old"));
        cache.store("github", "alice", Set.of("SHA256:new1", "SHA256:new2"));

        Set<String> result = cache.lookup("github", "alice");
        assertEquals(Set.of("SHA256:new1", "SHA256:new2"), result);
        assertFalse(result.contains("SHA256:old"));
    }

    @Test
    void evict_removesEntry() {
        cache.store("github", "alice", Set.of("SHA256:abc"));
        cache.evict("github", "alice");

        assertTrue(cache.lookup("github", "alice").isEmpty());
    }

    @Test
    void evict_nonExistentEntry_doesNotThrow() {
        assertDoesNotThrow(() -> cache.evict("github", "nobody"));
    }

    @Test
    void lookup_returnsSortedSet() {
        cache.store("github", "alice", Set.of("SHA256:zzz", "SHA256:aaa", "SHA256:mmm"));

        Set<String> result = cache.lookup("github", "alice");
        // TreeSet — iteration order is alphabetical
        assertEquals("SHA256:aaa", result.iterator().next());
    }
}
