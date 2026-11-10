package com.rbc.fogwall.db.model;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.Builder;

/**
 * A window of activity to aggregate. The window is always bounded, so an aggregate never scans a store's whole history.
 * Filters are exact matches; null means "don't filter on this".
 *
 * @param from inclusive start of the window
 * @param to exclusive end of the window
 * @param groupBy the dimensions to group by; empty totals the whole window
 * @param periodStarts the start of each period, ascending, the first equal to {@code from}; each period runs to the
 *     next start, the last to {@code to}. Required with {@link ActivityDimension#PERIOD}, ignored otherwise.
 */
@Builder
public record ActivityQuery(
        Instant from,
        Instant to,
        String provider,
        String owner,
        String repo,
        String user,
        Set<ActivityDimension> groupBy,
        List<Instant> periodStarts) {

    /** Bounds the size of the period expression a store builds. */
    public static final int MAX_PERIODS = 400;

    public ActivityQuery {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new IllegalArgumentException("An activity window needs a start before its end");
        }
        groupBy = groupBy == null ? Set.of() : Set.copyOf(groupBy);
        periodStarts = periodStarts == null ? List.of() : List.copyOf(periodStarts);
        if (groupBy.contains(ActivityDimension.PERIOD)) {
            checkPeriods(from, to, periodStarts);
        }
    }

    private static void checkPeriods(Instant from, Instant to, List<Instant> starts) {
        if (starts.isEmpty() || starts.size() > MAX_PERIODS) {
            throw new IllegalArgumentException("Grouping by period needs between 1 and " + MAX_PERIODS + " periods");
        }
        if (!starts.getFirst().equals(from)) {
            throw new IllegalArgumentException("The first period must start at the window's start");
        }
        for (int i = 1; i < starts.size(); i++) {
            if (!starts.get(i - 1).isBefore(starts.get(i))) {
                throw new IllegalArgumentException("Period starts must be strictly ascending");
            }
        }
        if (!starts.getLast().isBefore(to)) {
            throw new IllegalArgumentException("Every period must start before the window's end");
        }
    }

    public boolean groups(ActivityDimension dimension) {
        return groupBy.contains(dimension);
    }

    /** Whether the provider is part of the key: grouping by repository groups by provider too. */
    public boolean groupsProvider() {
        return groups(ActivityDimension.PROVIDER) || groups(ActivityDimension.REPO);
    }

    /** The period boundaries after the first, which a store compares a timestamp against in order. */
    public List<Instant> periodBoundaries() {
        return periodStarts.isEmpty() ? List.of() : periodStarts.subList(1, periodStarts.size());
    }
}
