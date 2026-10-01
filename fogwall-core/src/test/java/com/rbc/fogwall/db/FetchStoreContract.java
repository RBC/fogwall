package com.rbc.fogwall.db;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.FetchStore.RepoFetchSummary;
import com.rbc.fogwall.db.model.FetchActivity;
import com.rbc.fogwall.db.model.FetchActivityQuery;
import com.rbc.fogwall.db.model.FetchRefusal;
import com.rbc.fogwall.git.ProxyMode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What every {@link FetchStore} must do, run against each implementation by a subclass. */
public abstract class FetchStoreContract {

    private static final Instant HOUR = Instant.parse("2026-09-30T14:00:00Z");

    protected abstract FetchStore store();

    private static FetchActivity row(
            Instant hour, String owner, String repo, FetchActivity.Result result, long count, Instant lastSeen) {
        FetchRefusal refusal = result == FetchActivity.Result.BLOCKED ? FetchRefusal.NOT_IN_ALLOW_LIST : null;
        return new FetchActivity.Key(
                        hour,
                        "github",
                        owner,
                        repo,
                        FetchActivity.Transport.HTTP,
                        ProxyMode.TRANSPARENT,
                        result,
                        refusal,
                        null)
                .toActivity(count, lastSeen);
    }

    private static FetchActivity allowed(String owner, String repo, long count) {
        return row(HOUR, owner, repo, FetchActivity.Result.ALLOWED, count, HOUR.plusSeconds(60));
    }

    private List<FetchActivity> find(FetchActivityQuery.FetchActivityQueryBuilder query) {
        return store().find(query.build());
    }

    @Test
    void add_roundTripsEveryField() {
        FetchActivity written = new FetchActivity.Key(
                        HOUR,
                        "github",
                        "acme",
                        "widgets",
                        FetchActivity.Transport.SSH,
                        ProxyMode.SERVER,
                        FetchActivity.Result.BLOCKED,
                        FetchRefusal.DENY_RULE,
                        "rule-1")
                .toActivity(3, HOUR.plusSeconds(90));

        store().add(List.of(written));

        assertEquals(List.of(written), find(FetchActivityQuery.builder()));
    }

    @Test
    void add_toAnExistingRow_sumsTheCountAndKeepsTheLaterLastSeen() {
        store().add(List.of(row(HOUR, "acme", "widgets", FetchActivity.Result.ALLOWED, 2, HOUR.plusSeconds(600))));
        store().add(List.of(row(HOUR, "acme", "widgets", FetchActivity.Result.ALLOWED, 5, HOUR.plusSeconds(60))));

        List<FetchActivity> rows = find(FetchActivityQuery.builder());
        assertEquals(1, rows.size());
        assertEquals(7, rows.getFirst().getFetchCount());
        assertEquals(HOUR.plusSeconds(600), rows.getFirst().getLastSeen());
    }

    @Test
    void add_aDifferentHourOrOutcome_isItsOwnRow() {
        store().add(List.of(
                row(HOUR, "acme", "widgets", FetchActivity.Result.ALLOWED, 1, HOUR),
                row(HOUR.plusSeconds(3600), "acme", "widgets", FetchActivity.Result.ALLOWED, 1, HOUR.plusSeconds(3600)),
                row(HOUR, "acme", "widgets", FetchActivity.Result.BLOCKED, 1, HOUR)));

        assertEquals(3, find(FetchActivityQuery.builder()).size());
    }

    @Test
    void add_theOverflowRow_storesNoRepository() {
        FetchActivity overflow = new FetchActivity.Key(
                        HOUR,
                        "github",
                        "acme",
                        "widgets",
                        FetchActivity.Transport.HTTP,
                        ProxyMode.TRANSPARENT,
                        FetchActivity.Result.BLOCKED,
                        FetchRefusal.NOT_IN_ALLOW_LIST,
                        null)
                .overflow()
                .toActivity(9, HOUR);

        store().add(List.of(overflow));

        FetchActivity read = find(FetchActivityQuery.builder()).getFirst();
        assertNull(read.getOwner());
        assertNull(read.getRepoName());
        assertEquals(9, read.getFetchCount());
    }

