package com.rbc.fogwall.observability;

import java.util.Map;

/**
 * Maps a per-dialect SCM API operation to a provider-agnostic name for telemetry, so the {@code operation} metric/span
 * attribute aggregates across providers. {@code proposal} is fogwall's generic term for a pull request
 * (GitHub/Forgejo/Gitea) or merge request (GitLab).
 *
 * <p>Only the verbs that flatten cleanly are normalized — create/update/close/merge on a proposal or issue, and comment
 * create/update. GitLab and Forgejo fold "close" into an update, so {@code proposal.close} and {@code issue.close}
 * arise only where the dialect exposes a distinct close (GitHub). An operation with no clean cross-provider meaning — a
 * label, assignee, or reviewer edit, whose subject the raw op does not name — passes through unchanged and is
 * disambiguated by the separate {@code provider} attribute rather than flattened into a misleading verb. Raw op names
 * do not collide across dialects, so this keys on the string alone.
 */
public final class ScmApiOperations {

    private ScmApiOperations() {}

    private static final Map<String, String> NORMALIZED = Map.ofEntries(
            // GitHub GraphQL mutation fields
            Map.entry("createPullRequest", "proposal.create"),
            Map.entry("updatePullRequest", "proposal.update"),
            Map.entry("closePullRequest", "proposal.close"),
            Map.entry("mergePullRequest", "proposal.merge"),
            Map.entry("createIssue", "issue.create"),
            Map.entry("updateIssue", "issue.update"),
            Map.entry("closeIssue", "issue.close"),
            Map.entry("addComment", "comment.create"),
            // GitLab REST operations
            Map.entry("merge_requests.create", "proposal.create"),
            Map.entry("merge_requests.update", "proposal.update"),
            Map.entry("merge_requests.merge", "proposal.merge"),
            Map.entry("merge_requests.note", "comment.create"),
            Map.entry("issues.create", "issue.create"),
            Map.entry("issues.update", "issue.update"),
            Map.entry("issues.note", "comment.create"),
            // Forgejo/Gitea REST operations
            Map.entry("pulls.create", "proposal.create"),
            Map.entry("pulls.update", "proposal.update"),
            Map.entry("pulls.merge", "proposal.merge"),
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
