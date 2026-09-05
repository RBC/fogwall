package com.rbc.fogwall.servlet;

import static com.rbc.fogwall.servlet.ScmApiRestPathPolicy.EncodedSeparators.GITLAB_PROJECT_SEGMENT;
import static com.rbc.fogwall.servlet.ScmApiRestPathPolicy.EncodedSeparators.REJECTED;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ScmApiRestPathPolicyTest {

    @Test
    void acceptsOrdinaryPathsOnBothDialects() {
        assertTrue(ScmApiRestPathPolicy.isForwardable("/projects/acme%2Fwidgets/issues", GITLAB_PROJECT_SEGMENT));
        assertTrue(ScmApiRestPathPolicy.isForwardable("/projects/1234/merge_requests", GITLAB_PROJECT_SEGMENT));
        assertTrue(ScmApiRestPathPolicy.isForwardable("/repos/acme/widgets/issues", REJECTED));
        assertTrue(ScmApiRestPathPolicy.isForwardable("", REJECTED));
    }

    /** GitLab nests groups, so the project segment legitimately carries more than one encoded separator. */
    @Test
    void acceptsNestedGroupsInTheProjectSegment() {
        assertTrue(ScmApiRestPathPolicy.isForwardable(
                "/projects/group%2Fsubgroup%2Fwidgets/issues", GITLAB_PROJECT_SEGMENT));
    }

    @Test
    void confinesTheEncodedSeparatorToTheProjectSegment() {
        // Right shape, wrong segment: anywhere but index 1 has no dialect justification.
        assertFalse(ScmApiRestPathPolicy.isForwardable("/projects/acme/issues%2Fevil", GITLAB_PROJECT_SEGMENT));
        assertFalse(ScmApiRestPathPolicy.isForwardable("/groups/acme%2Fwidgets/issues", GITLAB_PROJECT_SEGMENT));
        assertFalse(ScmApiRestPathPolicy.isForwardable("/acme%2Fwidgets", GITLAB_PROJECT_SEGMENT));
    }

    @Test
    void rejectsEncodedSeparatorsEntirelyForGitea() {
        assertFalse(ScmApiRestPathPolicy.isForwardable("/repos/acme%2Fwidgets/issues", REJECTED));
        assertFalse(ScmApiRestPathPolicy.isForwardable("/projects/acme%2Fwidgets/issues", REJECTED));
    }

    @Test
    void rejectsEncodedBackslashAndMixedCase() {
        assertFalse(ScmApiRestPathPolicy.isForwardable("/repos/acme%5Cwidgets/issues", REJECTED));
        assertFalse(ScmApiRestPathPolicy.isForwardable("/projects/acme/issues%2fevil", GITLAB_PROJECT_SEGMENT));
        assertFalse(ScmApiRestPathPolicy.isForwardable("/projects/acme%5Cwidgets/x", GITLAB_PROJECT_SEGMENT));
    }

    /**
     * A traversal would let the URL the client library finally resolves differ from the one the allowlist matched and
     * the audit record names — so it is refused whatever the dialect, and in encoded form too.
     */
    @Test
    void rejectsTraversalSegments() {
        assertFalse(ScmApiRestPathPolicy.isForwardable("/projects/../../evil", GITLAB_PROJECT_SEGMENT));
        assertFalse(ScmApiRestPathPolicy.isForwardable("/repos/acme/../widgets", REJECTED));
        assertFalse(ScmApiRestPathPolicy.isForwardable("/projects/%2e%2e/evil", GITLAB_PROJECT_SEGMENT));
        assertFalse(ScmApiRestPathPolicy.isForwardable("/repos/%2E%2E/evil", REJECTED));
        assertFalse(ScmApiRestPathPolicy.isForwardable("/repos/./widgets", REJECTED));
    }

    @Test
    void rejectsNullAndRelativePaths() {
        assertFalse(ScmApiRestPathPolicy.isForwardable(null, REJECTED));
        assertFalse(ScmApiRestPathPolicy.isForwardable("projects/acme", GITLAB_PROJECT_SEGMENT));
    }
}
