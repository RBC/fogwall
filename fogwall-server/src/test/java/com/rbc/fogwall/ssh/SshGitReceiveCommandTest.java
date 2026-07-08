package com.rbc.fogwall.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rbc.fogwall.ssh.SshGitReceiveCommand.RepoRoute;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SshGitReceiveCommandTest {

    private static final URI GITEA_URI = URI.create("ssh://git@localhost:3022");

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void resolvesStandardPath() {
        RepoRoute route = SshGitReceiveCommand.resolveRoute("/localhost:3022/test-owner/test-repo.git", GITEA_URI);
        assertEquals("test-owner", route.owner());
        assertEquals("test-repo", route.repo());
        assertEquals("ssh://git@localhost:3022/test-owner/test-repo.git", route.upstreamUrl());
        assertEquals("/test-owner/test-repo", route.repoSlug());
    }

    @Test
    void acceptsPathWithoutLeadingSlash() {
        RepoRoute route = SshGitReceiveCommand.resolveRoute("localhost:3022/org/repo.git", GITEA_URI);
        assertEquals("org", route.owner());
        assertEquals("repo", route.repo());
    }

    @Test
    void acceptsPathWithoutDotGitSuffix() {
        RepoRoute route = SshGitReceiveCommand.resolveRoute("/localhost:3022/owner/repo", GITEA_URI);
        assertEquals("owner", route.owner());
        assertEquals("repo", route.repo());
    }

    @Test
    void matchesProviderWithNoPort() {
        URI noPortUri = URI.create("ssh://git@github.com");
        RepoRoute route = SshGitReceiveCommand.resolveRoute("/github.com/myorg/myrepo.git", noPortUri);
        assertEquals("myorg", route.owner());
        assertEquals("myrepo", route.repo());
        assertEquals("ssh://git@github.com/myorg/myrepo.git", route.upstreamUrl());
    }

    // ── Path format errors ────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/localhost:3022/only-two-segments", // missing repo
                "/localhost:3022", // only host
                "/justonething", // single segment
                "" // empty
            })
    void rejectsPathWithTooFewSegments(String path) {
        assertThrows(IllegalArgumentException.class, () -> SshGitReceiveCommand.resolveRoute(path, GITEA_URI));
    }

    // ── Provider mismatch ─────────────────────────────────────────────────────

    @Test
    void rejectsWrongHost() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> SshGitReceiveCommand.resolveRoute("/github.com:443/org/repo.git", GITEA_URI));
        assertEquals(true, ex.getMessage().contains("github.com:443"));
        assertEquals(true, ex.getMessage().contains("localhost:3022"));
    }

    @Test
    void rejectsWrongPort() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SshGitReceiveCommand.resolveRoute("/localhost:9999/owner/repo.git", GITEA_URI));
    }

    @Test
    void rejectsRightHostWrongPort() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SshGitReceiveCommand.resolveRoute("/localhost/owner/repo.git", GITEA_URI));
    }
}
