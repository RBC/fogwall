package com.rbc.fogwall.dashboard.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.provider.ForgejoProvider;
import com.rbc.fogwall.provider.GitHubProvider;
import com.rbc.fogwall.provider.GitLabProvider;
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

    // ---- Link-time scopes ----

    @Test
    void scope_identityOnly_requestsNoWriteAccess() {
        assertEquals(
                "&scope=read:user%20user:email%20read:public_key",
                ScmOAuthLinkController.scopeParam(new GitHubProvider("/proxy"), false, false));
        assertEquals("&scope=read_user", ScmOAuthLinkController.scopeParam(new GitLabProvider("/proxy"), false, false));
        assertEquals("&scope=read:user", ScmOAuthLinkController.scopeParam(codeberg(), false, false));
    }

    @Test
    void scope_brokeredPush_addsRepositoryWrite() {
        assertEquals(
                "&scope=read:user%20user:email%20read:public_key%20repo",
                ScmOAuthLinkController.scopeParam(new GitHubProvider("/proxy"), false, true));
        assertEquals(
                "&scope=read_user%20write_repository",
                ScmOAuthLinkController.scopeParam(new GitLabProvider("/proxy"), false, true));
        assertEquals("&scope=read:user%20write:repository", ScmOAuthLinkController.scopeParam(codeberg(), false, true));
    }

    @Test
    void scope_issuesAndBrokeredPush_requestsBoth() {
        assertEquals(
                "&scope=read:user%20user:email%20read:public_key%20repo",
                ScmOAuthLinkController.scopeParam(new GitHubProvider("/proxy"), true, true));
        assertEquals(
                "&scope=api%20write_repository",
                ScmOAuthLinkController.scopeParam(new GitLabProvider("/proxy"), true, true));
        assertEquals(
                "&scope=read:user%20write:issue%20write:repository",
                ScmOAuthLinkController.scopeParam(codeberg(), true, true));
    }

    private static ForgejoProvider codeberg() {
        return ForgejoProvider.builder()
                .name("codeberg")
                .uri(ForgejoProvider.CODEBERG)
                .build();
    }
}
