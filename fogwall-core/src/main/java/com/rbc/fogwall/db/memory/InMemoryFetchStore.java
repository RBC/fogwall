package com.rbc.fogwall.db.memory;

import com.rbc.fogwall.db.FetchStore;
import com.rbc.fogwall.db.model.FetchRecord;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** In-memory {@link FetchStore}. Data is lost on restart. */
public class InMemoryFetchStore implements FetchStore {

    private final Map<String, FetchRecord> store = new ConcurrentHashMap<>();

    @Override
    public void initialize() {}

    @Override
    public void record(FetchRecord fetchRecord) {
        store.put(fetchRecord.getId(), fetchRecord);
    }

    @Override
    public List<FetchRecord> findRecent(int limit) {
        return store.values().stream()
                .sorted(Comparator.comparing(FetchRecord::getTimestamp).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<FetchRecord> findByRepo(String provider, String owner, String repoName, int limit) {
        return store.values().stream()
                .filter(r -> provider.equals(r.getProvider())
                        && owner.equals(r.getOwner())
                        && repoName.equals(r.getRepoName()))
                .sorted(Comparator.comparing(FetchRecord::getTimestamp).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<RepoFetchSummary> summarizeByRepo() {
        return store.values().stream()
                .collect(Collectors.groupingBy(r -> r.getProvider() + "|" + r.getOwner() + "|" + r.getRepoName()))
                .entrySet()
                .stream()
                .map(e -> {
                    List<FetchRecord> records = e.getValue();
                    FetchRecord sample = records.get(0);
                    long blocked = records.stream()
                            .filter(r -> r.getResult() == FetchRecord.Result.BLOCKED)
                            .count();
                    return new RepoFetchSummary(
                            sample.getProvider(), sample.getOwner(), sample.getRepoName(), records.size(), blocked);
                })
                .sorted(Comparator.comparingLong(RepoFetchSummary::total).reversed())
                .toList();
    }
}
