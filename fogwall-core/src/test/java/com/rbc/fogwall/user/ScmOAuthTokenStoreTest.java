package com.rbc.fogwall.user;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.jdbc.DataSourceFactory;
import com.rbc.fogwall.db.jdbc.JdbcPushStore;
import com.rbc.fogwall.service.JdbcScmTokenCache;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link ScmOAuthTokenStore} backed by an H2 in-memory database (#40).
 *
 * <p>Each test gets its own isolated H2 database, schema initialized via {@link JdbcPushStore#initialize()} (same
 * schema/migrations that include {@code user_scm_tokens}). {@code user_scm_tokens.username} has a foreign key onto
 * {@code proxy_users}, so tests create the referenced user via {@link JdbcUserStore} first.
 */
class ScmOAuthTokenStoreTest {

    ScmOAuthTokenStore store;

    @BeforeEach
    void setUp() {
        DataSource ds = DataSourceFactory.h2InMemory("scm-oauth-token-test-" + UUID.randomUUID());
        new JdbcPushStore(ds).initialize();
        var userStore = new JdbcUserStore(ds, new JdbcScmTokenCache(ds, Duration.ofDays(1)));
        userStore.upsertAll(List.of(UserEntry.builder()
                .username("alice")
                .passwordHash("{noop}pw")
                .emails(List.of())
                .scmIdentities(List.of())
                .build()));
        store = new ScmOAuthTokenStore(ds);
    }

    @Test
    void save_thenFindAccessToken_roundTrips() {
        byte[] accessToken = "encrypted-access-token".getBytes(StandardCharsets.UTF_8);
        byte[] refreshToken = "encrypted-refresh-token".getBytes(StandardCharsets.UTF_8);
        Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);

        store.save("alice", "github", accessToken, refreshToken, "read_user", expiresAt);

        Optional<byte[]> found = store.findAccessToken("alice", "github");
        assertTrue(found.isPresent());
        assertArrayEquals(accessToken, found.get());
    }

    @Test
    void save_nullRefreshTokenAndExpiry_stillPersistsAccessToken() {
        byte[] accessToken = "token".getBytes(StandardCharsets.UTF_8);

        store.save("alice", "github", accessToken, null, null, null);

        assertArrayEquals(accessToken, store.findAccessToken("alice", "github").orElseThrow());
    }

    @Test
    void save_secondCallForSamePair_replacesToken() {
        store.save("alice", "github", "old-token".getBytes(StandardCharsets.UTF_8), null, null, null);
        store.save("alice", "github", "new-token".getBytes(StandardCharsets.UTF_8), null, null, null);

        assertArrayEquals(
                "new-token".getBytes(StandardCharsets.UTF_8),
                store.findAccessToken("alice", "github").orElseThrow());
    }

    @Test
    void save_differentProvidersForSameUser_bothPersist() {
        store.save("alice", "github", "gh-token".getBytes(StandardCharsets.UTF_8), null, null, null);
        store.save("alice", "gitlab", "gl-token".getBytes(StandardCharsets.UTF_8), null, null, null);

        assertArrayEquals(
                "gh-token".getBytes(StandardCharsets.UTF_8),
                store.findAccessToken("alice", "github").orElseThrow());
        assertArrayEquals(
                "gl-token".getBytes(StandardCharsets.UTF_8),
                store.findAccessToken("alice", "gitlab").orElseThrow());
    }

    @Test
    void findAccessToken_noStoredToken_returnsEmpty() {
        assertTrue(store.findAccessToken("alice", "github").isEmpty());
    }

    @Test
    void remove_deletesStoredToken() {
        store.save("alice", "github", "token".getBytes(StandardCharsets.UTF_8), null, null, null);

        store.remove("alice", "github");

        assertTrue(store.findAccessToken("alice", "github").isEmpty());
    }

    @Test
    void remove_noStoredToken_isNoop() {
        assertDoesNotThrow(() -> store.remove("alice", "github"));
    }
}
