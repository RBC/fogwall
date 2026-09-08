package com.rbc.fogwall.scmapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rbc.fogwall.db.InMemoryScmApiProposalStore;
import com.rbc.fogwall.db.model.ScmApiProposalRecord;
import com.rbc.fogwall.db.model.ScmApiProposalRecord.Kind;
import com.rbc.fogwall.db.model.ScmApiProposalRecord.State;
import com.rbc.fogwall.servlet.ScmApiRequestContext;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ProposalRegistrarTest {

    private final InMemoryScmApiProposalStore store = new InMemoryScmApiProposalStore();
    private final ProposalRegistrar github = new ProposalRegistrar(store, new GitHubProposalResponseReader());
    private final ProposalRegistrar gitlab = new ProposalRegistrar(store, new GitLabProposalResponseReader());
    private final ProposalRegistrar forgejo = new ProposalRegistrar(store, new ForgejoProposalResponseReader());

    private static ScmApiRequestContext context(String provider, String field, String nodeId) {
        var c = new ScmApiRequestContext();
        c.setProvider(provider);
        c.setResolvedUser("alice");
        c.setScmLogin("alice-scm");
        c.setRepoOwner("acme");
        c.setRepoName("widgets");
        c.setMutationField(field);
        c.setNodeId(nodeId);
        return c;
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void createThenCloseOnGitHub_oneRow_closedByNodeId() {
        var create = context("github", "createPullRequest", "R_1");
        create.setVariablesJson("{\"input\":{\"title\":\"t\"}}");
        github.recordUpstreamResponse(
                create,
                null,
                200,
                bytes(
                        "{\"data\":{\"createPullRequest\":{\"pullRequest\":{\"id\":\"PR_9\",\"url\":\"https://github.com/acme/widgets/pull/7\"}}}}"));
        assertEquals(200, create.getUpstreamStatus());
        assertNotNull(create.getProposalId());
        ScmApiProposalRecord row = store.findById(create.getProposalId()).orElseThrow();
        assertEquals(7, row.getNumber());
        assertEquals(State.OPEN, row.getState());
        assertEquals("alice", row.getCreatedBy());
        assertEquals(create.getActionId(), row.getCreatedActionId());

        var close = context("github", "closePullRequest", "PR_9");
        github.recordUpstreamResponse(
                close, null, 200, bytes("{\"data\":{\"closePullRequest\":{\"clientMutationId\":null}}}"));
        assertEquals(row.getId(), close.getProposalId());
        assertEquals(1, store.all().size());
        ScmApiProposalRecord after = store.findById(row.getId()).orElseThrow();
        assertEquals(State.CLOSED, after.getState());
        assertEquals(close.getActionId(), after.getLastActionId());
        assertEquals(create.getActionId(), after.getCreatedActionId(), "the create is still the create");
    }

    @Test
    void closeOfAnUnknownGitHubNode_registersNothing_butRecordsTheStatus() {
        var close = context("github", "closeIssue", "I_unknown");
        github.recordUpstreamResponse(close, null, 200, bytes("{\"data\":{}}"));
        assertEquals(200, close.getUpstreamStatus());
        assertNull(close.getProposalId());
        assertTrue(store.all().isEmpty());
    }

    @Test
    void restUpdateOnAProposalOpenedOutsideFogwall_registersItFromTheResponse() {
        var update = context("gitlab", "merge_requests.update", null);
        gitlab.recordUpstreamResponse(
                update,
                "/projects/acme%2Fwidgets/merge_requests/12",
                200,
                bytes(
                        "{\"iid\":12,\"state\":\"closed\",\"title\":\"old\",\"web_url\":\"https://gitlab.com/acme/widgets/-/merge_requests/12\"}"));
        ScmApiProposalRecord row = store.findById(update.getProposalId()).orElseThrow();
        assertEquals(Kind.PULL_REQUEST, row.getKind());
        assertEquals(12, row.getNumber());
        assertEquals(State.CLOSED, row.getState());
        assertEquals(update.getActionId(), row.getCreatedActionId());
    }

    @Test
    void upstreamFailure_recordsTheStatusAndTouchesNothing() {
        var create = context("gitea", "pulls.create", null);
        forgejo.recordUpstreamResponse(create, "/repos/acme/widgets/pulls", 422, bytes("{\"message\":\"exists\"}"));
        assertEquals(422, create.getUpstreamStatus());
        assertNull(create.getProposalId());
        assertTrue(store.all().isEmpty());
    }

    @Test
    void aCommentOnlyTouchesTheRow() {
        var create = context("gitea", "issues.create", null);
        forgejo.recordUpstreamResponse(
                create,
                "/repos/acme/widgets/issues",
                201,
                bytes(
                        "{\"number\":2,\"title\":\"Reported\",\"state\":\"open\",\"html_url\":\"https://gitea.com/acme/widgets/issues/2\"}"));
        var comment = context("gitea", "issues.comment", null);
        forgejo.recordUpstreamResponse(
                comment,
                "/repos/acme/widgets/issues/2/comments",
                201,
                bytes("{\"id\":5,\"issue_url\":\"https://gitea.com/api/v1/repos/acme/widgets/issues/2\"}"));
        ScmApiProposalRecord row = store.findById(create.getProposalId()).orElseThrow();
        assertEquals(comment.getProposalId(), row.getId());
        assertEquals("Reported", row.getTitle());
        assertEquals("https://gitea.com/acme/widgets/issues/2", row.getUrl(), "a comment's own URL never replaces it");
        assertEquals(comment.getActionId(), row.getLastActionId());
    }

    @Test
    void aReadIsNeverRegistered() {
        var read = context("gitea", null, null);
        forgejo.recordUpstreamResponse(read, "/repos/acme/widgets/issues/2", 200, bytes("{\"number\":2}"));
        assertTrue(store.all().isEmpty());
    }
}
