package com.rbc.fogwall.dashboard.activity;

import com.rbc.fogwall.db.model.ActivityQuery;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The window an activity report covers and the periods it is split into, in UTC. The granularity follows the window's
 * length so a chart stays readable: days up to {@value #MAX_DAILY_DAYS} days, weeks (from Monday) up to
 * {@value #MAX_WEEKLY_DAYS} days, months beyond. The start is moved back to the start of its first period, so every
 * period is whole.
 */
public record ActivityWindow(Instant from, Instant to, Granularity granularity, List<Instant> periodStarts) {

    public enum Granularity {
        DAY,
        WEEK,
        MONTH
    }

    static final Duration DEFAULT_SPAN = Duration.ofDays(90);
    static final long MAX_DAILY_DAYS = 45;
    static final long MAX_WEEKLY_DAYS = 366;
    /** Ten years of months, well inside {@link ActivityQuery#MAX_PERIODS}. */
    static final long MAX_SPAN_DAYS = 3660;

    /**
     * The window from {@code from} to {@code to}; either may be absent. An absent end is now, an absent start is
     * {@link #DEFAULT_SPAN} before the end.
     *
     * @throws IllegalArgumentException when the start is not before the end, or the span exceeds {@link #MAX_SPAN_DAYS}
     */
    public static ActivityWindow of(Optional<Instant> from, Optional<Instant> to, Instant now) {
        Instant end = to.orElse(now);
        Instant start = from.orElse(end.minus(DEFAULT_SPAN));
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("'from' must be before 'to'");
        }
        long days = Duration.between(start, end).toDays();
        if (days > MAX_SPAN_DAYS) {
            throw new IllegalArgumentException("An activity window can span at most " + MAX_SPAN_DAYS + " days");
        }
        Granularity granularity = days <= MAX_DAILY_DAYS
                ? Granularity.DAY
                : days <= MAX_WEEKLY_DAYS ? Granularity.WEEK : Granularity.MONTH;
        LocalDate first = periodStart(start.atZone(ZoneOffset.UTC).toLocalDate(), granularity);
        List<Instant> starts = new ArrayList<>();
        for (LocalDate d = first; d.atStartOfDay(ZoneOffset.UTC).toInstant().isBefore(end); d = next(d, granularity)) {
            starts.add(d.atStartOfDay(ZoneOffset.UTC).toInstant());
        }
        return new ActivityWindow(starts.getFirst(), end, granularity, List.copyOf(starts));
    }

    private static LocalDate periodStart(LocalDate day, Granularity granularity) {
        return switch (granularity) {
            case DAY -> day;
            case WEEK -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> day.withDayOfMonth(1);
        };
    }

    private static LocalDate next(LocalDate start, Granularity granularity) {
        return switch (granularity) {
            case DAY -> start.plus(1, ChronoUnit.DAYS);
            case WEEK -> start.plus(1, ChronoUnit.WEEKS);
            case MONTH -> start.plus(1, ChronoUnit.MONTHS);
        };
    }
}
