package com.rbc.fogwall.scmapi;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GitHubMutationAllowlistTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "createIssue",
                "updateIssue",
                "closeIssue",
                "createPullRequest",
                "updatePullRequest",
                "closePullRequest",
                "addComment"
            })
    void allowsEachInScopeMutationField(String field) {
        assertTrue(GitHubMutationAllowlist.isAllowed(field));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "deleteIssue",
                "mergePullRequest",
                // Review is out of scope — reviewers use the SCM's own UI, so fogwall never forwards these.
                "addPullRequestReview",
                "submitPullRequestReview",
                "addPullRequestReviewComment",
                "createRepository",
                "updateRepository",
                "deleteRepository",
                "createIssue ", // trailing whitespace must not fuzzy-match
                ""
            })
    void deniesAnyUnrecognizedMutationField(String field) {
        assertFalse(GitHubMutationAllowlist.isAllowed(field));
    }
}
