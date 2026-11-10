package com.rbc.fogwall.dashboard.activity;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.dashboard.activity.ActivityWindow.Granularity;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ActivityWindowTest {

    private static final Instant NOW = Instant.parse("2026-11-10T15:30:00Z");

    private static ActivityWindow window(String from, String to) {
        return ActivityWindow.of(
                Optional.ofNullable(from).map(Instant::parse),
                Optional.ofNullable(to).map(Instant::parse),
                NOW);
    }

    @Test
    void default_isNinetyDaysToNow_inWeeksFromAMonday() {
        ActivityWindow window = window(null, null);

        assertEquals(NOW, window.to());
        assertEquals(Granularity.WEEK, window.granularity());
        // 90 days before NOW is Thursday 2026-08-12; its week starts Monday 2026-08-10.
        assertEquals(Instant.parse("2026-08-10T00:00:00Z"), window.from());
        assertEquals(window.from(), window.periodStarts().getFirst());
        assertTrue(window.periodStarts().getLast().isBefore(NOW));
        assertEquals(14, window.periodStarts().size());
    }

    @Test
    void shortWindow_isInDays_startingAtMidnight() {
        ActivityWindow window = window("2026-11-01T12:00:00Z", "2026-11-04T06:00:00Z");

        assertEquals(Granularity.DAY, window.granularity());
        assertEquals(Instant.parse("2026-11-01T00:00:00Z"), window.from());
        assertEquals(4, window.periodStarts().size());
    }

    @Test
    void longWindow_isInMonths_startingOnTheFirst() {
        ActivityWindow window = window("2025-03-15T00:00:00Z", "2026-11-10T00:00:00Z");

        assertEquals(Granularity.MONTH, window.granularity());
        assertEquals(Instant.parse("2025-03-01T00:00:00Z"), window.from());
        assertEquals(
                Instant.parse("2026-11-01T00:00:00Z"), window.periodStarts().getLast());
    }

    @Test
    void reversedOrEmptyWindow_isRefused() {
        assertThrows(IllegalArgumentException.class, () -> window("2026-11-02T00:00:00Z", "2026-11-01T00:00:00Z"));
        assertThrows(IllegalArgumentException.class, () -> window("2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z"));
    }

    @Test
    void windowLongerThanTheCap_isRefused() {
        assertThrows(IllegalArgumentException.class, () -> window("2010-01-01T00:00:00Z", "2026-11-01T00:00:00Z"));
    }
}
