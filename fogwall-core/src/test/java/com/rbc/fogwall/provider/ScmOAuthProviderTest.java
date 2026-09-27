package com.rbc.fogwall.provider;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import org.junit.jupiter.api.Test;

class ScmOAuthProviderTest {

    @Test
    void gitHub_needsRepo() {
        var github = new GitHubProvider("/server");

        assertTrue(github.grantsPush("read:user,user:email,repo"));
        assertFalse(github.grantsPush("read:user,user:email,read:public_key"));
        assertFalse(github.grantsPush("public_repo"));
    }

    @Test
    void gitLab_needsWriteRepositoryOrApi() {
        var gitlab = new GitLabProvider("/server");

        assertTrue(gitlab.grantsPush("read_user write_repository"));
        assertTrue(gitlab.grantsPush("api"));
        assertFalse(gitlab.grantsPush("read_user"));
    }

    @Test
    void forgejo_needsWriteRepository() {
        var forgejo = ForgejoProvider.builder()
                .uri(URI.create("https://codeberg.org"))
                .build();

        assertTrue(forgejo.grantsPush("read:user,write:repository"));
        assertFalse(forgejo.grantsPush("read:user"));
    }

    @Test
    void unreportedScopes_areTakenToPermitAPush() {
        // A GitHub App's user tokens carry no scopes: what they can do is set on the app.
        var github = new GitHubProvider("/server");

        assertTrue(github.grantsPush(null));
        assertTrue(github.grantsPush(""));
    }
}
