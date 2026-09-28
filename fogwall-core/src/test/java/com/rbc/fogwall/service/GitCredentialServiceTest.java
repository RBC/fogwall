package com.rbc.fogwall.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.db.jdbc.DataSourceFactory;
import com.rbc.fogwall.db.jdbc.JdbcPushStore;
import com.rbc.fogwall.service.GitCredentialService.Issued;
import com.rbc.fogwall.user.GitCredential;
import com.rbc.fogwall.user.GitCredentialNameConflictException;
import com.rbc.fogwall.user.GitCredentialStore;
import com.rbc.fogwall.user.JdbcGitCredentialStore;
import com.rbc.fogwall.user.JdbcUserStore;
import com.rbc.fogwall.user.UserEntry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link GitCredentialService} over a real {@link JdbcGitCredentialStore} on H2, with a clock it controls.
 */
class GitCredentialServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");

    /** A clock the test moves by hand. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    GitCredentialStore store;
    MutableClock clock;
    GitCredentialService service;

    @BeforeEach
    void setUp() {
        DataSource ds = DataSourceFactory.h2InMemory("git-credential-service-test-" + UUID.randomUUID());
        new JdbcPushStore(ds).initialize();
        var userStore = new JdbcUserStore(ds, new JdbcScmTokenCache(ds, Duration.ofDays(1)));
        userStore.upsertAll(List.of(user("alice"), user("bob")));
        store = new JdbcGitCredentialStore(ds);
        clock = new MutableClock(NOW);
        service = new GitCredentialService(store, Optional.empty(), clock);
    }

    private static UserEntry user(String username) {
        return UserEntry.builder()
                .username(username)
                .passwordHash("{noop}pw")
                .emails(List.of())
                .scmIdentities(List.of())
                .build();
    }

    // ---- issue and authenticate ----

    @Test
    void issue_producesAPrefixedValueThatAuthenticates() {
        Issued issued = service.issue("alice", "laptop");

        String value = issued.value();
        assertTrue(value.startsWith(GitCredentialService.PREFIX));
        String rest = value.substring(GitCredentialService.PREFIX.length());
        assertEquals('_', rest.charAt(GitCredentialService.ID_LENGTH));
        assertEquals(issued.credential().id(), rest.substring(0, GitCredentialService.ID_LENGTH));
        assertFalse(rest.substring(GitCredentialService.ID_LENGTH + 1).isEmpty());
        assertTrue(GitCredentialService.isFogwallCredential(value));

        GitCredential authenticated = service.authenticate(value).orElseThrow();
        assertEquals(issued.credential().id(), authenticated.id());
        assertEquals("alice", authenticated.username());
        assertEquals("laptop", authenticated.name());
    }

    @Test
    void issue_storesOnlyAHashOfTheSecret() {
        Issued issued = service.issue("alice", "laptop");
        // The secret follows the fixed-length id; it is base64url, so it may itself contain '_'.
        String secret =
                issued.value().substring(GitCredentialService.PREFIX.length() + GitCredentialService.ID_LENGTH + 1);

        GitCredential stored = store.findById(issued.credential().id()).orElseThrow();

        assertNotEquals(secret, stored.secretHash());
        assertFalse(stored.secretHash().contains(secret));
        assertEquals(GitCredentialService.hash(secret), stored.secretHash());
        assertTrue(stored.secretHash().matches("\\{sha256}[0-9a-f]{64}"), stored.secretHash());
    }

    @Test
    void issue_withoutALimit_hasNoExpiry() {
        Issued issued = service.issue("alice", "laptop");

        assertEquals(NOW, issued.credential().createdAt());
        assertNull(issued.credential().expiresAt());
        assertNull(store.findById(issued.credential().id()).orElseThrow().expiresAt());
    }

    @Test
    void issue_withALimit_expiresThatFarFromIssue() {
        var limited = new GitCredentialService(store, Optional.of(Duration.ofDays(30)), clock);

        Issued issued = limited.issue("alice", "laptop");

        assertEquals(NOW.plus(Duration.ofDays(30)), issued.credential().expiresAt());
    }

    @Test
    void authenticate_wrongSecret_isEmpty() {
        Issued issued = service.issue("alice", "laptop");

        String wrong = GitCredentialService.PREFIX + issued.credential().id() + "_not-the-secret";

        assertTrue(service.authenticate(wrong).isEmpty());
    }

    @Test
    void authenticate_unknownId_isEmpty() {
        service.issue("alice", "laptop");

        assertTrue(service.authenticate(GitCredentialService.PREFIX + "abcdefghijklmnop_secret")
                .isEmpty());
    }

    @Test
    void authenticate_malformedValues_areEmptyWithoutALookup() {
        GitCredentialStore spyStore = mock(GitCredentialStore.class);
        var withSpy = new GitCredentialService(spyStore, Optional.empty(), clock);

        assertTrue(withSpy.authenticate(null).isEmpty());
        assertTrue(withSpy.authenticate("ghp_abcdefghijklmnop_secret").isEmpty(), "no prefix");
        assertTrue(withSpy.authenticate("fgw_short_secret").isEmpty(), "short id");
        assertTrue(withSpy.authenticate("fgw_abcdefghijklmnop").isEmpty(), "no separator");
        assertTrue(withSpy.authenticate("fgw_abcdefghijklmnop_").isEmpty(), "empty secret");
        assertTrue(withSpy.authenticate("fgw_abcdefghijklmnopXsecret").isEmpty(), "wrong separator");
        assertTrue(withSpy.authenticate("fgw_ABCDEFGHIJKLMNOP_secret").isEmpty(), "id outside the alphabet");
        assertTrue(withSpy.authenticate("fgw_abcdefghijklmn01_secret").isEmpty(), "id outside the alphabet");

        verifyNoInteractions(spyStore);
    }

    @Test
    void isFogwallCredential_onlyRecognisesThePrefix() {
        assertTrue(GitCredentialService.isFogwallCredential("fgw_anything"));
        assertFalse(GitCredentialService.isFogwallCredential("ghp_anything"));
        assertFalse(GitCredentialService.isFogwallCredential(null));
    }

    // ---- expiry ----

    @Test
    void authenticate_pastTheStoredExpiry_isEmpty() {
        var limited = new GitCredentialService(store, Optional.of(Duration.ofHours(1)), clock);
        Issued issued = limited.issue("alice", "laptop");

        clock.advance(Duration.ofMinutes(59));
        assertTrue(limited.authenticate(issued.value()).isPresent());

        clock.advance(Duration.ofMinutes(1));
        assertTrue(limited.authenticate(issued.value()).isEmpty());
    }

    @Test
    void authenticate_pastAMaxLifetimeConfiguredAfterIssue_isEmpty() {
        // Issued with no limit, so no expiry is stored; a limit configured later still retires it.
        Issued issued = service.issue("alice", "laptop");
        assertNull(issued.credential().expiresAt());

        var limited = new GitCredentialService(store, Optional.of(Duration.ofDays(7)), clock);
        clock.advance(Duration.ofDays(6));
        assertTrue(limited.authenticate(issued.value()).isPresent());

        clock.advance(Duration.ofDays(1));
        assertTrue(limited.authenticate(issued.value()).isEmpty());
        assertTrue(service.authenticate(issued.value()).isPresent(), "the unlimited service still accepts it");
    }

    // ---- effective expiry ----

    @Test
    void effectiveExpiry_withoutALimit_isTheStoredExpiry() {
        Issued issued = service.issue("alice", "laptop");

        assertNull(service.effectiveExpiry(issued.credential()));
        assertFalse(service.isExpired(issued.credential()));
    }

    @Test
    void effectiveExpiry_ofACredentialIssuedBeforeALimit_isIssuePlusTheLimit() {
        Issued issued = service.issue("alice", "laptop");
        var limited = new GitCredentialService(store, Optional.of(Duration.ofDays(7)), clock);

        assertEquals(NOW.plus(Duration.ofDays(7)), limited.effectiveExpiry(issued.credential()));
        clock.advance(Duration.ofDays(7));
        assertTrue(limited.isExpired(issued.credential()));
    }

    @Test
    void effectiveExpiry_afterTheLimitIsLowered_isTheEarlierOfTheTwo() {
        var generous = new GitCredentialService(store, Optional.of(Duration.ofDays(90)), clock);
        Issued issued = generous.issue("alice", "laptop");
        var strict = new GitCredentialService(store, Optional.of(Duration.ofDays(30)), clock);

        assertEquals(NOW.plus(Duration.ofDays(30)), strict.effectiveExpiry(issued.credential()));
        assertEquals(NOW.plus(Duration.ofDays(90)), generous.effectiveExpiry(issued.credential()));
    }

    // ---- rotate ----

    @Test
    void rotate_replacesTheSecretKeepingIdAndName() {
        Issued issued = service.issue("alice", "laptop");
        service.authenticate(issued.value());
        assertNotNull(store.findById(issued.credential().id()).orElseThrow().lastUsedAt());
        clock.advance(Duration.ofHours(1));

        Issued rotated = service.rotate("alice", issued.credential().id()).orElseThrow();

        assertEquals(issued.credential().id(), rotated.credential().id());
        assertEquals("laptop", rotated.credential().name());
        assertNotEquals(issued.value(), rotated.value());
        assertTrue(service.authenticate(issued.value()).isEmpty(), "old value stops working");
        assertTrue(service.authenticate(rotated.value()).isPresent());

        GitCredential stored = store.findById(issued.credential().id()).orElseThrow();
        assertEquals(NOW.plus(Duration.ofHours(1)), stored.createdAt());
        assertNull(rotated.credential().lastUsedAt());
    }

    @Test
    void rotate_clearsLastUsedAt() {
        Issued issued = service.issue("alice", "laptop");
        service.authenticate(issued.value());

        service.rotate("alice", issued.credential().id());

        assertNull(store.findById(issued.credential().id()).orElseThrow().lastUsedAt());
    }

    @Test
    void rotate_anotherUsersCredential_isRefused() {
        Issued issued = service.issue("alice", "laptop");

        assertTrue(service.rotate("bob", issued.credential().id()).isEmpty());

        assertTrue(service.authenticate(issued.value()).isPresent());
        assertEquals(
                issued.credential().secretHash(),
                store.findById(issued.credential().id()).orElseThrow().secretHash());
    }

    @Test
    void rotate_unknownId_isEmpty() {
        assertTrue(service.rotate("alice", "abcdefghijklmnop").isEmpty());
    }

    // ---- revoke ----

    @Test
    void revoke_removesTheCredential() {
        Issued issued = service.issue("alice", "laptop");

        assertTrue(service.revoke("alice", issued.credential().id()));

        assertTrue(service.authenticate(issued.value()).isEmpty());
        assertTrue(store.findById(issued.credential().id()).isEmpty());
    }

    @Test
    void revoke_anotherUsersCredential_isRefused() {
        Issued issued = service.issue("alice", "laptop");

        assertFalse(service.revoke("bob", issued.credential().id()));

        assertTrue(service.authenticate(issued.value()).isPresent());
    }

    @Test
    void revoke_unknownId_isFalse() {
        assertFalse(service.revoke("alice", "abcdefghijklmnop"));
    }

    // ---- list ----

    @Test
    void list_isOrderedByName_andScopedToTheUser() {
        service.issue("alice", "workstation");
        service.issue("alice", "ci");
        service.issue("alice", "laptop");
        service.issue("bob", "laptop");

        List<GitCredential> listed = service.list("alice");

        assertEquals(
                List.of("ci", "laptop", "workstation"),
                listed.stream().map(GitCredential::name).toList());
        assertTrue(listed.stream().allMatch(c -> c.username().equals("alice")));
        assertEquals(List.of(), service.list("nobody"));
    }

    // ---- names ----

    @Test
    void issue_rejectsBlankTooLongAndControlCharacterNames() {
        assertThrows(IllegalArgumentException.class, () -> service.issue("alice", null));
        assertThrows(IllegalArgumentException.class, () -> service.issue("alice", "   "));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.issue("alice", "x".repeat(GitCredentialService.MAX_NAME_LENGTH + 1)));
        assertThrows(IllegalArgumentException.class, () -> service.issue("alice", "lap\ntop"));
        assertThrows(IllegalArgumentException.class, () -> service.issue("alice", "lap\u0007top"));
        assertEquals(List.of(), service.list("alice"));
    }

    @Test
    void issue_trimsTheName_andAcceptsTheMaximumLength() {
        assertEquals("laptop", service.issue("alice", "  laptop  ").credential().name());
        String longest = "x".repeat(GitCredentialService.MAX_NAME_LENGTH);
        assertEquals(longest, service.issue("alice", longest).credential().name());
    }

    @Test
    void issue_duplicateNameForTheSameUser_conflicts() {
        service.issue("alice", "laptop");

        assertThrows(GitCredentialNameConflictException.class, () -> service.issue("alice", "laptop"));
        assertEquals(1, service.list("alice").size());
    }

    @Test
    void issue_sameNameForDifferentUsers_isAllowed() {
        service.issue("alice", "laptop");

        assertDoesNotThrow(() -> service.issue("bob", "laptop"));
        assertEquals(1, service.list("bob").size());
    }

    // ---- use recording ----

    @Test
    void authenticate_recordsFirstUse_thenOnlyAfterTheInterval() {
        Issued issued = service.issue("alice", "laptop");
        String id = issued.credential().id();
        assertNull(store.findById(id).orElseThrow().lastUsedAt());

        service.authenticate(issued.value());
        assertEquals(NOW, store.findById(id).orElseThrow().lastUsedAt());

        clock.advance(GitCredentialService.USE_RECORD_INTERVAL.minusSeconds(1));
        service.authenticate(issued.value());
        assertEquals(NOW, store.findById(id).orElseThrow().lastUsedAt(), "not rewritten within the interval");

        clock.advance(Duration.ofSeconds(2));
        service.authenticate(issued.value());
        assertEquals(clock.instant(), store.findById(id).orElseThrow().lastUsedAt());
    }

    @Test
    void authenticate_failedAttempt_recordsNoUse() {
        Issued issued = service.issue("alice", "laptop");

        service.authenticate(GitCredentialService.PREFIX + issued.credential().id() + "_wrong");

        assertNull(store.findById(issued.credential().id()).orElseThrow().lastUsedAt());
    }
}
