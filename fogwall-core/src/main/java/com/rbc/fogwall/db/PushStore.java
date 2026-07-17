package com.rbc.fogwall.db;

import com.rbc.fogwall.db.model.Attestation;
import com.rbc.fogwall.db.model.PushQuery;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStatus;
import com.rbc.fogwall.db.model.PushSummary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Storage abstraction for push records. Implementations exist for in-memory, JDBC (H2, SQLite, Postgres), and MongoDB.
 *
 * <p>This is the Java equivalent of fogwall's Sink interface.
 */
public interface PushStore {

    /**
     * Persist a push record (insert or update). When a push is first received, call this to create the record. Call
     * again after each validation step or status change to update it.
     */
    void save(PushRecord record);

    /** Find a push by its ID. */
    Optional<PushRecord> findById(String id);

    /** Query pushes with optional filters. */
    List<PushRecord> find(PushQuery query);

    /**
     * Return lightweight summary projections for the list view. Omits all child collections (steps, commits,
     * attestation). Default implementation delegates to {@link #find} and projects in memory; JDBC override uses a lean
     * SELECT.
     */
    default List<PushSummary> findSummaries(PushQuery query) {
        return find(query).stream()
                .map(r -> PushSummary.builder()
                        .id(r.getId())
                        .status(r.getStatus())
                        .url(r.getUrl())
                        .upstreamUrl(r.getUpstreamUrl())
                        .provider(r.getProvider())
                        .project(r.getProject())
                        .repoName(r.getRepoName())
                        .branch(r.getBranch())
                        .commitTo(r.getCommitTo())
                        .author(r.getAuthor())
                        .user(r.getUser())
                        .resolvedUser(r.getResolvedUser())
                        .timestamp(r.getTimestamp())
                        .build())
                .toList();
    }

    /** Delete a push record and all associated data. */
    void delete(String id);

    /**
     * Approve a push. Sets status to APPROVED and records the attestation.
     *
     * @return the updated record
     */
    PushRecord approve(String id, Attestation attestation);

    /**
     * Reject a push. Sets status to REJECTED and records the attestation.
     *
     * @return the updated record
     */
    PushRecord reject(String id, Attestation attestation);

    /**
     * Cancel a push. Sets status to CANCELED and records the attestation.
     *
     * @return the updated record
     */
    PushRecord cancel(String id, Attestation attestation);

    /**
     * Update the forward status of a transparent-proxy re-push after the upstream HTTP response is received. Only
     * touches {@code status} and (on error) {@code errorMessage} — does not modify attestation or steps.
     *
     * @param id the push record ID
     * @param status {@link PushStatus#FORWARDED} on success, {@link PushStatus#ERROR} on failure
     * @param errorMessage human-readable upstream error detail; {@code null} for FORWARDED
     */
    void updateForwardStatus(String id, PushStatus status, String errorMessage);

    /** Initialize the store (create tables, indexes, etc.). Called once at startup. */
    void initialize();

    /** Close resources. Called on shutdown. */
    default void close() {}

    /**
     * Summarise push activity grouped by provider + project + repo_name. Returns one entry per distinct repo with total
     * push count. Default implementation is correct but unoptimised; JDBC override uses a SQL aggregate.
     */
    default List<RepoPushSummary> summarizeByRepo() {
        record Key(String provider, String owner, String repoName) {}
        Map<Key, Long> counts = new java.util.LinkedHashMap<>();
        for (PushRecord r : find(PushQuery.builder().limit(Integer.MAX_VALUE).build())) {
            Key k = new Key(
                    r.getProvider() != null ? r.getProvider() : "",
                    r.getProject() != null ? r.getProject() : "",
                    r.getRepoName() != null ? r.getRepoName() : "");
            counts.merge(k, 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .map(e -> new RepoPushSummary(
                        e.getKey().provider(), e.getKey().owner(), e.getKey().repoName(), e.getValue()))
                .toList();
    }

    /**
     * Count push records for a user grouped by status. Accepts the same filters as {@link #find(PushQuery)} except
     * {@code status} is ignored — counts are returned for all statuses. Default implementation is correct but
     * unoptimised; JDBC override uses a SQL aggregate.
     */
    default Map<String, Long> countByStatus(PushQuery query) {
        PushQuery noStatus = PushQuery.builder()
                .project(query.getProject())
                .repoName(query.getRepoName())
                .branch(query.getBranch())
                .user(query.getUser())
                .authorEmail(query.getAuthorEmail())
                .commitTo(query.getCommitTo())
                .search(query.getSearch())
                .limit(Integer.MAX_VALUE)
                .build();
        Map<String, Long> result = new HashMap<>();
        for (PushRecord r : find(noStatus)) {
            result.merge(r.getStatus().name(), 1L, Long::sum);
        }
        return result;
    }

    /**
     * Aggregate push status counts for all users in a single pass, returning a map of username → (status → count).
     * Default implementation is correct but unoptimised; JDBC override uses a SQL aggregate.
     */
    default Map<String, Map<String, Long>> countPushStatusByUser() {
        Map<String, Map<String, Long>> result = new HashMap<>();
        for (PushRecord r : find(PushQuery.builder().limit(Integer.MAX_VALUE).build())) {
            if (r.getResolvedUser() == null) continue;
            result.computeIfAbsent(r.getResolvedUser(), k -> new HashMap<>())
                    .merge(r.getStatus().name(), 1L, Long::sum);
        }
        return result;
    }

    /** Per-repo aggregate returned by {@link #summarizeByRepo()}. */
    record RepoPushSummary(String provider, String owner, String repoName, long total) {}
}
