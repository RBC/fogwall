package com.rbc.fogwall.scmapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rbc.fogwall.db.model.ScmApiProposalRecord.Kind;
import com.rbc.fogwall.db.model.ScmApiProposalRecord.State;
import com.rbc.fogwall.servlet.ScmApiRequestContext;
import org.junit.jupiter.api.Test;

/** Response shapes as {@code gh}'s GraphQL mutations actually return them, captured from real traffic. */
class GitHubProposalResponseReaderTest {

    private final GitHubProposalResponseReader reader = new GitHubProposalResponseReader();

    private static ScmApiRequestContext context(String field, String nodeId, String nodeType, String variablesJson) {
        var c = new ScmApiRequestContext();
        c.setMutationField(field);
        c.setNodeId(nodeId);
        c.setNodeType(nodeType);
        c.setVariablesJson(variablesJson);
        return c;
    }

    // gh selects only id and url on a create, and only clientMutationId on everything else.
    @Test
    void createPullRequest_readsIdUrlAndNumberFromUrl() {
        ProposalOutcome o = reader.read(
                        context(
                                "createPullRequest",
                                "R_repo",
                                "REPOSITORY",
                                "{\"input\":{\"repositoryId\":\"R_repo\",\"title\":\"Proposed via gh\",\"baseRefName\":\"main\"}}"),
                        null,
                        "{\"data\":{\"createPullRequest\":{\"pullRequest\":{\"id\":\"PR_kwDO1\",\"url\":\"https://github.com/acme/widgets/pull/7\"}}}}")
                .orElseThrow();
        assertEquals(Kind.PULL_REQUEST, o.kind());
        assertEquals(7, o.number());
        assertEquals("PR_kwDO1", o.nodeId());
        assertEquals("https://github.com/acme/widgets/pull/7", o.url());
        assertEquals("Proposed via gh", o.title());
        assertEquals(State.OPEN, o.state());
    }

    @Test
    void createIssue_withErrorsAndNoObject_isEmpty() {
        assertTrue(reader.read(
                        context("createIssue", "R_repo", "REPOSITORY", null),
                        null,
                        "{\"data\":{\"createIssue\":null},\"errors\":[{\"message\":\"nope\"}]}")
                .isEmpty());
    }

    @Test
    void closeAndUpdate_areKeyedOnTheAddressedNode() {
        ProposalOutcome close = reader.read(
                        context("closePullRequest", "PR_kwDO1", "PULL_REQUEST", null),
                        null,
                        "{\"data\":{\"closePullRequest\":{\"clientMutationId\":null}}}")
                .orElseThrow();
        assertEquals("PR_kwDO1", close.nodeId());
        assertEquals(State.CLOSED, close.state());
        assertNull(close.number());

        ProposalOutcome edit = reader.read(
                        context(
                                "updateIssue",
                                "I_kwDO2",
                                "ISSUE",
                                "{\"input\":{\"id\":\"I_kwDO2\",\"body\":\"edited\"}}"),
                        null,
                        "{\"data\":{\"updateIssue\":{\"clientMutationId\":null}}}")
                .orElseThrow();
        assertEquals(Kind.ISSUE, edit.kind());
        assertNull(edit.state(), "an update says nothing about state");
        assertNull(edit.title(), "no title in the input, so none reported");
    }

    @Test
    void addComment_hasNoKindWhenTheNodeCouldBeEither() {
        ProposalOutcome o = reader.read(context("addComment", "I_kwDO2", "ISSUE_OR_PULL_REQUEST", null), null, "{}")
                .orElseThrow();
        assertNull(o.kind());
        assertEquals("I_kwDO2", o.nodeId());
    }

    @Test
    void aRead_isEmpty() {
        assertTrue(reader.read(context(null, null, null, null), null, "{}").isEmpty());
    }
}
