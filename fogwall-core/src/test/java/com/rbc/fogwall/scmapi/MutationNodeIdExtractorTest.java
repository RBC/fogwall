package com.rbc.fogwall.scmapi;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class MutationNodeIdExtractorTest {

    private static final JsonMapper MAPPER = new JsonMapper();

    @Test
    void createIssue_extractsRepositoryIdFromInput() {
        var variables = MAPPER.readTree("{\"input\":{\"repositoryId\":\"R_1\"}}");
        var ref = MutationNodeIdExtractor.extract("createIssue", variables).orElseThrow();
        assertEquals("R_1", ref.nodeId());
        assertEquals(MutationNodeIdRef.NodeType.REPOSITORY, ref.nodeType());
    }

    @Test
    void createPullRequest_extractsRepositoryIdFromInput() {
        var variables = MAPPER.readTree("{\"input\":{\"repositoryId\":\"R_2\"}}");
        var ref =
                MutationNodeIdExtractor.extract("createPullRequest", variables).orElseThrow();
        assertEquals("R_2", ref.nodeId());
        assertEquals(MutationNodeIdRef.NodeType.REPOSITORY, ref.nodeType());
    }

    @Test
    void updateIssue_extractsIdFromInput() {
        var variables = MAPPER.readTree("{\"input\":{\"id\":\"I_1\"}}");
        var ref = MutationNodeIdExtractor.extract("updateIssue", variables).orElseThrow();
        assertEquals("I_1", ref.nodeId());
        assertEquals(MutationNodeIdRef.NodeType.ISSUE, ref.nodeType());
    }

    @Test
    void closeIssue_extractsIssueIdFromInput() {
        var variables = MAPPER.readTree("{\"input\":{\"issueId\":\"I_2\"}}");
        var ref = MutationNodeIdExtractor.extract("closeIssue", variables).orElseThrow();
        assertEquals("I_2", ref.nodeId());
        assertEquals(MutationNodeIdRef.NodeType.ISSUE, ref.nodeType());
    }

    @Test
    void addComment_extractsSubjectIdFromInput_asIssueOrPullRequest() {
        var variables = MAPPER.readTree("{\"input\":{\"subjectId\":\"PR_1\"}}");
        var ref = MutationNodeIdExtractor.extract("addComment", variables).orElseThrow();
        assertEquals("PR_1", ref.nodeId());
        assertEquals(MutationNodeIdRef.NodeType.ISSUE_OR_PULL_REQUEST, ref.nodeType());
    }

    @Test
    void updatePullRequest_extractsPullRequestIdFromInput() {
        var variables = MAPPER.readTree("{\"input\":{\"pullRequestId\":\"PR_2\"}}");
        var ref =
                MutationNodeIdExtractor.extract("updatePullRequest", variables).orElseThrow();
        assertEquals("PR_2", ref.nodeId());
        assertEquals(MutationNodeIdRef.NodeType.PULL_REQUEST, ref.nodeType());
    }

    /** Review is out of scope, so the extractor carries no entry for it — the allowlist denies it first regardless. */
    @Test
    void addPullRequestReview_hasNoEntry() {
        var variables = MAPPER.readTree("{\"input\":{\"pullRequestId\":\"PR_3\"}}");
        assertTrue(MutationNodeIdExtractor.extract("addPullRequestReview", variables)
                .isEmpty());
    }

    @Test
    void closePullRequest_extractsPullRequestIdFromInput() {
        var variables = MAPPER.readTree("{\"input\":{\"pullRequestId\":\"PR_4\"}}");
        var ref = MutationNodeIdExtractor.extract("closePullRequest", variables).orElseThrow();
        assertEquals("PR_4", ref.nodeId());
        assertEquals(MutationNodeIdRef.NodeType.PULL_REQUEST, ref.nodeType());
    }

    @Test
    void unknownMutationField_returnsEmpty() {
        var variables = MAPPER.readTree("{\"input\":{\"repositoryId\":\"R_1\"}}");
        assertTrue(MutationNodeIdExtractor.extract("deleteIssue", variables).isEmpty());
    }

    @Test
    void nullVariables_returnsEmpty() {
        assertTrue(MutationNodeIdExtractor.extract("createIssue", null).isEmpty());
    }

    @Test
    void missingExpectedKey_returnsEmpty() {
        var variables = MAPPER.readTree("{\"input\":{}}");
        assertTrue(MutationNodeIdExtractor.extract("createIssue", variables).isEmpty());
    }

    @Test
    void nonTextualNodeIdValue_returnsEmpty() {
        var variables = MAPPER.readTree("{\"input\":{\"repositoryId\":123}}");
        assertTrue(MutationNodeIdExtractor.extract("createIssue", variables).isEmpty());
    }
}
