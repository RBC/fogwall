package com.rbc.fogwall.db.memory;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.model.FetchRecord;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryFetchStoreTest {

    InMemoryFetchStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryFetchStore();
    }

    private FetchRecord rec(String provider, String owner, String repo, FetchRecord.Result result, Instant ts) {
        return FetchRecord.builder()
                .provider(provider)
                .owner(owner)
                .repoName(repo)
                .result(result)
                .timestamp(ts)
                .build();
    }

    @Test
    void record_findRecent_roundTrip() {
        FetchRecord r = rec("github", "acme", "repo", FetchRecord.Result.ALLOWED, Instant.now());
        store.record(r);

        var recent = store.findRecent(10);
        assertEquals(1, recent.size());
        assertEquals(r.getId(), recent.get(0).getId());
    }

    @Test
    void findRecent_orderedNewestFirst() {
        Instant t0 = Instant.ofEpochSecond(1000);
        Instant t1 = Instant.ofEpochSecond(2000);
        Instant t2 = Instant.ofEpochSecond(3000);
        store.record(rec("github", "acme", "repo", FetchRecord.Result.ALLOWED, t1));
        store.record(rec("github", "acme", "repo", FetchRecord.Result.ALLOWED, t0));
        store.record(rec("github", "acme", "repo", FetchRecord.Result.ALLOWED, t2));

        var recent = store.findRecent(10);
        assertEquals(t2, recent.get(0).getTimestamp());
        assertEquals(t0, recent.get(2).getTimestamp());
    }

    @Test
    void findRecent_respectsLimit() {
        for (int i = 0; i < 5; i++) {
            store.record(rec("github", "acme", "repo-" + i, FetchRecord.Result.ALLOWED, Instant.now()));
        }
        assertEquals(3, store.findRecent(3).size());
    }

    @Test
    void findByRepo_filtersCorrectly() {
        store.record(rec("github", "acme", "repo", FetchRecord.Result.ALLOWED, Instant.now()));
        store.record(rec("github", "acme", "other", FetchRecord.Result.ALLOWED, Instant.now()));
        store.record(rec("gitlab", "acme", "repo", FetchRecord.Result.ALLOWED, Instant.now()));

        var results = store.findByRepo("github", "acme", "repo", 10);
        assertEquals(1, results.size());
        assertEquals("repo", results.get(0).getRepoName());
    }

    @Test
    void findByRepo_respectsLimit() {
        Instant base = Instant.ofEpochSecond(1000);
        for (int i = 0; i < 5; i++) {
            store.record(rec("github", "acme", "repo", FetchRecord.Result.ALLOWED, base.plusSeconds(i)));
        }
        assertEquals(2, store.findByRepo("github", "acme", "repo", 2).size());
    }

    @Test
    void summarizeByRepo_groupsAndCountsCorrectly() {
        store.record(rec("github", "acme", "repo", FetchRecord.Result.ALLOWED, Instant.now()));
        store.record(rec("github", "acme", "repo", FetchRecord.Result.BLOCKED, Instant.now()));
        store.record(rec("github", "acme", "repo", FetchRecord.Result.ALLOWED, Instant.now()));
        store.record(rec("github", "other", "repo", FetchRecord.Result.ALLOWED, Instant.now()));

        var summary = store.summarizeByRepo();
        assertEquals(2, summary.size());

        var acme = summary.stream()
                .filter(s -> "acme".equals(s.owner()))
                .findFirst()
                .orElseThrow();
        assertEquals(3, acme.total());
        assertEquals(1, acme.blocked());
    }

    @Test
    void summarizeByRepo_orderedByTotalDescending() {
        store.record(rec("github", "small", "repo", FetchRecord.Result.ALLOWED, Instant.now()));
        for (int i = 0; i < 3; i++) {
            store.record(rec("github", "big", "repo", FetchRecord.Result.ALLOWED, Instant.now()));
        }

        var summary = store.summarizeByRepo();
        assertEquals("big", summary.get(0).owner());
        assertEquals("small", summary.get(1).owner());
    }

    @Test
    void findRecent_empty_returnsEmptyList() {
        assertTrue(store.findRecent(10).isEmpty());
    }

    @Test
    void initialize_doesNotThrow() {
        assertDoesNotThrow(() -> store.initialize());
    }
}
