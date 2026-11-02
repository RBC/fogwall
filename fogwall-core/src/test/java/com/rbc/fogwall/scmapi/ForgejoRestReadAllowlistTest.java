package com.rbc.fogwall.scmapi;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ForgejoRestReadAllowlistTest {

    /**
     * Every path {@code tea} and {@code fj} requested in live captures of issue and pull request list, view, create.
     */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "/version",
                "/user",
                "/repos/search",
                "/orgs/coopernetes",
                "/repos/coopernetes/test-repo",
                "/repos/coopernetes/test-repo/issues",
                "/repos/coopernetes/test-repo/issues/2",
                "/repos/coopernetes/test-repo/issues/2/comments",
                "/repos/coopernetes/test-repo/issues/2/reactions",
                "/repos/coopernetes/test-repo/pulls",
                "/repos/coopernetes/test-repo/pulls/1",
                "/repos/coopernetes/test-repo/pulls/1.diff",
                "/repos/coopernetes/test-repo/pulls/1/files",
                "/repos/coopernetes/test-repo/pulls/1/commits",
                "/repos/coopernetes/test-repo/pulls/1/reviews",
                "/repos/coopernetes/test-repo/commits/0be72ca0753e07c8e1b4831146d55fbe388b16da/status",
                "/repos/coopernetes/test-repo/labels",
                "/repos/coopernetes/test-repo/milestones",
                "/repos/coopernetes/test-repo/issue_templates",
                "/repos/coopernetes/test-repo/issue_config",
                "/repos/coopernetes/test-repo/compare/main...feature%2Fx",
                "/repos/coopernetes/test-repo/raw/.forgejo%2Fpull_request_template.md"
            })
    void servesTheCapturedReads(String path) {
        assertTrue(ForgejoRestAllowlist.isReadAllowed(path), path);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/repos/coopernetes/test-repo/actions/runs",
                "/repos/coopernetes/test-repo/releases",
                "/repos/coopernetes/test-repo/contents/README.md",
                "/repos/coopernetes/test-repo/issues/abc",
                "/repos/coopernetes/test-repo/pulls/1/merge",
                "/repos/coopernetes/test-repo/raw/docs/README.md",
                "/repos/coopernetes",
                "/admin/users",
                "/user/keys",
                "/user/repos",
                "/"
            })
    void refusesEveryOtherRead(String path) {
        assertFalse(ForgejoRestAllowlist.isReadAllowed(path), path);
    }

    @Test
    void aNullPathIsRefused() {
        assertFalse(ForgejoRestAllowlist.isReadAllowed(null));
    }
}
