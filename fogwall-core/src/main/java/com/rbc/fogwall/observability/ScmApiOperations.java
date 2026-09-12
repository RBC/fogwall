package com.rbc.fogwall.observability;

import java.util.Map;

/**
 * Maps a per-dialect SCM API operation to a provider-agnostic name for telemetry, so the {@code operation} metric/span
 * attribute aggregates across providers. {@code pull_request} is the normalized name for a pull request
 * (GitHub/Forgejo/Gitea) or merge request (GitLab).
 *
 * <p>Only the verbs that flatten cleanly are normalized — create/update/close/merge on a pull/merge request or issue,
 * and comment create/update. GitLab and Forgejo fold "close" into an update, so {@code pull_request.close} and
 * {@code issue.close} arise only where the dialect exposes a distinct close (GitHub). An operation with no clean
 * cross-provider meaning — a label, assignee, or reviewer edit, whose subject the raw op does not name — passes through
 * unchanged and is disambiguated by the separate {@code provider} attribute rather than flattened into a misleading
 * verb. Raw op names do not collide across dialects, so this keys on the string alone.
 */
public final class ScmApiOperations {

    private ScmApiOperations() {}

    private static final Map<String, String> NORMALIZED = Map.ofEntries(
            // GitHub GraphQL mutation fields
            Map.entry("createPullRequest", "pull_request.create"),
            Map.entry("updatePullRequest", "pull_request.update"),
            Map.entry("closePullRequest", "pull_request.close"),
            Map.entry("mergePullRequest", "pull_request.merge"),
            Map.entry("createIssue", "issue.create"),
            Map.entry("updateIssue", "issue.update"),
            Map.entry("closeIssue", "issue.close"),
            Map.entry("addComment", "comment.create"),
            // GitLab REST operations
            Map.entry("merge_requests.create", "pull_request.create"),
            Map.entry("merge_requests.update", "pull_request.update"),
            Map.entry("merge_requests.merge", "pull_request.merge"),
            Map.entry("merge_requests.note", "comment.create"),
            Map.entry("issues.create", "issue.create"),
            Map.entry("issues.update", "issue.update"),
            Map.entry("issues.note", "comment.create"),
            // Forgejo/Gitea REST operations
            Map.entry("pulls.create", "pull_request.create"),
            Map.entry("pulls.update", "pull_request.update"),
            Map.entry("pulls.merge", "pull_request.merge"),
            Map.entry("issues.comment", "comment.create"),
            Map.entry("issues.comment.update", "comment.update"));

    /**
     * The provider-agnostic name for {@code rawOperation}, or {@code rawOperation} unchanged when it has no clean
     * cross-provider mapping. Returns {@code null} for a {@code null} input.
     */
    public static String normalize(String rawOperation) {
        if (rawOperation == null) {
            return null;
        }
        return NORMALIZED.getOrDefault(rawOperation, rawOperation);
    }
}
