package com.rbc.fogwall.db.model;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * What kind of contribution an SCM API action made, read off its {@link ScmApiActionRecord#getMutationField()}. One
 * vocabulary across the GitHub GraphQL dialect and the GitLab and Forgejo REST dialects, so the same contribution
 * counts the same on every provider.
 *
 * <p>Closing and reopening count as an edit: Forgejo's {@code tea} sends a close as the same request as an edit, so
 * telling the two apart would hold on some providers and not others.
 */
public enum ScmApiActivity {
    OPEN_PULL_REQUEST("createPullRequest", "merge_requests.create", "pulls.create"),
    OPEN_ISSUE("createIssue", "issues.create"),
    EDIT(
            "updateIssue",
            "closeIssue",
            "reopenIssue",
            "updatePullRequest",
            "closePullRequest",
            "reopenPullRequest",
            "markPullRequestReadyForReview",
            "convertPullRequestToDraft",
            "replaceActorsForAssignable",
            "addLabelsToLabelable",
            "removeLabelsFromLabelable",
            "requestReviewsByLogin",
            "issues.update",
            "merge_requests.update",
            "pulls.update",
            "issues.labels.add",
            "issues.assignees.add",
            "issues.assignees.remove"),
    COMMENT(
            "addComment",
            "updateIssueComment",
            "deleteIssueComment",
            "issues.note",
            "merge_requests.note",
            "issues.comment",
            "issues.comment.update"),
    MERGE("mergePullRequest", "merge_requests.merge", "pulls.merge"),
    /** A mutation fogwall does not forward, recorded only because it was refused. */
    OTHER;

    private final Set<String> mutationFields;

    ScmApiActivity(String... mutationFields) {
        this.mutationFields = Set.of(mutationFields);
    }

    public Set<String> mutationFields() {
        return mutationFields;
    }

    public boolean opens() {
        return this == OPEN_PULL_REQUEST || this == OPEN_ISSUE;
    }

    /** Every activity but {@link #OTHER}, which is what matches none of their fields. */
    public static List<ScmApiActivity> classified() {
        return Arrays.stream(values()).filter(a -> a != OTHER).toList();
    }

    public static ScmApiActivity of(String mutationField) {
        if (mutationField == null) {
            return OTHER;
        }
        return classified().stream()
                .filter(a -> a.mutationFields.contains(mutationField))
                .findFirst()
                .orElse(OTHER);
    }
}
