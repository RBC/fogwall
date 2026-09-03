package com.rbc.fogwall.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class CacheConfigTest {

    // ---- resolveCloneDepth ----

    @Test
    void resolveCloneDepth_unset_usesModeDefault() {
        var cfg = new CacheConfig.TransportCacheConfig();
        assertEquals(100, cfg.resolveCloneDepth(100));
        assertEquals(0, cfg.resolveCloneDepth(0));
    }

    @Test
    void resolveCloneDepth_explicitZero_isDistinctFromUnset() {
        var cfg = new CacheConfig.TransportCacheConfig();
        cfg.setCloneDepth(0);
        // Explicit 0 (full) must win over a shallow mode default — 0 is a real choice, not "unset".
        assertEquals(0, cfg.resolveCloneDepth(100));
    }

    @Test
    void resolveCloneDepth_explicitValue_wins() {
        var cfg = new CacheConfig.TransportCacheConfig();
        cfg.setCloneDepth(25);
        assertEquals(25, cfg.resolveCloneDepth(100));
    }

    // ---- resolveShallowSince / parseDuration ----

    @Test
    void resolveShallowSince_blank_isNull() {
        var cfg = new CacheConfig.TransportCacheConfig();
        assertNull(cfg.resolveShallowSince());
    }

    @Test
    void parseDuration_days() {
        assertEquals(Duration.ofDays(90), CacheConfig.parseDuration("90d"));
    }

    @Test
    void parseDuration_hoursMinutesSeconds() {
        assertEquals(Duration.ofHours(12), CacheConfig.parseDuration("12h"));
        assertEquals(Duration.ofMinutes(30), CacheConfig.parseDuration("30m"));
        assertEquals(Duration.ofSeconds(45), CacheConfig.parseDuration("45s"));
    }

    @Test
    void parseDuration_caseInsensitiveAndWhitespace() {
        assertEquals(Duration.ofDays(7), CacheConfig.parseDuration(" 7D "));
    }

    @Test
    void parseDuration_iso8601() {
        assertEquals(Duration.ofHours(48), CacheConfig.parseDuration("PT48H"));
    }

    @Test
    void parseDuration_blankOrNull_isNull() {
        assertNull(CacheConfig.parseDuration(""));
        assertNull(CacheConfig.parseDuration("   "));
        assertNull(CacheConfig.parseDuration(null));
    }

    @Test
    void parseDuration_invalid_throws() {
        assertThrows(IllegalArgumentException.class, () -> CacheConfig.parseDuration("ninety-days"));
        assertThrows(IllegalArgumentException.class, () -> CacheConfig.parseDuration("90x"));
    }

    // ---- defaults ----

    @Test
    void defaults_areUnsetForBothModes() {
        var cfg = new CacheConfig();
        assertNull(cfg.getProxy().getCloneDepth(), "proxy clone-depth unset by default; mode default applied later");
        assertNull(cfg.getServer().getCloneDepth());
        assertNull(cfg.getProxy().resolveShallowSince());
        assertNull(cfg.getServer().resolveShallowSince());
    }
}
