package com.rbc.fogwall.scmapi;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GitLabRestReadAllowlistTest {

    /** Every path {@code glab} requested in live captures of issue and merge request list, view and create. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "/user",
                "/users",
                "/projects/coopernetes%2Ftest-repo-gitlab",
                "/projects/53539888",
                "/projects/coopernetes%2Ftest-repo-gitlab/issues",
                "/projects/coopernetes%2Ftest-repo-gitlab/issues/4",
                "/projects/coopernetes%2Ftest-repo-gitlab/issues/4/notes",
                "/projects/coopernetes%2Ftest-repo-gitlab/merge_requests",
                "/projects/coopernetes%2Ftest-repo-gitlab/merge_requests/15",
                "/projects/coopernetes%2Ftest-repo-gitlab/merge_requests/15/notes",
                "/projects/coopernetes%2Ftest-repo-gitlab/merge_requests/15/versions",
                "/projects/coopernetes%2Ftest-repo-gitlab/merge_requests/15/approval_state",
                "/projects/coopernetes%2Ftest-repo-gitlab/merge_requests/15/discussions",
                "/projects/coopernetes%2Ftest-repo-gitlab/merge_requests/15/versions/2021515614",
                "/projects/coopernetes%2Ftest-repo-gitlab/labels",
                "/projects/coopernetes%2Ftest-repo-gitlab/milestones"
            })
    void servesTheCapturedReads(String path) {
        assertTrue(GitLabRestAllowlist.isReadAllowed(path), path);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/projects/coopernetes%2Ftest-repo-gitlab/pipelines",
                "/projects/coopernetes%2Ftest-repo-gitlab/jobs/1/trace",
                "/projects/coopernetes%2Ftest-repo-gitlab/repository/files/README.md",
                "/projects/coopernetes%2Ftest-repo-gitlab/issues/abc",
                "/projects/coopernetes%2Ftest-repo-gitlab/issues/4/notes/extra",
                "/projects/a/b/issues",
                "/groups/coopernetes",
                "/users/1/keys",
                "/version",
                "/"
            })
    void refusesEveryOtherRead(String path) {
        assertFalse(GitLabRestAllowlist.isReadAllowed(path), path);
    }

    @Test
    void aNullPathIsRefused() {
        assertFalse(GitLabRestAllowlist.isReadAllowed(null));
    }
}