    @Test
    void find_filtersOnEachField() {
        store().add(List.of(
                allowed("acme", "widgets", 1),
                row(HOUR, "acme", "gadgets", FetchActivity.Result.BLOCKED, 1, HOUR),
                new FetchActivity.Key(
                                HOUR,
                                "gitlab",
                                "other",
                                "widgets",
                                FetchActivity.Transport.SSH,
                                ProxyMode.SERVER,
                                FetchActivity.Result.ALLOWED,
                                null,
                                null)
                        .toActivity(1, HOUR)));

        assertEquals(
                1,
                find(FetchActivityQuery.builder().result(FetchActivity.Result.BLOCKED))
                        .size());
        assertEquals(
                1,
                find(FetchActivityQuery.builder().transport(FetchActivity.Transport.SSH))
                        .size());
        assertEquals(1, find(FetchActivityQuery.builder().provider("gitlab")).size());
        assertEquals(2, find(FetchActivityQuery.builder().owner("acme")).size());
        assertEquals(2, find(FetchActivityQuery.builder().repoName("widgets")).size());
        assertEquals(
                1,
                find(FetchActivityQuery.builder()
                                .provider("github")
                                .owner("acme")
                                .repoName("widgets"))
                        .size());
    }

    @Test
    void find_searchMatchesOwnerOrRepoName_ignoringCase() {
        store().add(List.of(allowed("Acme", "widgets", 1), allowed("other", "ACME-tools", 1), allowed("x", "y", 1)));

        assertEquals(2, find(FetchActivityQuery.builder().search("acme")).size());
    }

    @Test
    void find_ordersByHourAndPages() {
        for (int i = 0; i < 5; i++) {
            Instant hour = HOUR.plusSeconds(3600L * i);
            store().add(List.of(row(hour, "acme", "repo-" + i, FetchActivity.Result.ALLOWED, 1, hour)));
        }

        assertEquals(
                List.of("repo-4", "repo-3"),
                find(FetchActivityQuery.builder().limit(2)).stream()
                        .map(FetchActivity::getRepoName)
                        .toList());
        assertEquals(
                List.of("repo-2", "repo-1"),
                find(FetchActivityQuery.builder().limit(2).offset(2)).stream()
                        .map(FetchActivity::getRepoName)
                        .toList());
        assertEquals(
                "repo-0",
                find(FetchActivityQuery.builder().limit(1).newestFirst(false))
                        .getFirst()
                        .getRepoName());
    }

    @Test
    void find_onAnEmptyStore_isEmpty() {
        assertTrue(find(FetchActivityQuery.builder()).isEmpty());
    }

    @Test
    void summarizeByRepo_sumsCountsAcrossHours_leavesOutTheOverflowRow_largestFirst() {
        store().add(List.of(
                allowed("acme", "repo", 3),
                row(HOUR.plusSeconds(3600), "acme", "repo", FetchActivity.Result.ALLOWED, 2, HOUR),
                row(HOUR, "acme", "repo", FetchActivity.Result.BLOCKED, 4, HOUR),
                allowed("other", "repo", 1),
                allowed("acme", "repo", 0).toBuilder()
                        .id("overflow")
                        .owner(null)
                        .repoName(null)
                        .fetchCount(50)
                        .build()));

        List<RepoFetchSummary> summary = store().summarizeByRepo();

        assertEquals(
                List.of(
                        new RepoFetchSummary("github", "acme", "repo", 9, 4),
                        new RepoFetchSummary("github", "other", "repo", 1, 0)),
                summary);
    }

    @Test
    void pruneBefore_dropsHoursThatStartedBeforeTheCutoff() {
        store().add(List.of(
                row(HOUR, "acme", "old", FetchActivity.Result.ALLOWED, 1, HOUR),
                row(HOUR.plusSeconds(3600), "acme", "new", FetchActivity.Result.ALLOWED, 1, HOUR)));

        store().pruneBefore(HOUR.plusSeconds(3600));

        assertEquals(
                List.of("new"),
                find(FetchActivityQuery.builder()).stream()
                        .map(FetchActivity::getRepoName)
                        .toList());
    }
}
