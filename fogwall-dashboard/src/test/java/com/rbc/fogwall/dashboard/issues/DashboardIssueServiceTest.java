package com.rbc.fogwall.dashboard.issues;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rbc.fogwall.config.FogwallConfig;
import com.rbc.fogwall.config.ProviderConfig;
import com.rbc.fogwall.crypto.TokenCipher;
import com.rbc.fogwall.crypto.TokenCipherProvider;
import com.rbc.fogwall.db.ScmApiActionStore;
import com.rbc.fogwall.db.ScmApiProposalStore;
import com.rbc.fogwall.db.model.ScmApiActionRecord;
import com.rbc.fogwall.db.model.ScmApiActionStatus;
import com.rbc.fogwall.db.model.ScmApiProposalRecord;
import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.provider.GitHubProvider;
import com.rbc.fogwall.provider.ProviderRegistry;
import com.rbc.fogwall.scmapi.ScmContentInspector;
import com.rbc.fogwall.user.ScmOAuthTokenStore;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardIssueServiceTest {

    @Mock
    ProviderRegistry providers;

    @Mock
    RepoPermissionService permissions;

    @Mock
    ScmContentInspector contentInspector;

    @Mock
    ScmOAuthTokenStore tokenStore;

    @Mock
    TokenCipherProvider cipherProvider;

    @Mock
    TokenCipher cipher;

    @Mock
    ScmApiActionStore auditStore;

    @Mock
    ScmApiProposalStore proposalStore;

    @Mock
    FogwallConfig fogwallConfig;

    @Mock
    DashboardIssueClient client;

    private final GitHubProvider github = new GitHubProvider("/proxy");

    private DashboardIssueService service;

    @BeforeEach
    void setUp() {
        service = new DashboardIssueService(
                providers,
                permissions,
                contentInspector,
                Optional.of(tokenStore),
                cipherProvider,
                auditStore,
                proposalStore,
                fogwallConfig,
                client);
    }

    private void providerEnabled() {
        ProviderConfig config = new ProviderConfig();
        config.setIssuesEnabled(true);
        when(providers.getProviders()).thenReturn(List.of(github));
        when(fogwallConfig.getProviders()).thenReturn(Map.of("github", config));
    }

    private void tokenAvailable() {
        when(tokenStore.findAccessToken("alice", "github")).thenReturn(Optional.of(new byte[] {1, 2, 3}));
        when(cipherProvider.cipher()).thenReturn(Optional.of(cipher));
        when(cipher.decrypt(any())).thenReturn("gho_token".getBytes(StandardCharsets.UTF_8));
    }

    private ScmApiActionStatus savedStatus() {
        ArgumentCaptor<ScmApiActionRecord> captor = ArgumentCaptor.forClass(ScmApiActionRecord.class);
        verify(auditStore).save(captor.capture());
        return captor.getValue().getStatus();
    }

    @Test
    void create_success_returns201_andAuditsForwarded() {
        providerEnabled();
        when(permissions.isAllowedToFileIssue("alice", "github", "/octocat/hello"))
                .thenReturn(true);
        when(contentInspector.inspect(anyList(), any())).thenReturn(List.of());
        tokenAvailable();
        when(client.createIssue(github, "octocat", "hello", "Bug", "It broke", "gho_token"))
                .thenReturn(new DashboardIssueClient.IssueResult(42, "https://github.com/octocat/hello/issues/42"));

        var outcome = service.createIssue("alice", "github", "octocat", "hello", "Bug", "It broke");

        assertTrue(outcome.ok());
        assertEquals(201, outcome.httpStatus());
        assertEquals(42, outcome.result().number());
        assertEquals(ScmApiActionStatus.FORWARDED, savedStatus());
        // A created issue is recorded in the proposal registry (the current-state index the Proposals view reads).
        verify(proposalStore).save(any());
    }

    @Test
    void close_flipsRegistryStateToClosed_andAuditsForwarded() {
        providerEnabled();
        when(permissions.isAllowedToFileIssue("alice", "github", "/octocat/hello"))
                .thenReturn(true);
        when(contentInspector.inspect(anyList(), any())).thenReturn(List.of());
        tokenAvailable();
        when(client.setState(github, "octocat", "hello", 42, true, "gho_token"))
                .thenReturn(new DashboardIssueClient.IssueResult(42, "https://github.com/octocat/hello/issues/42"));
        ScmApiProposalRecord existing = ScmApiProposalRecord.builder()
                .provider("github")
                .repoOwner("octocat")
                .repoName("hello")
                .kind(ScmApiProposalRecord.Kind.ISSUE)
                .number(42)
                .state(ScmApiProposalRecord.State.OPEN)
                .build();
        when(proposalStore.findByTarget("github", "octocat", "hello", ScmApiProposalRecord.Kind.ISSUE, 42))
                .thenReturn(Optional.of(existing));

        var outcome = service.setState("alice", "github", "octocat", "hello", 42, true);

        assertTrue(outcome.ok());
        assertEquals(200, outcome.httpStatus());
        assertEquals(ScmApiActionStatus.FORWARDED, savedStatus());
        ArgumentCaptor<ScmApiProposalRecord> captor = ArgumentCaptor.forClass(ScmApiProposalRecord.class);
        verify(proposalStore).update(captor.capture());
        assertEquals(ScmApiProposalRecord.State.CLOSED, captor.getValue().getState());
    }

    @Test
    void create_withoutGrant_isForbidden_andNotForwarded() {
        providerEnabled();
        when(permissions.isAllowedToFileIssue("alice", "github", "/octocat/hello"))
                .thenReturn(false);

        var outcome = service.createIssue("alice", "github", "octocat", "hello", "Bug", "It broke");

        assertFalse(outcome.ok());
        assertEquals(403, outcome.httpStatus());
        verify(client, never()).createIssue(any(), anyString(), anyString(), anyString(), anyString(), anyString());
        assertEquals(ScmApiActionStatus.DENIED, savedStatus());
    }

    @Test
    void create_contentRejected_is422_withViolations_andNotForwarded() {
        providerEnabled();
        when(permissions.isAllowedToFileIssue("alice", "github", "/octocat/hello"))
                .thenReturn(true);
        when(contentInspector.inspect(anyList(), any())).thenReturn(List.of("secret detected in proposal content"));

        var outcome = service.createIssue("alice", "github", "octocat", "hello", "Bug", "token=abcd");

        assertFalse(outcome.ok());
        assertEquals(422, outcome.httpStatus());
        assertEquals(List.of("secret detected in proposal content"), outcome.violations());
        verify(client, never()).createIssue(any(), anyString(), anyString(), anyString(), anyString(), anyString());
        assertEquals(ScmApiActionStatus.REJECTED, savedStatus());
    }

    @Test
    void create_withoutLinkedToken_is409_andAuditsDenied() {
        providerEnabled();
        when(permissions.isAllowedToFileIssue("alice", "github", "/octocat/hello"))
                .thenReturn(true);
        when(contentInspector.inspect(anyList(), any())).thenReturn(List.of());
        when(tokenStore.findAccessToken("alice", "github")).thenReturn(Optional.empty());
        when(cipherProvider.cipher()).thenReturn(Optional.of(cipher));

        var outcome = service.createIssue("alice", "github", "octocat", "hello", "Bug", "It broke");

        assertFalse(outcome.ok());
        assertEquals(409, outcome.httpStatus());
        assertEquals(ScmApiActionStatus.DENIED, savedStatus());
    }

    @Test
    void create_upstreamError_is502_andAuditsError() {
        providerEnabled();
        when(permissions.isAllowedToFileIssue("alice", "github", "/octocat/hello"))
                .thenReturn(true);
        when(contentInspector.inspect(anyList(), any())).thenReturn(List.of());
        tokenAvailable();
        when(client.createIssue(any(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new DashboardIssueClient.IssueApiException(403, "the provider returned HTTP 403"));

        var outcome = service.createIssue("alice", "github", "octocat", "hello", "Bug", "It broke");

        assertFalse(outcome.ok());
        assertEquals(502, outcome.httpStatus());
        assertEquals(ScmApiActionStatus.ERROR, savedStatus());
    }

    @Test
    void create_forDisabledProvider_isForbidden() {
        ProviderConfig config = new ProviderConfig(); // issues disabled by default
        when(providers.getProviders()).thenReturn(List.of(github));
        when(fogwallConfig.getProviders()).thenReturn(Map.of("github", config));

        var outcome = service.createIssue("alice", "github", "octocat", "hello", "Bug", "It broke");

        assertFalse(outcome.ok());
        assertEquals(403, outcome.httpStatus());
    }

    @Test
    void eligibleProviders_intersectsLinkedAndEnabled() {
        ProviderConfig enabled = new ProviderConfig();
        enabled.setIssuesEnabled(true);
        when(tokenStore.findLinkedProviders("alice")).thenReturn(List.of("github", "gitlab"));
        when(providers.getProviders()).thenReturn(List.of(github));
        when(fogwallConfig.getProviders()).thenReturn(Map.of("github", enabled));

        // gitlab is linked but not issue-enabled (and not a registered provider here); github is both.
        assertEquals(List.of("github"), service.eligibleProviders("alice"));
    }
}
