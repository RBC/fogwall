package com.rbc.fogwall.scmapi;

import java.util.Map;

/**
 * Classifies a read (a non-mutating SCM API request) to a coarse {@code <resource>.read} operation for telemetry, so an
 * operator can tell an issue read from a pull/merge request or comment read rather than an opaque {@code read}.
 * Best-effort and low-cardinality.
 *
 * <p>Shaped by the fixed set of requests the CLIs actually send — like the mutation allowlists, not a provider's whole
 * schema (see the SCM API proxy notes). A REST read names its resource in the path; a GitHub GraphQL read is one of a
 * handful of named target-resolution queries, keyed on its {@code operationName}. Anything unrecognised is a plain
 * {@code read}.
 */
public final class ScmApiReadResource {

    private ScmApiReadResource() {}

    /**
     * A REST GET sub-path (below the dialect mount) → a coarse read operation, normalized across both REST dialects.
     */
    public static String fromRestPath(String path) {
        if (path == null) {
            return "read";
        }
        if (path.contains("/comments") || path.contains("/notes")) {
            return "comment.read";
        }
        if (path.contains("/merge_requests") || path.contains("/pulls")) {
            return "pull_request.read";
        }
        if (path.contains("/issues")) {
            return "issue.read";
        }
        return "other.read";
    }

    /**
     * The named GitHub GraphQL read queries {@code gh} sends to resolve a target before a mutation, each mapped to the
     * resource it reads. From observed traffic (the SCM API proxy notes); an unlisted name — a gh version change — is a
     * plain {@code read}.
     */
    private static final Map<String, String> GITHUB_QUERY_READS = Map.of(
            "IssueRepositoryInfo", "repository.read",
            "RepositoryInfo", "repository.read",
            "IssueByNumber", "issue.read",
            "PullRequestByNumber", "pull_request.read",
            "PullRequestForBranch", "pull_request.read",
            "PullRequestProjectItems", "pull_request.read");

    /**
     * A GitHub GraphQL read keyed by its {@code operationName} (already parsed by the gate — no re-parse).
     * {@code operationName} is client-set and gh-version-specific, so this is a best-effort label with a {@code read}
     * fallback, never an input to a decision.
     */
    public static String fromGraphQl(String operationName) {
        return operationName == null ? "read" : GITHUB_QUERY_READS.getOrDefault(operationName, "read");
    }
}
