package com.rbc.fogwall.dashboard.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rbc.fogwall.config.FogwallConfig;
import com.rbc.fogwall.config.ProviderConfig;
import com.rbc.fogwall.dashboard.audit.AdminAuditLog;
import com.rbc.fogwall.provider.ForgejoProvider;
import com.rbc.fogwall.provider.ProviderRegistry;
import com.rbc.fogwall.service.GitCredentialService;
import com.rbc.fogwall.user.GitCredential;
import com.rbc.fogwall.user.GitCredentialNameConflictException;
import com.rbc.fogwall.user.ScmOAuthToken;
import com.rbc.fogwall.user.ScmOAuthTokenStore;
import com.rbc.fogwall.user.UserStore;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class GitCredentialControllerTest {

    private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");
    private static final GitCredential LAPTOP =
            new GitCredential("abcdefghijklmnop", "alice", "laptop", "{sha256}hash", NOW, null, null);

    @Mock
    GitCredentialService credentials;

    @Mock
    UserStore userStore;

    @Mock
    ScmOAuthTokenStore scmOAuthTokens;

    @Mock
    ProviderRegistry providers;

    @Mock
    AdminAuditLog auditLog;

    private final FogwallConfig config = new FogwallConfig();
    private GitCredentialController controller;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("alice", null, List.of()));
        controller = new GitCredentialController(credentials, userStore, scmOAuthTokens, providers, config, auditLog);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void brokeredProvider() {
        ProviderConfig gitea = new ProviderConfig();
        gitea.getOauth().setEnabled(true);
        gitea.getOauth().setClientId("client");
        gitea.getOauth().setBrokeredPush(true);
        config.setProviders(Map.of("gitea", gitea));
    }

    private static ScmOAuthToken token(String scopes) {
        return new ScmOAuthToken(new byte[] {1}, null, scopes, null);
    }

    /**
     * A brokering provider on which alice has linked an account that can push: the precondition for holding a
     * credential.
     */
    private void linkedBrokeredProvider() {
        brokeredProvider();
        when(scmOAuthTokens.findToken("alice", "gitea")).thenReturn(Optional.of(token("read:user,write:repository")));
    }

    @Test
    void issue_returnsTheValueOnce_andMaterialisesTheUser() {
        linkedBrokeredProvider();
        when(credentials.issue("alice", "laptop"))
                .thenReturn(new GitCredentialService.Issued(LAPTOP, "fgw_abcdefghijklmnop_secret"));

        var response = controller.issue(new GitCredentialController.IssueRequest("laptop"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        var body = (GitCredentialController.IssuedView) response.getBody();
        assertEquals("fgw_abcdefghijklmnop_secret", body.value());
        assertEquals("laptop", body.credential().name());
        verify(userStore).upsertUser("alice");
        verify(auditLog).success("git-credential.issue", "user:alice", "credential=abcdefghijklmnop");
    }

    @Test
    void issue_withoutABrokeringProvider_isRefused() {
        var response = controller.issue(new GitCredentialController.IssueRequest("laptop"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verify(credentials, never()).issue(anyString(), anyString());
    }

    @Test
    void issue_onAProviderWithBrokeringButLinkingOff_isRefused() {
        ProviderConfig gitea = new ProviderConfig();
        gitea.getOauth().setBrokeredPush(true);
        config.setProviders(Map.of("gitea", gitea));

        var response = controller.issue(new GitCredentialController.IssueRequest("laptop"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    void issue_withoutALinkedAccount_isRefused_andAudited() {
        // A credential is useless without a linked token to push with, so none is handed out before the link exists.
        brokeredProvider();
        when(scmOAuthTokens.findLinkedProviders("alice")).thenReturn(List.of("github"));

        var response = controller.issue(new GitCredentialController.IssueRequest("laptop"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(
                response.getBody().toString().contains("Link your account for gitea"),
                response.getBody().toString());
        verify(credentials, never()).issue(anyString(), anyString());
        verify(userStore, never()).upsertUser(anyString());
        verify(auditLog).denied("git-credential.issue", "user:alice", "no linked account");
    }

    @Test
    void rotate_withoutALinkedAccount_isRefused() {
        brokeredProvider();
        when(scmOAuthTokens.findLinkedProviders("alice")).thenReturn(List.of());

        var response = controller.rotate("abcdefghijklmnop");

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verify(credentials, never()).rotate(anyString(), anyString());
        verify(auditLog).denied("git-credential.rotate", "user:alice", "no linked account");
    }

    @Test
    void providersMine_areTheBrokeringProvidersTheUserLinked() {
        ProviderConfig gitlab = new ProviderConfig();
        gitlab.getOauth().setEnabled(true);
        gitlab.getOauth().setBrokeredPush(true);
        ProviderConfig codeberg = new ProviderConfig();
        codeberg.getOauth().setEnabled(true);
        config.setProviders(Map.of("gitlab", gitlab, "codeberg", codeberg));
        when(scmOAuthTokens.findToken("alice", "gitlab")).thenReturn(Optional.of(token(null)));

        assertEquals(List.of("gitlab"), controller.providersMine());
    }

    @Test
    void issue_withALinkedAccountThatCannotPush_isRefusedWithTheRemedy() {
        // Linked before the provider brokered pushes, so the token was granted no scope that can push.
        brokeredProvider();
        when(providers.getProvider("gitea"))
                .thenReturn(Optional.of(ForgejoProvider.builder()
                        .name("gitea")
                        .uri(URI.create("https://gitea.example.com"))
                        .build()));
        when(scmOAuthTokens.findToken("alice", "gitea")).thenReturn(Optional.of(token("read:user")));
        when(scmOAuthTokens.findLinkedProviders("alice")).thenReturn(List.of("gitea"));

        var response = controller.issue(new GitCredentialController.IssueRequest("laptop"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(
                response.getBody().toString().contains("without permission to push"),
                response.getBody().toString());
        verify(credentials, never()).issue(anyString(), anyString());
        verify(auditLog).denied("git-credential.issue", "user:alice", "linked account cannot push");
        assertEquals(List.of(), controller.providersMine());
    }

    @Test
    void issue_invalidName_is400() {
        linkedBrokeredProvider();
        when(credentials.issue("alice", " ")).thenThrow(new IllegalArgumentException("A credential needs a name"));

        var response = controller.issue(new GitCredentialController.IssueRequest(" "));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void issue_duplicateName_is409() {
        linkedBrokeredProvider();
        when(credentials.issue("alice", "laptop")).thenThrow(new GitCredentialNameConflictException("alice", "laptop"));

        var response = controller.issue(new GitCredentialController.IssueRequest("laptop"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    void listMine_neverExposesTheHash() {
        when(credentials.list("alice")).thenReturn(List.of(LAPTOP));

        List<GitCredentialController.CredentialView> views = controller.listMine();

        assertEquals(1, views.size());
        assertEquals("laptop", views.get(0).name());
        assertFalse(views.get(0).toString().contains("hash"));
    }

    @Test
    void listForUser_reportsTheEffectiveExpiry() {
        // LAPTOP was issued with no expiry; a lifetime limit configured since has already retired it.
        Instant retired = NOW.plusSeconds(60);
        when(credentials.list("bob")).thenReturn(List.of(LAPTOP));
        when(credentials.effectiveExpiry(LAPTOP)).thenReturn(retired);
        when(credentials.isExpired(LAPTOP)).thenReturn(true);

        var view = controller.listForUser("bob").get(0);

        assertEquals(retired, view.expiresAt());
        assertTrue(view.expired());
    }

    @Test
    void rotate_returnsTheNewValue() {
        linkedBrokeredProvider();
        when(credentials.rotate("alice", "abcdefghijklmnop"))
                .thenReturn(Optional.of(new GitCredentialService.Issued(LAPTOP, "fgw_abcdefghijklmnop_new")));

        var response = controller.rotate("abcdefghijklmnop");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("fgw_abcdefghijklmnop_new", ((GitCredentialController.IssuedView) response.getBody()).value());
    }

    @Test
    void rotate_someoneElsesCredential_is404() {
        linkedBrokeredProvider();
        when(credentials.rotate("alice", "zzzzzzzzzzzzzzzz")).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, controller.rotate("zzzzzzzzzzzzzzzz").getStatusCode());
    }

    @Test
    void revokeMine_revokesOnlyTheCurrentUsersCredential() {
        when(credentials.revoke("alice", "abcdefghijklmnop")).thenReturn(true);

        assertEquals(
                HttpStatus.NO_CONTENT, controller.revokeMine("abcdefghijklmnop").getStatusCode());
        verify(auditLog).success("git-credential.revoke", "user:alice", "credential=abcdefghijklmnop");
    }

    @Test
    void revokeMine_unknown_is404() {
        when(credentials.revoke("alice", "zzzzzzzzzzzzzzzz")).thenReturn(false);

        assertEquals(
                HttpStatus.NOT_FOUND, controller.revokeMine("zzzzzzzzzzzzzzzz").getStatusCode());
        verify(auditLog, never()).success(any(), any(), any());
    }

    @Test
    void revokeForUser_revokesTheNamedUsersCredential() {
        when(credentials.revoke("bob", "abcdefghijklmnop")).thenReturn(true);

        assertEquals(
                HttpStatus.NO_CONTENT,
                controller.revokeForUser("bob", "abcdefghijklmnop").getStatusCode());
        verify(auditLog).success("git-credential.revoke", "user:bob", "credential=abcdefghijklmnop");
    }
}
