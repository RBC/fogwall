package com.rbc.fogwall.scmapi;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.testing.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class ForkLocationCacheTest {

    private static final OwnerRepo UPSTREAM = new OwnerRepo("upstream-owner", "widgets");

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-01T12:00:00Z"));

    private static ForkLocationCache.Key key(String headOwner) {
        return ForkLocationCache.Key.of("forgejo", UPSTREAM, headOwner);
    }

    @Test
    void keyIgnoresCase() {
        var cache = new ForkLocationCache(clock, 10);
        cache.put(ForkLocationCache.Key.of("Forgejo", new OwnerRepo("Upstream-Owner", "Widgets"), "Forker"), 7);

        assertEquals(OptionalLong.of(7), cache.get(key("forker")));
    }

    @Test
    void entryExpiresAfterTtl() {
        var cache = new ForkLocationCache(clock, 10);
        cache.put(key("forker"), 7);

        clock.advance(ForkLocationCache.TTL.minusSeconds(1));
        assertEquals(OptionalLong.of(7), cache.get(key("forker")));

        clock.advance(Duration.ofSeconds(1));
        assertTrue(cache.get(key("forker")).isEmpty());
        assertEquals(0, cache.size());
    }

    @Test
    void full_evictsOldestEntry() {
        var cache = new ForkLocationCache(clock, 2);
        cache.put(key("a"), 1);
        cache.put(key("b"), 2);
        cache.put(key("c"), 3);

        assertEquals(2, cache.size());
        assertTrue(cache.get(key("a")).isEmpty());
        assertEquals(OptionalLong.of(2), cache.get(key("b")));
        assertEquals(OptionalLong.of(3), cache.get(key("c")));
    }

    @Test
    void refreshedEntry_isNoLongerOldest() {
        var cache = new ForkLocationCache(clock, 2);
        cache.put(key("a"), 1);
        cache.put(key("b"), 2);
        cache.put(key("a"), 10);
        cache.put(key("c"), 3);

        assertEquals(OptionalLong.of(10), cache.get(key("a")));
        assertTrue(cache.get(key("b")).isEmpty());
    }

    @Test
    void insert_dropsExpiredEntries() {
        var cache = new ForkLocationCache(clock, 10);
        cache.put(key("a"), 1);
        cache.put(key("b"), 2);
        clock.advance(ForkLocationCache.TTL);
        cache.put(key("c"), 3);

        assertEquals(1, cache.size());
        assertEquals(OptionalLong.of(3), cache.get(key("c")));
    }

    @Test
    void invalidate_keepsAnEntryRefreshedToAnotherRepository() {
        var cache = new ForkLocationCache(clock, 10);
        cache.put(key("forker"), 8);

        cache.invalidate(key("forker"), 7);
        assertEquals(OptionalLong.of(8), cache.get(key("forker")));

        cache.invalidate(key("forker"), 8);
        assertTrue(cache.get(key("forker")).isEmpty());
    }
}
