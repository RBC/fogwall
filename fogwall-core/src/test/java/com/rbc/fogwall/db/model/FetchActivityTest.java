package com.rbc.fogwall.db.model;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.git.ProxyMode;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FetchActivityTest {

    private static FetchActivity.Key key(String owner, String repo, String ruleId) {
        return new FetchActivity.Key(
                Instant.parse("2026-09-30T14:00:00Z"),
                "github",
                owner,
                repo,
                FetchActivity.Transport.HTTP,
                ProxyMode.SERVER,
                FetchActivity.Result.ALLOWED,
                null,
                ruleId);
    }

    /** Every fogwall instance must land the same dimensions on the same row. */
    @Test
    void id_isStable_andDiffersWithAnyDimension() {
        assertEquals(
                key("acme", "widgets", "r").id(), key("acme", "widgets", "r").id());
        assertNotEquals(
                key("acme", "widgets", "r").id(), key("acme", "gadgets", "r").id());
        assertEquals(64, key("acme", "widgets", "r").id().length());
    }

    /** Neither a null nor a separator inside a value can make two different keys collide. */
    @Test
    void id_tellsNullFromEmptyAndDoesNotShiftAcrossFields() {
        assertNotEquals(
                key(null, "widgets", null).id(), key("", "widgets", null).id());
        assertNotEquals(key("a", "bc", null).id(), key("ab", "c", null).id());
    }

    @Test
    void overflow_dropsRepositoryAndRule_keepsTheOutcome() {
        FetchActivity.Key overflow = key("acme", "widgets", "r").overflow();

        assertNull(overflow.owner());
        assertNull(overflow.repoName());
        assertNull(overflow.ruleId());
        assertEquals(FetchActivity.Result.ALLOWED, overflow.result());
        assertEquals("github", overflow.provider());
    }
}
