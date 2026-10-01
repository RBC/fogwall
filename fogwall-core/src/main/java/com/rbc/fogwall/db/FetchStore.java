package com.rbc.fogwall.db;

import com.rbc.fogwall.db.model.FetchActivity;
import com.rbc.fogwall.db.model.FetchActivityQuery;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Hourly fetch activity, written in batches by {@link FetchActivityRecorder} rather than once per request. Analogous to
 * {@link PushStore} for the read path, at a deliberately coarser grain.
 */
public interface FetchStore {

    /**
     * Adds each row's {@code fetchCount} to the stored row with the same id, creating it when there is none, and keeps
     * the later of the two {@code lastSeen}s. Safe for several fogwall instances adding to the same row.
     */
    void add(Collection<FetchActivity> increments);

    /** Return activity rows matching the query, by hour. */
    List<FetchActivity> find(FetchActivityQuery query);

    /**
     * Summarise fetch activity grouped by provider + owner + repo_name: total fetch count and blocked fetch count. The
     * row collecting fetches past the in-memory key cap names no repository and is left out.
     */
    List<RepoFetchSummary> summarizeByRepo();

    /** Delete rows for hours that started before {@code cutoff}. */
    void pruneBefore(Instant cutoff);

    /** Initialize the store (run migrations). Called once at startup. */
    void initialize();

    /** Per-repo aggregate returned by {@link #summarizeByRepo()}. */
    record RepoFetchSummary(String provider, String owner, String repoName, long total, long blocked) {}
}
