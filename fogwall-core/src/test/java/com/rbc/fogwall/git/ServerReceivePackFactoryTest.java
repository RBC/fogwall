package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.approval.ApprovalGateway;
import com.rbc.fogwall.approval.SelfApprovalPolicy;
import com.rbc.fogwall.config.BinaryBlobConfig;
import com.rbc.fogwall.config.CommitConfig;
import com.rbc.fogwall.config.ContentPatternConfig;
import com.rbc.fogwall.config.DiffScanConfig;
import com.rbc.fogwall.config.GpgConfig;
import com.rbc.fogwall.config.SecretScanConfig;
import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.UrlRuleRegistry;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStep;
import com.rbc.fogwall.db.model.StepStatus;
import com.rbc.fogwall.observability.FogwallTelemetry;
import com.rbc.fogwall.observability.InMemoryTelemetry;
import com.rbc.fogwall.observability.StageOutcome;
import com.rbc.fogwall.observability.StageTimer;
import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.provider.GitHubProvider;
import com.rbc.fogwall.service.PushIdentityResolver;
import com.rbc.fogwall.service.ScmOAuthTokenService;
import com.rbc.fogwall.servlet.filter.FogwallCredentialFilter;
import com.rbc.fogwall.user.UserEntry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceivePack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

/**
 * The identity resolver, push store, approval gateway and self-approval policy are security controls: without the
 * resolver no push is matched to a user, without the store there is no push record and no approval state, without the
 * gateway nothing gates forwarding, and without the policy a self-approval would forward unchecked. Construction must
 * fail loudly rather than assemble a hook chain that silently skips any of them.
 */
class ServerReceivePackFactoryTest {

    private final GitHubProvider provider = new GitHubProvider("/push");
    private final SelfApprovalPolicy policy = mock(SelfApprovalPolicy.class);
    private final PushIdentityResolver resolver = mock(PushIdentityResolver.class);

    @Test
    void nullIdentityResolver_refusedAtConstruction() {
        var ex = assertThrows(
                NullPointerException.class,
                () -> new ServerReceivePackFactory(
                        provider,
                        CommitConfig.defaultConfig(),
                        null,
                        mock(PushStore.class),
                        mock(ApprovalGateway.class),
                        policy));
        assertTrue(ex.getMessage().startsWith("pushIdentityResolver"), ex.getMessage());
    }

    @Test
    void nullPushStore_refusedAtConstruction() {
        assertThrows(
                NullPointerException.class,
                () -> new ServerReceivePackFactory(
                        provider, CommitConfig.defaultConfig(), resolver, null, mock(ApprovalGateway.class), policy));
    }

    @Test
    void nullApprovalGateway_refusedAtConstruction() {
        assertThrows(
                NullPointerException.class,
                () -> new ServerReceivePackFactory(
                        provider, CommitConfig.defaultConfig(), resolver, mock(PushStore.class), null, policy));
    }

    @Test
    void bothControlDependenciesPresent_constructs() {
        assertDoesNotThrow(() -> new ServerReceivePackFactory(
                provider,
                CommitConfig.defaultConfig(),
                resolver,
                mock(PushStore.class),
                mock(ApprovalGateway.class),
                policy));
    }

