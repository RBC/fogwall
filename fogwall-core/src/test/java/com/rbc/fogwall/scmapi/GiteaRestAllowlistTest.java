package com.rbc.fogwall.scmapi;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Cases derived from the {@code tea} 0.15.1 and {@code fj} v0.6.0 endpoint tables in docs/internals/SCM_API_PROXY.md —
 * one per row, plus the fail-closed rejections.
 */
class GiteaRestAllowlistTest {

    @Test
    void matchesIssueCreate() {
        Optional<ScmApiRestMatch> match = GiteaRestAllowlist.match("POST", "/repos/acme/widgets/issues");
        assertTrue(match.isPresent());
        assertEquals("issues.create", match.get().operation());
        assertEquals("acme", match.get().ownerRepo().owner());
        assertEquals("widgets", match.get().ownerRepo().name());
    }

    @Test
    void matchesIssueUpdate() {
        Optional<ScmApiRestMatch> match = GiteaRestAllowlist.match("PATCH", "/repos/acme/widgets/issues/7");
        assertTrue(match.isPresent());
        assertEquals("issues.update", match.get().operation());
    }

    @Test
    void matchesIssueComment() {
        Optional<ScmApiRestMatch> match = GiteaRestAllowlist.match("POST", "/repos/acme/widgets/issues/7/comments");
        assertTrue(match.isPresent());
        assertEquals("issues.comment", match.get().operation());
    }

    @Test
    void matchesCommentUpdate() {
        Optional<ScmApiRestMatch> match = GiteaRestAllowlist.match("PATCH", "/repos/acme/widgets/issues/comments/42");
        assertTrue(match.isPresent());
        assertEquals("issues.comment.update", match.get().operation());
    }

    @Test
    void matchesPullCreate() {
        Optional<ScmApiRestMatch> match = GiteaRestAllowlist.match("POST", "/repos/acme/widgets/pulls");
        assertTrue(match.isPresent());
        assertEquals("pulls.create", match.get().operation());
    }

    @Test
    void matchesPullUpdate() {
        Optional<ScmApiRestMatch> match = GiteaRestAllowlist.match("PATCH", "/repos/acme/widgets/pulls/3");
        assertTrue(match.isPresent());
        assertEquals("pulls.update", match.get().operation());
    }

    /** Review is out of scope — reviewers use the SCM's own UI. Merge is a maintainer operation tracked separately. */
    @Test
    void deniesReviewAndMerge() {
        assertTrue(GiteaRestAllowlist.match("POST", "/repos/acme/widgets/pulls/3/reviews")
                .isEmpty());
        assertTrue(GiteaRestAllowlist.match("POST", "/repos/acme/widgets/pulls/3/merge")
                .isEmpty());
    }

    /**
     * The divergence that makes a single union allowlist necessary: {@code tea pr close} sends {@code PATCH /pulls/{n}}
     * while {@code fj pr close} sends {@code PATCH /issues/{n}}, against the same server for the same user-facing
     * operation. Allowlisting only one form silently breaks the other CLI.
     */
    @Test
    void bothPrCloseFormsMatch_teaViaPulls_fjViaIssues() {
        assertEquals(
                "pulls.update",
                GiteaRestAllowlist.match("PATCH", "/repos/acme/widgets/pulls/3")
                        .orElseThrow()
                        .operation());
        assertEquals(
                "issues.update",
                GiteaRestAllowlist.match("PATCH", "/repos/acme/widgets/issues/3")
                        .orElseThrow()
                        .operation());
    }

    @Test
    void ownerAndRepoAreUrlDecodedPerSegment() {
        Optional<ScmApiRestMatch> match = GiteaRestAllowlist.match("POST", "/repos/my%20org/my%20repo/issues");
        assertTrue(match.isPresent());
        assertEquals("my org", match.get().ownerRepo().owner());
        assertEquals("my repo", match.get().ownerRepo().name());
    }

    @Test
    void readsAreNotMatched_gatedSeparatelyAsProviderLevelReads() {
        assertTrue(GiteaRestAllowlist.match("GET", "/repos/acme/widgets/issues").isEmpty());
    }

    @Test
    void unlistedMutationIsDenied() {
        assertTrue(GiteaRestAllowlist.match("DELETE", "/repos/acme/widgets/issues/7")
                .isEmpty());
        assertTrue(GiteaRestAllowlist.match("POST", "/repos/acme/widgets/issues/7/labels")
                .isEmpty());
        assertTrue(
                GiteaRestAllowlist.match("POST", "/repos/acme/widgets/releases").isEmpty());
    }

    @Test
    void nonRepoScopedPathIsDenied() {
        // `tea` fires GET /api/v1/version alongside repo traffic; nothing non-repo-scoped may ever mutate.
        assertTrue(GiteaRestAllowlist.match("POST", "/version").isEmpty());
        assertTrue(GiteaRestAllowlist.match("POST", "/user/repos").isEmpty());
    }

    @Test
    void extraPathSegmentsAreDenied() {
        assertTrue(GiteaRestAllowlist.match("POST", "/repos/acme/widgets/issues/7/comments/extra")
                .isEmpty());
    }

    @Test
    void nonNumericIndexIsDenied() {
        assertTrue(GiteaRestAllowlist.match("PATCH", "/repos/acme/widgets/issues/notanumber")
                .isEmpty());
    }

    @Test
    void nullInputsAreDenied() {
        assertTrue(GiteaRestAllowlist.match(null, "/repos/acme/widgets/issues").isEmpty());
        assertTrue(GiteaRestAllowlist.match("POST", null).isEmpty());
    }
}
