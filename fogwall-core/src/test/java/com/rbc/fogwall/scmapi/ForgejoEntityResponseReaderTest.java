package com.rbc.fogwall.scmapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rbc.fogwall.db.model.ScmApiEntityRecord.Kind;
import com.rbc.fogwall.db.model.ScmApiEntityRecord.State;
import com.rbc.fogwall.servlet.ScmApiRequestContext;
import org.junit.jupiter.api.Test;

/** Response shapes as Gitea/Forgejo return them to {@code tea} and {@code fj}, captured from real traffic. */
class ForgejoEntityResponseReaderTest {

    private final ForgejoEntityResponseReader reader = new ForgejoEntityResponseReader();

    private static ScmApiRequestContext op(String operation) {
        var c = new ScmApiRequestContext();
        c.setMutationField(operation);
        return c;
    }

    @Test
    void createPull_readsNumberHtmlUrlAndState() {
        EntityOutcome o = reader.read(
                        op("pulls.create"),
                        "/repos/acme/widgets/pulls",
                        "{\"id\":77,\"number\":4,\"title\":\"Proposed via tea\",\"state\":\"open\",\"merged\":false,\"html_url\":\"https://gitea.com/acme/widgets/pulls/4\",\"head\":{\"ref\":\"x\"},\"base\":{\"ref\":\"main\"}}")
                .orElseThrow();
        assertEquals(Kind.PULL_REQUEST, o.kind());
        assertEquals(4, o.number());
        assertEquals(State.OPEN, o.state());
        assertEquals("https://gitea.com/acme/widgets/pulls/4", o.url());
    }

    // Per Gitea's own server source, the merge endpoint returns a bare 200 with no body — so the target comes only
    // from the request path, state is MERGED by construction, and no merge commit SHA is available to record.
    @Test
    void merge_emptyBody_stillRecordsMergedFromThePath() {
        EntityOutcome o = reader.read(op("pulls.merge"), "/repos/acme/widgets/pulls/4/merge", null)
                .orElseThrow();
        assertEquals(Kind.PULL_REQUEST, o.kind());
        assertEquals(4, o.number());
        assertEquals(State.MERGED, o.state());
        assertNull(o.mergeCommitSha());
    }

    @Test
    void teaClosesAPullThroughTheIssueEndpoint_kindComesFromTheResponse() {
        // tea pr close is PATCH /issues/{n} with the full object; the response is an Issue carrying pull_request.
        EntityOutcome o = reader.read(
                        op("issues.update"),
                        "/repos/acme/widgets/issues/4",
                        "{\"number\":4,\"state\":\"closed\",\"html_url\":\"https://gitea.com/acme/widgets/pulls/4\",\"pull_request\":{\"merged\":false,\"merged_at\":null}}")
                .orElseThrow();
        assertEquals(Kind.PULL_REQUEST, o.kind());
        assertEquals(State.CLOSED, o.state());
    }

    @Test
    void mergedPull_reportsMerged() {
        EntityOutcome o = reader.read(
                        op("pulls.update"),
                        "/repos/acme/widgets/pulls/4",
                        "{\"number\":4,\"state\":\"closed\",\"merged\":true,\"head\":{},\"base\":{}}")
                .orElseThrow();
        assertEquals(State.MERGED, o.state());
    }

    @Test
    void comment_namesItsIssueFromTheCommentsIssueUrl() {
        EntityOutcome o = reader.read(
                        op("issues.comment"),
                        "/repos/acme/widgets/issues/2/comments",
                        "{\"id\":9,\"issue_url\":\"https://gitea.com/api/v1/repos/acme/widgets/issues/2\",\"body\":\"hi\"}")
                .orElseThrow();
        assertEquals(2, o.number());
        assertNull(o.kind());
    }

    @Test
    void labelWrite_fallsBackToTheNumberInThePath() {
        EntityOutcome o = reader.read(
                        op("issues.labels.add"), "/repos/acme/widgets/issues/2/labels", "[{\"id\":1,\"name\":\"bug\"}]")
                .orElseThrow();
        assertEquals(2, o.number());
        assertNull(o.state());
    }

    @Test
    void aRead_isEmpty() {
        assertTrue(reader.read(op(null), "/repos/acme/widgets/issues/2", "{\"number\":2}")
                .isEmpty());
    }
}
