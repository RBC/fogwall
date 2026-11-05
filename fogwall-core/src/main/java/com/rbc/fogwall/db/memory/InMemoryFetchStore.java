package com.rbc.fogwall.db.memory;

import com.rbc.fogwall.db.FetchStore;
import com.rbc.fogwall.db.model.FetchActivity;
import com.rbc.fogwall.db.model.FetchActivityQuery;
import com.rbc.fogwall.db.model.RecordCursor;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** In-memory {@link FetchStore}. Data is lost on restart. */
public class InMemoryFetchStore implements FetchStore {

    private final Map<String, FetchActivity> store = new ConcurrentHashMap<>();

    @Override
    public void initialize() {}

    @Override
    public void add(Collection<FetchActivity> increments) {
        for (FetchActivity row : increments) {
            store.merge(
                    row.getId(),
                    row,
                    (stored, added) -> stored.toBuilder()
                            .fetchCount(stored.getFetchCount() + added.getFetchCount())
                            .lastSeen(
                                    stored.getLastSeen().isAfter(added.getLastSeen())
                                            ? stored.getLastSeen()
                                            : added.getLastSeen())
                            .build());
        }
    }

    @Override
    public List<FetchActivity> find(FetchActivityQuery query) {
        Comparator<RecordCursor> order = query.isNewestFirst() ? RecordCursor.ORDER.reversed() : RecordCursor.ORDER;
        return store.values().stream()
                .filter(matches(query))
                .filter(r -> query.getAfter() == null || order.compare(cursor(r), query.getAfter()) > 0)
                .sorted(Comparator.comparing(InMemoryFetchStore::cursor, order))
                .skip(query.getOffset())
                .limit(query.getLimit())
                .toList();
    }

    private static RecordCursor cursor(FetchActivity r) {
        return new RecordCursor(r.getBucketStart(), r.getId());
    }

    private static Predicate<FetchActivity> matches(FetchActivityQuery q) {
        return r -> (q.getResult() == null || q.getResult() == r.getResult())
                && (q.getFrom() == null || !r.getBucketStart().isBefore(q.getFrom()))
                && (q.getTo() == null || r.getBucketStart().isBefore(q.getTo()))
                && (q.getTransport() == null || q.getTransport() == r.getTransport())
                && (q.getProvider() == null || q.getProvider().equals(r.getProvider()))
                && (q.getOwner() == null || q.getOwner().equals(r.getOwner()))
                && (q.getRepoName() == null || q.getRepoName().equals(r.getRepoName()))
                && (q.getSearch() == null
                        || q.getSearch().isBlank()
                        || contains(r.getOwner(), q.getSearch())
                        || contains(r.getRepoName(), q.getSearch()));
    }

    private static boolean contains(String value, String search) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT));
    }

    @Override
    public List<RepoFetchSummary> summarizeByRepo() {
        return store.values().stream()
                .filter(r -> r.getOwner() != null && r.getRepoName() != null)
                .collect(Collectors.groupingBy(r -> List.of(r.getProvider(), r.getOwner(), r.getRepoName())))
                .values()
                .stream()
                .map(rows -> {
                    FetchActivity sample = rows.getFirst();
                    long total = rows.stream()
                            .mapToLong(FetchActivity::getFetchCount)
                            .sum();
                    long blocked = rows.stream()
                            .filter(r -> r.getResult() == FetchActivity.Result.BLOCKED)
                            .mapToLong(FetchActivity::getFetchCount)
                            .sum();
                    return new RepoFetchSummary(
                            sample.getProvider(), sample.getOwner(), sample.getRepoName(), total, blocked);
                })
                .sorted(Comparator.comparingLong(RepoFetchSummary::total).reversed())
                .toList();
    }

    @Override
    public void pruneBefore(Instant cutoff) {
        store.values().removeIf(r -> r.getBucketStart().isBefore(cutoff));
    }
}
