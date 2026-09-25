package com.rbc.fogwall.jetty;

import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;
import static com.rbc.fogwall.servlet.FogwallServlet.PRE_APPROVED_ATTR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.approval.ApprovalGateway;
import com.rbc.fogwall.approval.SelfApprovalPolicy;
import com.rbc.fogwall.approval.SelfApprovalPolicy.Verdict;
import com.rbc.fogwall.config.BinaryBlobConfig;
import com.rbc.fogwall.config.CommitConfig;
import com.rbc.fogwall.config.ContentPatternConfig;
import com.rbc.fogwall.config.DiffScanConfig;
import com.rbc.fogwall.config.FogwallConfig;
import com.rbc.fogwall.config.JettyConfigurationBuilder;
import com.rbc.fogwall.config.ScmOAuthConfig;
import com.rbc.fogwall.config.SecretScanConfig;
import com.rbc.fogwall.db.FetchStore;
import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.PushStoreFactory;
import com.rbc.fogwall.db.UrlRuleRegistry;
import com.rbc.fogwall.db.model.Attestation;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStatus;
import com.rbc.fogwall.git.GitRequestDetails;
import com.rbc.fogwall.git.LocalRepositoryCache;
import com.rbc.fogwall.git.PushContext;
import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.provider.GitHubProvider;
import com.rbc.fogwall.service.PushIdentityResolver;
import com.rbc.fogwall.servlet.filter.AllowApprovedPushFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Both proxy modes must consult the {@link SelfApprovalPolicy} they are assembled with before forwarding an approved
 * push. Each test stubs the policy to refuse and checks that the assembled pipeline refuses too.
 */
class SelfApprovalWiringTest {

    @TempDir
    Path tempDir;

    private final SelfApprovalPolicy policy = mock(SelfApprovalPolicy.class);
    private final GitHubProvider provider = new GitHubProvider("/github");

    @Test
    void serverMode_approvalHookConsultsContextPolicy() throws Exception {
        String recordId = UUID.randomUUID().toString();
        PushRecord approved =
                PushRecord.builder().id(recordId).status(PushStatus.APPROVED).build();
        PushStore pushStore = mock(PushStore.class);
        when(pushStore.findById(recordId)).thenReturn(Optional.of(approved));
        when(policy.evaluate(approved)).thenReturn(Verdict.MISSING_ROLE);

        FogwallContext context = mock(FogwallContext.class);
        when(context.pushStore()).thenReturn(pushStore);
        when(context.approvalGateway()).thenReturn(mock(ApprovalGateway.class));
        when(context.selfApprovalPolicy()).thenReturn(policy);
        var factory = FogwallServletRegistrar.buildReceivePackFactory(
                context, new JettyConfigurationBuilder(new FogwallConfig()), provider);

        PushContext pushContext = new PushContext();
        pushContext.setValidationRecordId(recordId);
        Repository repo = repo();
        ReceiveCommand cmd = updateCommand();
        factory.buildApprovalHook(pushContext).onPreReceive(new ReceivePack(repo), List.of(cmd));

        verify(policy).evaluate(approved);
        assertEquals(ReceiveCommand.Result.REJECTED_OTHER_REASON, cmd.getResult());
    }

    @Test
    void transparentProxy_allowApprovedPushFilterConsultsPolicy() throws Exception {
        PushStore store = PushStoreFactory.h2InMemory("wiring-" + UUID.randomUUID());
        PushRecord approved = PushRecord.builder()
                .commitTo("deadbeef")
                .branch("refs/heads/main")
                .provider(provider.getProviderId())
                .project("owner")
                .repoName("repo")
                .build();
        store.save(approved);
        store.approve(
                approved.getId(),
                Attestation.builder()
                        .pushId(approved.getId())
                        .type(Attestation.Type.APPROVAL)
                        .reviewerUsername("alice")
                        .build());
        when(policy.evaluate(any())).thenReturn(Verdict.MISSING_ROLE);

        AllowApprovedPushFilter filter = FogwallServletRegistrar.buildCoreFilters(
                        provider,
                        mock(LocalRepositoryCache.class),
                        new JettyConfigurationBuilder(new FogwallConfig()),
                        CommitConfig::defaultConfig,
                        DiffScanConfig::defaultConfig,
                        SecretScanConfig::defaultConfig,
                        BinaryBlobConfig::defaultConfig,
                        ContentPatternConfig::defaultConfig,
                        store,
                        "https://fogwall.example.com",
                        mock(ApprovalGateway.class),
                        mock(PushIdentityResolver.class),
                        mock(RepoPermissionService.class),
                        policy,
                        mock(FetchStore.class),
                        mock(UrlRuleRegistry.class),
                        ScmOAuthConfig.defaultConfig())
                .stream()
                .filter(AllowApprovedPushFilter.class::isInstance)
                .map(AllowApprovedPushFilter.class::cast)
                .findFirst()
                .orElseThrow();

        HttpServletRequest req = pushRequest(pushDetails());
        filter.doHttpFilter(req, mock(HttpServletResponse.class));

        verify(policy).evaluate(argThat(r -> approved.getId().equals(r.getId())));
        verify(req, never()).setAttribute(eq(PRE_APPROVED_ATTR), any());
    }

    private Repository repo() throws Exception {
        Git git = Git.init().setDirectory(tempDir.toFile()).call();
        git.getRepository().getConfig().setBoolean("commit", null, "gpgsign", false);
        return git.getRepository();
    }

    private ReceiveCommand updateCommand() throws Exception {
        Git git = Git.open(tempDir.toFile());
        ObjectId first = commit(git, "init");
        ObjectId second = commit(git, "second");
        return new ReceiveCommand(first, second, "refs/heads/main", ReceiveCommand.Type.UPDATE);
    }

    private ObjectId commit(Git git, String message) throws Exception {
        Files.writeString(tempDir.resolve(UUID.randomUUID() + ".txt"), message);
        git.add().addFilepattern(".").call();
        PersonIdent dev = new PersonIdent("Dev", "dev@example.com");
        return git.commit()
                .setAuthor(dev)
                .setCommitter(dev)
                .setMessage(message)
                .setSign(false)
                .call()
                .getId();
    }

    private GitRequestDetails pushDetails() {
        GitRequestDetails details = new GitRequestDetails();
        details.setCommitTo("deadbeef");
        details.setBranch("refs/heads/main");
        details.setProvider(provider);
        details.setRepoRef(GitRequestDetails.RepoRef.builder()
                .owner("owner")
                .name("repo")
                .slug("/owner/repo")
                .build());
        return details;
    }

    private static HttpServletRequest pushRequest(GitRequestDetails details) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getContentType()).thenReturn("application/x-git-receive-pack-request");
        when(req.getRequestURI()).thenReturn("/proxy/github.com/owner/repo.git/git-receive-pack");
        when(req.getAttribute(GIT_REQUEST_ATTR)).thenReturn(details);
        when(req.getHeaderNames()).thenReturn(Collections.emptyEnumeration());
        return req;
    }
}
