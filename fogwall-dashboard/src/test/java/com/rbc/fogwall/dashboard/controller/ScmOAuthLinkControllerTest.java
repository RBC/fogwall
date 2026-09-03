package com.rbc.fogwall.dashboard.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.ssh.SshKeyUtils;
import com.rbc.fogwall.user.UserStore;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Covers the email-verification filtering logic used when an SCM OAuth callback locks in provider-verified emails (#40)
 * — see {@code ScmOAuthLinkController.lockProviderVerifiedEmails}. Deserializes real GitHub {@code GET /user/emails}
 * response shapes rather than mocking HTTP, so a field-name drift in the response format would fail this test too.
 */
class ScmOAuthLinkControllerTest {

    @Test
    void verifiedGitHubEmails_includesOnlyVerifiedEntries() {
        String json = """
                [
                    {"email": "verified@example.com", "verified": true, "primary": true},
                    {"email": "unverified@example.com", "verified": false, "primary": false}
                ]
                """;

        var entries = new JsonMapper().readValue(json, ScmOAuthLinkController.GitHubEmailEntry[].class);
        List<String> verified = ScmOAuthLinkController.verifiedGitHubEmails(entries);

        assertEquals(List.of("verified@example.com"), verified);
    }

    @Test
    void verifiedGitHubEmails_emptyWhenNoneVerified() {
        String json = """
                [
                    {"email": "unverified@example.com", "verified": false, "primary": true}
                ]
                """;

        var entries = new JsonMapper().readValue(json, ScmOAuthLinkController.GitHubEmailEntry[].class);
        List<String> verified = ScmOAuthLinkController.verifiedGitHubEmails(entries);

        assertTrue(verified.isEmpty());
    }

    @Test
    void verifiedGitHubEmails_includesMultipleVerifiedEntries() {
        String json = """
                [
                    {"email": "primary@example.com", "verified": true, "primary": true},
                    {"email": "secondary@example.com", "verified": true, "primary": false},
                    {"email": "unverified@example.com", "verified": false, "primary": false}
                ]
                """;

        var entries = new JsonMapper().readValue(json, ScmOAuthLinkController.GitHubEmailEntry[].class);
        List<String> verified = ScmOAuthLinkController.verifiedGitHubEmails(entries);

        assertEquals(List.of("primary@example.com", "secondary@example.com"), verified);
    }

    @Test
    void verifiedForgejoEmails_includesOnlyVerifiedEntries() {
        String json = """
                [
                    {"email": "verified@example.com", "verified": true, "primary": true},
                    {"email": "unverified@example.com", "verified": false, "primary": false}
                ]
                """;

        var entries = new JsonMapper().readValue(json, ScmOAuthLinkController.ForgejoEmailEntry[].class);
        List<String> verified = ScmOAuthLinkController.verifiedForgejoEmails(entries);

        assertEquals(List.of("verified@example.com"), verified);
    }

    private static final String SAMPLE_KEY =
            "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIIQiTzhWg82OVGUGpUMctA7FoBSZteJQ5R/TPaVfCC95";

    @Test
    void importOAuthSshKeys_newKey_addsLockedKeyWithTitleAsLabel() {
        UserStore mutable = mock(UserStore.class);
        var keys = List.of(new ScmOAuthLinkController.OAuthSshKeyEntry(SAMPLE_KEY, "work laptop"));
        String fingerprint = SshKeyUtils.fingerprint(SAMPLE_KEY);

        ScmOAuthLinkController.importOAuthSshKeys(mutable, "alice", "github", keys);

        verify(mutable).addSshKey("alice", fingerprint, SAMPLE_KEY, "work laptop", true, "github");
    }

    @Test
    void importOAuthSshKeys_blankTitle_fallsBackToDefaultLabel() {
        UserStore mutable = mock(UserStore.class);
        var keys = List.of(new ScmOAuthLinkController.OAuthSshKeyEntry(SAMPLE_KEY, ""));

        ScmOAuthLinkController.importOAuthSshKeys(mutable, "alice", "github", keys);

        verify(mutable)
                .addSshKey(eq("alice"), any(), eq(SAMPLE_KEY), eq("Imported from github"), eq(true), eq("github"));
    }

    @Test
    void importOAuthSshKeys_alreadyRegisteredFingerprint_stillCallsAddSshKey_soASecondProviderCanBeRecordedAsASource() {
        // #40: a key can legitimately be verified by more than one linked provider. addSshKey itself (not this
        // caller) decides whether that's a genuine no-op or records an additional source — so the caller must
        // never pre-filter fingerprints the user already has, or a second provider's source is silently dropped.
        UserStore mutable = mock(UserStore.class);
        var keys = List.of(new ScmOAuthLinkController.OAuthSshKeyEntry(SAMPLE_KEY, "work laptop"));
        String fingerprint = SshKeyUtils.fingerprint(SAMPLE_KEY);

        ScmOAuthLinkController.importOAuthSshKeys(mutable, "alice", "gitlab", keys);

        verify(mutable).addSshKey("alice", fingerprint, SAMPLE_KEY, "work laptop", true, "gitlab");
    }
}
