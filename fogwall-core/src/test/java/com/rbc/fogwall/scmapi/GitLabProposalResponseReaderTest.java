package com.rbc.fogwall.scmapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rbc.fogwall.db.model.ScmApiProposalRecord.Kind;
import com.rbc.fogwall.db.model.ScmApiProposalRecord.State;
import com.rbc.fogwall.servlet.ScmApiRequestContext;
import org.junit.jupiter.api.Test;

/** Response shapes as GitLab returns them to {@code glab}, captured from real traffic. */
class GitLabProposalResponseReaderTest {

    private final GitLabProposalResponseReader reader = new GitLabProposalResponseReader();

    private static ScmApiRequestContext op(String operation) {
        var c = new ScmApiRequestContext();
        c.setMutationField(operation);
        return c;
    }

    @Test
    void createMergeRequest_readsIidWebUrlAndState() {
        ProposalOutcome o = reader.read(
                        op("merge_requests.create"),
                        "/projects/acme%2Fwidgets/merge_requests",
                        "{\"id\":9001,\"iid\":3,\"title\":\"Proposed via glab\",\"state\":\"opened\",\"web_url\":\"https://gitlab.com/acme/widgets/-/merge_requests/3\"}")
                .orElseThrow();
        assertEquals(Kind.PULL_REQUEST, o.kind());
        assertEquals(3, o.number());
        assertEquals(State.OPEN, o.state());
        assertEquals("https://gitlab.com/acme/widgets/-/merge_requests/3", o.url());
        assertEquals("Proposed via glab", o.title());
    }

    // Verified live: the response is the full updated MR object, including merge_commit_sha — the one dialect whose
    // merge response actually names what the merge produced.
    @Test
    void mergeMergeRequest_readsMergedStateAndCommitSha() {
        ProposalOutcome o = reader.read(
                        op("merge_requests.merge"),
                        "/projects/acme%2Fwidgets/merge_requests/11/merge",
                        "{\"iid\":11,\"state\":\"merged\",\"web_url\":\"https://gitlab.com/acme/widgets/-/merge_requests/11\",\"merge_commit_sha\":\"3a0bc7f011690f05b3baf5821b10d17b3be247d5\"}")
                .orElseThrow();
        assertEquals(Kind.PULL_REQUEST, o.kind());
        assertEquals(State.MERGED, o.state());
        assertEquals("3a0bc7f011690f05b3baf5821b10d17b3be247d5", o.mergeCommitSha());
    }

    @Test
    void updateWithStateEvent_reportsTheNewState() {
        ProposalOutcome o = reader.read(
                        op("issues.update"),
                        "/projects/acme%2Fwidgets/issues/1",
                        "{\"iid\":1,\"state\":\"closed\",\"web_url\":\"https://gitlab.com/acme/widgets/-/issues/1\"}")
                .orElseThrow();
        assertEquals(Kind.ISSUE, o.kind());
        assertEquals(State.CLOSED, o.state());
    }

    @Test
    void note_namesItsIssueAndNothingElse() {
        ProposalOutcome o = reader.read(
                        op("issues.note"),
                        "/projects/acme%2Fwidgets/issues/1/notes",
                        "{\"id\":55,\"noteable_iid\":1,\"body\":\"hi\"}")
                .orElseThrow();
        assertEquals(1, o.number());
        assertNull(o.url());
        assertNull(o.state());
    }

    @Test
    void unparseableOrMissingBody_stillNamesTheTargetWhenThePathDoes() {
        assertEquals(
                8,
                reader.read(op("issues.update"), "/projects/a%2Fb/issues/8", null)
                        .orElseThrow()
                        .number());
        assertTrue(reader.read(op(null), "/x", "{}").isEmpty());
    }
}
