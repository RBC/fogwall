package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.approval.ApprovalGateway;
import com.rbc.fogwall.approval.SelfApprovalPolicy;
import com.rbc.fogwall.config.CommitConfig;
import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.UrlRuleRegistry;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.provider.GitHubProvider;
import com.rbc.fogwall.service.PushIdentityResolver;
import com.rbc.fogwall.service.ScmOAuthTokenService;
import com.rbc.fogwall.servlet.filter.FogwallCredentialFilter;
import com.rbc.fogwall.user.UserEntry;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceivePack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

/**
 * The push store, approval gateway and self-approval policy are security controls: without the store there is no push
 * record and no approval state, without the gateway nothing gates forwarding, and without the policy a self-approval
 * would forward unchecked. Construction must fail loudly rather than assemble a hook chain that silently skips any of
 * them.
 */
class ServerReceivePackFactoryTest {

    private final GitHubProvider provider = new GitHubProvider("/push");
    private final SelfApprovalPolicy policy = mock(SelfApprovalPolicy.class);

    @Test
    void nullPushStore_refusedAtConstruction() {
        assertThrows(
                NullPointerException.class,
                () -> new ServerReceivePackFactory(
                        provider, CommitConfig.defaultConfig(), null, mock(ApprovalGateway.class), policy));
    }

    @Test
    void nullApprovalGateway_refusedAtConstruction() {
        assertThrows(
                NullPointerException.class,
                () -> new ServerReceivePackFactory(
                        provider, CommitConfig.defaultConfig(), mock(PushStore.class), null, policy));
    }

    @Test
    void bothControlDependenciesPresent_constructs() {
        assertDoesNotThrow(() -> new ServerReceivePackFactory(
                provider, CommitConfig.defaultConfig(), mock(PushStore.class), mock(ApprovalGateway.class), policy));
    }

    @Test
    void nullSelfApprovalPolicy_refusedAtConstruction() {
        assertThrows(
                NullPointerException.class,
                () -> new ServerReceivePackFactory(
                        provider,
                        CommitConfig.defaultConfig(),
                        mock(PushStore.class),
                        mock(ApprovalGateway.class),
                        null));
    }

    // ---- a push authenticated by a fogwall-issued credential ----

    @TempDir
    Path tempDir;

    /**
     * The push context is not reachable from the assembled {@link ReceivePack}, so the pre-receive chain is run over an
     * empty command set and the lifecycle record it persists is read back: the permission hook's step names the
     * credential and records the fogwall user, and the identity resolver, which maps client tokens to users, is never
     * consulted because there is no client token to map.
     */
    @Test
    void credentialAuthenticatedRequest_fixesTheUser_andLeavesNoTokenToResolve() throws Exception {
        Repository db =
                Git.init().setBare(true).setDirectory(tempDir.toFile()).call().getRepository();
        UserEntry alice = UserEntry.builder()
                .username("alice")
                .emails(List.of())
                .scmIdentities(List.of())
                .build();
        var linked = new ScmOAuthCredentialsProvider(mock(ScmOAuthTokenService.class), "alice", "github");
        PushStore pushStore = mock(PushStore.class);
        PushIdentityResolver identityResolver = mock(PushIdentityResolver.class);
        RepoPermissionService permissions = mock(RepoPermissionService.class);
        when(permissions.isAllowedToPush("alice", "github", "/owner/repo")).thenReturn(true);
        var factory = new ServerReceivePackFactory(
                provider,
                CommitConfig::defaultConfig,
                null,
                null,
                null,
                null,
                null,
                permissions,
                identityResolver,
                pushStore,
                mock(ApprovalGateway.class),
                policy,
                null,
                Duration.ofSeconds(10),
                mock(UrlRuleRegistry.class));

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getPathInfo()).thenReturn("/owner/repo.git/git-receive-pack");
        when(req.getHeader("Authorization"))
                .thenReturn("Basic "
                        + Base64.getEncoder()
                                .encodeToString("me:fgw_abcdefghijklmnop_secret".getBytes(StandardCharsets.UTF_8)));
        when(req.getAttribute(FogwallCredentialFilter.AUTHENTICATION_ATTRIBUTE))
                .thenReturn(new CredentialAuthentication(alice, "abcdefghijklmnop", "laptop"));
        when(req.getAttribute(ServerRepositoryResolver.CREDENTIALS_ATTRIBUTE)).thenReturn(linked);
        when(req.getAttribute(ServerRepositoryResolver.UPSTREAM_URL_ATTRIBUTE))
                .thenReturn("https://github.com/owner/repo.git");

        ReceivePack rp = factory.create(req, db);
        rp.getPreReceiveHook().onPreReceive(rp, List.of());

        ArgumentCaptor<PushRecord> record = ArgumentCaptor.forClass(PushRecord.class);
        verify(pushStore).save(record.capture());
        assertEquals("alice", record.getValue().getResolvedUser());
        assertTrue(record.getValue().getSteps().stream()
                .flatMap(step -> step.getLogs().stream())
                .anyMatch(l -> l.equals("Authenticated by fogwall credential 'laptop' (abcdefghijklmnop) of alice")));
        verifyNoInteractions(identityResolver);
    }
}
