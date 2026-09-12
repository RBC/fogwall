package com.rbc.fogwall.scmapi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ScmApiReadResourceTest {

    @Test
    void restPathsClassifyByResource() {
        // Forgejo/Gitea and GitLab share the vocabulary; the resource is read off the path.
        assertEquals("issue.read", ScmApiReadResource.fromRestPath("/repos/acme/widgets/issues/5"));
        assertEquals("comment.read", ScmApiReadResource.fromRestPath("/repos/acme/widgets/issues/5/comments"));
        assertEquals("proposal.read", ScmApiReadResource.fromRestPath("/repos/acme/widgets/pulls/3"));
        assertEquals("proposal.read", ScmApiReadResource.fromRestPath("/projects/acme%2Fwidgets/merge_requests/2"));
        assertEquals(
                "comment.read", ScmApiReadResource.fromRestPath("/projects/acme%2Fwidgets/merge_requests/2/notes"));
    }

    @Test
    void restPathUnknownResourceIsOther() {
        assertEquals("other.read", ScmApiReadResource.fromRestPath("/repos/acme/widgets/contents/README.md"));
        assertEquals("read", ScmApiReadResource.fromRestPath(null));
    }

    @Test
    void githubQueriesClassifyByOperationName() {
        assertEquals("issue.read", ScmApiReadResource.fromGraphQl("IssueByNumber"));
        assertEquals("repository.read", ScmApiReadResource.fromGraphQl("RepositoryInfo"));
        assertEquals("repository.read", ScmApiReadResource.fromGraphQl("IssueRepositoryInfo"));
        assertEquals("proposal.read", ScmApiReadResource.fromGraphQl("PullRequestByNumber"));
        assertEquals("proposal.read", ScmApiReadResource.fromGraphQl("PullRequestForBranch"));
    }

    @Test
    void unknownOrAnonymousGraphqlQueryIsPlainRead() {
        // A gh version change that renames a query falls back rather than mislabeling.
        assertEquals("read", ScmApiReadResource.fromGraphQl("SomeNewQueryGhAdded"));
        assertEquals("read", ScmApiReadResource.fromGraphQl(null));
    }
}
