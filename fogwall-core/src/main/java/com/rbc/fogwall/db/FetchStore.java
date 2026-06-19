package com.rbc.fogwall.db;

import com.rbc.fogwall.db.model.FetchRecord;
import java.util.List;

/**
 * Append-only audit log for fetch (and info/refs) requests through the proxy. Analogous to {@link PushStore} but
 * intentionally lightweight — no steps, commits, or attestations.
 */
public interface FetchStore {

    /** Record a fetch event. */
    void record(FetchRecord fetchRecord);

    /**
     * Return the most recent fetch records, newest first.
     *
     * @param limit maximum number of records to return
     */
    List<FetchRecord> findRecent(int limit);

    /**
     * Return fetch records for a specific repo, newest first.
     *
     * @param provider provider name
     * @param owner repository owner/org
     * @param repoName repository name
     * @param limit maximum number of records to return
     */
    List<FetchRecord> findByRepo(String provider, String owner, String repoName, int limit);

    /**
     * Summarise fetch activity grouped by provider + owner + repo_name. Each entry contains the repo coordinates plus
     * total fetch count and blocked fetch count.
     */
    List<RepoFetchSummary> summarizeByRepo();

    /** Initialize the store (run migrations). Called once at startup. */
    void initialize();

    /** Per-repo aggregate returned by {@link #summarizeByRepo()}. */
    record RepoFetchSummary(String provider, String owner, String repoName, long total, long blocked) {}
}