    @Test
    void nullSelfApprovalPolicy_refusedAtConstruction() {
        assertThrows(
                NullPointerException.class,
                () -> new ServerReceivePackFactory(
                        provider,
                        CommitConfig.defaultConfig(),
                        resolver,
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

    // ---- checks turned off in config ----

    private static final Set<String> SWITCHABLE_STEPS = Set.of(
            PushStepKind.BINARY_BLOB.key(),
            PushStepKind.DIFF_SCAN.key(),
            PushStepKind.GPG_SIGNATURE.key(),
            PushStepKind.SECRET_SCAN.key(),
            PushStepKind.CONTENT_PATTERN_MESSAGE.key(),
            PushStepKind.CONTENT_PATTERN_DIFF.key());

    /**
     * Runs one push's pre-receive chain over an empty command set, as the credential test above does, and returns the
     * step names on the record it persists. The factory is reused across calls, so each call is a new push reading the
     * suppliers' current values.
     */
    private Set<String> recordedStepNames(ServerReceivePackFactory factory, PushStore pushStore, Repository db)
            throws Exception {
        UserEntry alice = UserEntry.builder()
                .username("alice")
                .emails(List.of())
                .scmIdentities(List.of())
                .build();
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getPathInfo()).thenReturn("/owner/repo.git/git-receive-pack");
        when(req.getAttribute(FogwallCredentialFilter.AUTHENTICATION_ATTRIBUTE))
                .thenReturn(new CredentialAuthentication(alice, "abcdefghijklmnop", "laptop"));
        when(req.getAttribute(ServerRepositoryResolver.CREDENTIALS_ATTRIBUTE))
                .thenReturn(new ScmOAuthCredentialsProvider(mock(ScmOAuthTokenService.class), "alice", "github"));
        when(req.getAttribute(ServerRepositoryResolver.UPSTREAM_URL_ATTRIBUTE))
                .thenReturn("https://github.com/owner/repo.git");

        clearInvocations(pushStore);
        ReceivePack rp = factory.create(req, db);
        rp.getPreReceiveHook().onPreReceive(rp, List.of());

        ArgumentCaptor<PushRecord> record = ArgumentCaptor.forClass(PushRecord.class);
        verify(pushStore).save(record.capture());
        return record.getValue().getSteps().stream().map(PushStep::getStepName).collect(Collectors.toSet());
    }

    @Test
    void checksTurnedOff_recordNoStep_andReloadTogglesThem() throws Exception {
        Repository db =
                Git.init().setBare(true).setDirectory(tempDir.toFile()).call().getRepository();
        PushStore pushStore = mock(PushStore.class);
        RepoPermissionService permissions = mock(RepoPermissionService.class);
        when(permissions.isAllowedToPush("alice", "github", "/owner/repo")).thenReturn(true);
        AtomicReference<BinaryBlobConfig> binaryBlob = new AtomicReference<>(BinaryBlobConfig.defaultConfig());
        AtomicReference<ContentPatternConfig> contentPatterns =
                new AtomicReference<>(ContentPatternConfig.defaultConfig());
        var factory = new ServerReceivePackFactory(
                provider,
                CommitConfig::defaultConfig,
                DiffScanConfig::defaultConfig,
                SecretScanConfig::defaultConfig,
                binaryBlob::get,
                contentPatterns::get,
                GpgConfig.defaultConfig(),
                permissions,
                mock(PushIdentityResolver.class),
                pushStore,
                mock(ApprovalGateway.class),
                policy,
                null,
                Duration.ofSeconds(10),
                mock(UrlRuleRegistry.class));

        Set<String> allOff = recordedStepNames(factory, pushStore, db);
        assertTrue(
                allOff.stream().noneMatch(SWITCHABLE_STEPS::contains),
                "checks turned off in config must record no step, got " + allOff);
        assertTrue(allOff.contains(PushStepKind.PUSH_PERMISSION.key()), "checks with no off state still record");

        binaryBlob.set(BinaryBlobConfig.builder().enabled(true).build());
        contentPatterns.set(ContentPatternConfig.builder()
                .enabled(true)
                .bundles(List.of("national-id-us"))
                .scanCommitMessages(false)
                .build());
        Set<String> afterReload = recordedStepNames(factory, pushStore, db);
        assertTrue(afterReload.contains(PushStepKind.BINARY_BLOB.key()));
        assertTrue(afterReload.contains(PushStepKind.CONTENT_PATTERN_DIFF.key()));
        assertFalse(afterReload.contains(PushStepKind.CONTENT_PATTERN_MESSAGE.key()));

        binaryBlob.set(BinaryBlobConfig.defaultConfig());
        contentPatterns.set(ContentPatternConfig.defaultConfig());
        Set<String> offAgain = recordedStepNames(factory, pushStore, db);
        assertTrue(offAgain.stream().noneMatch(SWITCHABLE_STEPS::contains), "got " + offAgain);
    }

    // ---- per-stage telemetry ----

    /**
     * Runs one credential-authenticated push's pre-receive chain under a request span, as ObservabilityFilter would.
     */
    private SpanData runUnderRequestSpan(
            InMemoryTelemetry otel, boolean allowedToPush, Repository db, PushStore pushStore) throws Exception {
        RepoPermissionService permissions = mock(RepoPermissionService.class);
        when(permissions.isAllowedToPush("alice", "github", "/owner/repo")).thenReturn(allowedToPush);
        var factory = new ServerReceivePackFactory(
                provider,
                CommitConfig::defaultConfig,
                null,
                null,
                null,
                null,
                null,
                permissions,
                mock(PushIdentityResolver.class),
                pushStore,
                mock(ApprovalGateway.class),
                policy,
                null,
                Duration.ofSeconds(10),
                mock(UrlRuleRegistry.class));
        factory.setTelemetry(otel.telemetry());
        UserEntry alice = UserEntry.builder()
                .username("alice")
                .emails(List.of())
                .scmIdentities(List.of())
                .build();
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getPathInfo()).thenReturn("/owner/repo.git/git-receive-pack");
        when(req.getAttribute(FogwallCredentialFilter.AUTHENTICATION_ATTRIBUTE))
                .thenReturn(new CredentialAuthentication(alice, "abcdefghijklmnop", "laptop"));
        when(req.getAttribute(ServerRepositoryResolver.CREDENTIALS_ATTRIBUTE))
                .thenReturn(new ScmOAuthCredentialsProvider(mock(ScmOAuthTokenService.class), "alice", "github"));
        when(req.getAttribute(ServerRepositoryResolver.UPSTREAM_URL_ATTRIBUTE))
                .thenReturn("https://github.com/owner/repo.git");

        Span request = otel.telemetry().tracer().spanBuilder("git.push server").startSpan();
        try (Scope ignored = request.makeCurrent()) {
            ReceivePack rp = factory.create(req, db);
            rp.getPreReceiveHook().onPreReceive(rp, List.of());
        } finally {
            request.end();
        }
        return otel.span("git.push server");
    }

    @Test
    void eachHook_isAStageSpanUnderTheRequestSpan() throws Exception {
        Repository db =
                Git.init().setBare(true).setDirectory(tempDir.toFile()).call().getRepository();
        var otel = new InMemoryTelemetry();

        SpanData request = runUnderRequestSpan(otel, true, db, mock(PushStore.class));

        SpanData permission = otel.span(PushStepKind.PUSH_PERMISSION.key());
        assertEquals(request.getSpanId(), permission.getParentSpanId());
        assertEquals("pass", permission.getAttributes().get(FogwallTelemetry.STAGE_OUTCOME));
        assertEquals(
                1,
                otel.stageCount(
                        PushStepKind.PUSH_PERMISSION.key(), FogwallTelemetry.MODE_SERVER, "github", StageOutcome.PASS));
        assertTrue(otel.spanNames().contains(FogwallTelemetry.APPROVAL_STAGE), "got " + otel.spanNames());
        assertTrue(
                otel.spans().stream()
                        .filter(s -> !s.getName().equals("git.push server"))
                        .allMatch(s -> s.getParentSpanId().equals(request.getSpanId())),
                "every stage is a direct child of the request span");
    }

    @Test
    void hookThatRefusesThePush_isABlockedStage() throws Exception {
        Repository db =
                Git.init().setBare(true).setDirectory(tempDir.toFile()).call().getRepository();
        var otel = new InMemoryTelemetry();

        runUnderRequestSpan(otel, false, db, mock(PushStore.class));

        assertEquals(
                "block",
                otel.span(PushStepKind.PUSH_PERMISSION.key()).getAttributes().get(FogwallTelemetry.STAGE_OUTCOME));
        assertEquals(
                1,
                otel.stageCount(
                        PushStepKind.PUSH_PERMISSION.key(),
                        FogwallTelemetry.MODE_SERVER,
                        "github",
                        StageOutcome.BLOCK));
    }

    @Test
    void sshStages_recordMetricsWithoutSpans() {
        var otel = new InMemoryTelemetry();
        UserEntry alice = UserEntry.builder().username("alice").build();
        var stages = ServerReceivePackFactory.HookStages.of(
                otel.telemetry(), PushTransport.ssh(alice, "SHA256:x", null, null), "github");

        stages.start(PushStepKind.URL_RULE.key()).end(StageOutcome.PASS);

        assertTrue(otel.spans().isEmpty());
        assertEquals(
                1,
                otel.stageCount(PushStepKind.URL_RULE.key(), FogwallTelemetry.MODE_SSH, "github", StageOutcome.PASS));
    }

    private long timeForward(InMemoryTelemetry otel, StepStatus recorded, StageOutcome expected) {
        var pushContext = new PushContext();
        ForwardingPostReceiveHook forwarding = mock(ForwardingPostReceiveHook.class);
        doAnswer(inv -> {
                    pushContext.addStep(PushStep.builder()
                            .stepName("forward")
                            .status(recorded)
                            .build());
                    return null;
                })
                .when(forwarding)
                .onPostReceive(any(), any());
        var stages = ServerReceivePackFactory.HookStages.of(otel.telemetry(), PushTransport.http(), "github");

        ServerReceivePackFactory.timedForward(forwarding, stages, pushContext).onPostReceive(null, List.of());

        return otel.stageCount(FogwallTelemetry.FORWARD_STAGE, FogwallTelemetry.MODE_SERVER, "github", expected);
    }

    @Test
    void forward_isAPassStage_whenItSucceeds() {
        assertEquals(1, timeForward(new InMemoryTelemetry(), StepStatus.PASS, StageOutcome.PASS));
    }

    @Test
    void forward_isAnErrorStage_whenTheForwardingHookRecordsAFailure() {
        assertEquals(1, timeForward(new InMemoryTelemetry(), StepStatus.FAIL, StageOutcome.ERROR));
    }

    @Test
    void forward_isNotWrapped_whenTelemetryIsOff() {
        ForwardingPostReceiveHook forwarding = mock(ForwardingPostReceiveHook.class);
        var stages =
                ServerReceivePackFactory.HookStages.of(FogwallTelemetry.disabled(), PushTransport.http(), "github");

        assertSame(forwarding, ServerReceivePackFactory.timedForward(forwarding, stages, new PushContext()));
    }

    @Test
    void hookOutcome_tellsAnErrorFromARefusal() {
        var context = new ValidationContext();
        context.addIssue(PushStepKind.URL_RULE, "earlier", "an issue from a previous hook");
        int before = context.getIssues().size();

        assertEquals(StageOutcome.PASS, ServerReceivePackFactory.hookOutcome(context, before, false));
        context.addIssue(PushStepKind.SECRET_SCAN, "secret found", "detail");
        assertEquals(StageOutcome.BLOCK, ServerReceivePackFactory.hookOutcome(context, before, true));
        context.addError(PushStepKind.SECRET_SCAN, "scanner failed", "detail");
        assertEquals(StageOutcome.ERROR, ServerReceivePackFactory.hookOutcome(context, before, true));
    }

    @Test
    void hookOutcome_ignoresErrorsFromEarlierHooks() {
        var context = new ValidationContext();
        context.addError(PushStepKind.DIFF_SCAN, "earlier error", "detail");
        int before = context.getIssues().size();
        context.addIssue(PushStepKind.SECRET_SCAN, "secret found", "detail");

        assertEquals(StageOutcome.BLOCK, ServerReceivePackFactory.hookOutcome(context, before, true));
    }

    @Test
    void lifecycleHooksOtherThanApproval_areNotStages() {
        var stages = ServerReceivePackFactory.HookStages.of(
                new InMemoryTelemetry().telemetry(), PushTransport.http(), "github");

        assertSame(StageTimer.NOOP, stages.start(new QuarantinePromotionHook(null)));
    }
}
