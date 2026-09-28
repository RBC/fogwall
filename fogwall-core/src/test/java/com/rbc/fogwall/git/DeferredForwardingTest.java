package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.approval.ApprovalGateway;
import com.rbc.fogwall.approval.SelfApprovalPolicy;
import com.rbc.fogwall.config.CommitConfig;
import com.rbc.fogwall.db.jdbc.DataSourceFactory;
import com.rbc.fogwall.db.jdbc.JdbcParkedPushStore;
import com.rbc.fogwall.db.jdbc.JdbcPushStore;
import com.rbc.fogwall.db.memory.InMemoryUrlRuleRegistry;
import com.rbc.fogwall.db.model.AccessRule;
import com.rbc.fogwall.db.model.Attestation;
import com.rbc.fogwall.db.model.MatchTarget;
import com.rbc.fogwall.db.model.MatchType;
import com.rbc.fogwall.db.model.ParkedRefUpdate;
import com.rbc.fogwall.db.model.PushQuery;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStatus;
import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.provider.GitHubProvider;
import com.rbc.fogwall.service.PushIdentityResolver;
import com.rbc.fogwall.service.ScmOAuthTokenService;
import com.rbc.fogwall.servlet.filter.FogwallCredentialFilter;
import com.rbc.fogwall.user.UserEntry;
import jakarta.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.TestProtocol;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives a real git push through {@link ServerReceivePackFactory} in-process and checks deferred forwarding end to end
 * against a file upstream: the client is told its push succeeded while neither the mirror nor upstream moves, and the
 * push lands upstream only once approved and forwarded from the parked-push store.
 */
class DeferredForwardingTest {

    private static final String MAIN = "refs/heads/main";

    @TempDir
    Path tempDir;

    private Repository upstream;
    private String upstreamUrl;
    private Repository mirror;
    private Git client;
    private JdbcPushStore pushStore;
    private JdbcParkedPushStore parkedPushStore;
    private DeferredForwarder forwarder;
    private ServerReceivePackFactory factory;
    private TestProtocol<HttpServletRequest> protocol;
    private final ApprovalGateway approvalGateway = mock(ApprovalGateway.class);
    private final ScmOAuthTokenService oauthTokens = mock(ScmOAuthTokenService.class);
    private final SelfApprovalPolicy policy = mock(SelfApprovalPolicy.class);

    @BeforeEach
    void setUp() throws Exception {
        upstream = Git.init()
                .setBare(true)
                .setInitialBranch("main")
                .setDirectory(tempDir.resolve("upstream.git").toFile())
                .call()
                .getRepository();
        upstreamUrl = upstream.getDirectory().toURI().toString();

        client = Git.cloneRepository()
                .setURI(upstreamUrl)
                .setDirectory(tempDir.resolve("client").toFile())
                .call();
        client.getRepository().getConfig().setBoolean("commit", null, "gpgsign", false);
        client.getRepository().getConfig().setBoolean("tag", null, "gpgsign", false);
        client.getRepository().getConfig().save();
        commit("initial");
        client.push()
                .setRemote("origin")
                .setRefSpecs(new RefSpec("HEAD:" + MAIN))
                .call();

        when(oauthTokens.access("alice", "github")).thenReturn(new ScmOAuthTokenService.Access.Usable("token", "repo"));
        var credentials = new ScmOAuthCredentialsProvider(oauthTokens, "alice", "github");
        var cache = new LocalRepositoryCache(tempDir.resolve("cache"), 0, false);
        mirror = cache.getOrClone(upstreamUrl, credentials, null, "fogwall-user:alice");

        DataSource dataSource = DataSourceFactory.h2InMemory("deferred-" + UUID.randomUUID());
        pushStore = new JdbcPushStore(dataSource);
        pushStore.initialize();
        parkedPushStore = new JdbcParkedPushStore(dataSource);

        when(policy.evaluate(any())).thenReturn(SelfApprovalPolicy.Verdict.NOT_SELF_APPROVAL);
        forwarder = new DeferredForwarder(
                pushStore,
                parkedPushStore,
                cache,
                oauthTokens,
                policy,
                "instance-a",
                Duration.ofMinutes(15),
                Duration.ofDays(7),
                0,
                Clock.systemUTC());

        var rules = new InMemoryUrlRuleRegistry();
        rules.save(AccessRule.builder()
                .ruleOrder(1)
                .access(AccessRule.Access.ALLOW)
                .operation(AccessRule.Operation.BOTH)
                .target(MatchTarget.OWNER)
                .value("owner")
                .matchType(MatchType.GLOB)
                .build());
        RepoPermissionService permissions = mock(RepoPermissionService.class);
        when(permissions.isAllowedToPush(anyString(), anyString(), anyString())).thenReturn(true);
        factory = new ServerReceivePackFactory(
                new GitHubProvider("/push"),
                CommitConfig::defaultConfig,
                null,
                null,
                null,
                null,
                null,
                permissions,
                mock(PushIdentityResolver.class),
                pushStore,
                approvalGateway,
                policy,
                null,
                Duration.ofSeconds(10),
                rules);
        factory.setDeferredForwarding(new ServerReceivePackFactory.DeferredForwarding(parkedPushStore, forwarder));

        // TestProtocol serves over an in-memory bidirectional pipe, as the SSH transport does.
        protocol = new TestProtocol<>(null, (req, db) -> {
            var rp = factory.create(req, db);
            rp.setBiDirectionalPipe(true);
            return rp;
        });
        Transport.register(protocol);
    }

    @AfterEach
    void tearDown() {
        Transport.unregister(protocol);
        forwarder.close();
    }

    private RevCommit commit(String message) throws Exception {
        Path file = client.getRepository().getWorkTree().toPath().resolve("file.txt");
        Files.writeString(file, message + "\n");
        client.add().addFilepattern("file.txt").call();
        return client.commit()
                .setMessage(message)
                .setAuthor("Alice", "alice@example.com")
                .setCommitter("Alice", "alice@example.com")
                .call();
    }

    private HttpServletRequest credentialRequest() {
        UserEntry alice = UserEntry.builder()
                .username("alice")
                .emails(List.of())
                .scmIdentities(List.of())
                .build();
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getPathInfo()).thenReturn("/owner/repo.git/git-receive-pack");
        when(req.getAttribute(FogwallCredentialFilter.AUTHENTICATION_ATTRIBUTE))
                .thenReturn(new CredentialAuthentication(alice, "abcdefghijklmnop", "laptop"));
        when(req.getAttribute(ServerRepositoryResolver.UPSTREAM_URL_ATTRIBUTE)).thenReturn(upstreamUrl);
        return req;
    }

    /** Pushes HEAD to main through fogwall and returns the id of the push record it created. */
    private String pushThroughFogwall() throws Exception {
        URIish uri = protocol.register(credentialRequest(), mirror);
        Iterable<PushResult> results = client.push()
                .setRemote(uri.toString())
                .setRefSpecs(new RefSpec("HEAD:" + MAIN))
                .call();
        RemoteRefUpdate update = results.iterator().next().getRemoteUpdate(MAIN);
        assertEquals(RemoteRefUpdate.Status.OK, update.getStatus(), "the client is told its push succeeded");
        return pushStore.find(PushQuery.builder().build()).stream()
                .filter(r -> r.getCommitTo().equals(update.getNewObjectId().name()))
                .map(PushRecord::getId)
                .findFirst()
                .orElseThrow();
    }

    private void approve(String pushId) {
        pushStore.approve(
                pushId,
                Attestation.builder()
                        .pushId(pushId)
                        .type(Attestation.Type.APPROVAL)
                        .reviewerUsername("reviewer")
                        .build());
    }

    private PushRecord awaitTerminal(String pushId) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(20);
        while (Instant.now().isBefore(deadline)) {
            PushRecord record = pushStore.findById(pushId).orElseThrow();
            if (record.getStatus() == PushStatus.FORWARDED || record.getStatus() == PushStatus.ERROR) {
                return record;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("push " + pushId + " was not forwarded in time");
    }

    private ObjectId tip(Repository repo) throws Exception {
        return repo.exactRef(MAIN).getObjectId();
    }

    @Test
    void push_isAcknowledgedAndParked_withoutMovingMirrorOrUpstream() throws Exception {
        ObjectId before = tip(upstream);
        commit("parked change");

        String pushId = pushThroughFogwall();

        PushRecord record = pushStore.findById(pushId).orElseThrow();
        assertEquals(PushStatus.PENDING, record.getStatus());
        assertTrue(record.isDeferred());
        assertTrue(parkedPushStore.find(pushId).isPresent());
        assertEquals(before, tip(upstream));
        assertEquals(before, tip(mirror));
        verify(approvalGateway, never()).waitForApproval(anyString(), any(), any(), any());
    }

    @Test
    void approvedPush_isForwardedFromTheStore() throws Exception {
        RevCommit change = commit("parked change");
        String pushId = pushThroughFogwall();

        approve(pushId);
        assertTrue(forwarder.start(pushId, false));

        PushRecord record = awaitTerminal(pushId);
        assertEquals(PushStatus.FORWARDED, record.getStatus(), record.getErrorMessage());
        assertEquals(change.getId(), tip(upstream));
        assertTrue(parkedPushStore.find(pushId).isEmpty(), "the pack is deleted once forwarded");
    }

    @Test
    void pendingPush_cannotBeForwarded() throws Exception {
        commit("parked change");
        String pushId = pushThroughFogwall();

        assertFalse(forwarder.start(pushId, false));
    }

    @Test
    void pushesQueuedOnOneBranch_bothLand() throws Exception {
        commit("first");
        String first = pushThroughFogwall();
        RevCommit second = commit("second");
        String secondId = pushThroughFogwall();

        approve(first);
        approve(secondId);
        forwarder.start(first, false);
        assertEquals(PushStatus.FORWARDED, awaitTerminal(first).getStatus());
        forwarder.start(secondId, false);

        PushRecord record = awaitTerminal(secondId);
        assertEquals(PushStatus.FORWARDED, record.getStatus(), record.getErrorMessage());
        assertEquals(second.getId(), tip(upstream));
    }

    @Test
    void upstreamMovedAway_endsInError_andKeepsThePackForRetry() throws Exception {
        commit("parked change");
        String pushId = pushThroughFogwall();
        // Someone else rewrites upstream main to an unrelated history while the push waits for review.
        Git other = Git.init().setDirectory(tempDir.resolve("other").toFile()).call();
        other.getRepository().getConfig().setBoolean("commit", null, "gpgsign", false);
        other.getRepository().getConfig().save();
        Files.writeString(tempDir.resolve("other").resolve("x.txt"), "x\n");
        other.add().addFilepattern("x.txt").call();
        other.commit().setMessage("unrelated").call();
        other.push()
                .setRemote(upstreamUrl)
                .setRefSpecs(new RefSpec("+HEAD:" + MAIN))
                .call();
        ObjectId rewritten = tip(upstream);

        approve(pushId);
        forwarder.start(pushId, false);

        PushRecord record = awaitTerminal(pushId);
        assertEquals(PushStatus.ERROR, record.getStatus());
        assertNotNull(record.getErrorMessage());
        assertEquals(rewritten, tip(upstream));
        assertTrue(parkedPushStore.find(pushId).isPresent(), "a failed forward keeps its pack for a retry");
        assertTrue(forwarder.start(pushId, true), "forward now can retry it");
    }

    // ---- refusals: each leaves upstream untouched and records why ----

    private PushRecord forwardAndAwait(String pushId) throws InterruptedException {
        assertTrue(forwarder.start(pushId, false));
        return awaitTerminal(pushId);
    }

    @Test
    void approvalAttestationMissing_refused() throws Exception {
        ObjectId before = tip(upstream);
        commit("parked change");
        String pushId = pushThroughFogwall();
        // APPROVED, but the latest attestation is not an approval.
        pushStore.approve(
                pushId,
                Attestation.builder()
                        .pushId(pushId)
                        .type(Attestation.Type.REJECTION)
                        .reviewerUsername("reviewer")
                        .build());

        PushRecord record = forwardAndAwait(pushId);

        assertEquals(PushStatus.ERROR, record.getStatus());
        assertEquals("No approval is recorded for this push", record.getErrorMessage());
        assertEquals(before, tip(upstream));
    }

    @Test
    void selfApprovalNoLongerEntitled_refused() throws Exception {
        ObjectId before = tip(upstream);
        commit("parked change");
        String pushId = pushThroughFogwall();
        approve(pushId);
        when(policy.evaluate(any())).thenReturn(SelfApprovalPolicy.Verdict.MISSING_ROLE);

        PushRecord record = forwardAndAwait(pushId);

        assertEquals(PushStatus.ERROR, record.getStatus());
        assertEquals(SelfApprovalPolicy.Verdict.MISSING_ROLE.getReason(), record.getErrorMessage());
        assertEquals(before, tip(upstream));
    }

    @Test
    void linkedAccountUnusable_refusedWithTheRemedy() throws Exception {
        ObjectId before = tip(upstream);
        commit("parked change");
        String pushId = pushThroughFogwall();
        approve(pushId);
        when(oauthTokens.access("alice", "github"))
                .thenReturn(new ScmOAuthTokenService.Access.Unusable(ScmOAuthTokenService.Reason.NOT_LINKED));

        PushRecord record = forwardAndAwait(pushId);

        assertEquals(PushStatus.ERROR, record.getStatus());
        assertTrue(record.getErrorMessage().contains("link it again"), record.getErrorMessage());
        assertEquals(before, tip(upstream));
    }

    @Test
    void storedPushGone_refused() throws Exception {
        ObjectId before = tip(upstream);
        commit("parked change");
        String pushId = pushThroughFogwall();
        approve(pushId);
        parkedPushStore.delete(pushId);

        PushRecord record = forwardAndAwait(pushId);

        assertEquals(PushStatus.ERROR, record.getStatus());
        assertTrue(record.getErrorMessage().contains("no longer available"), record.getErrorMessage());
        assertEquals(before, tip(upstream));
    }

    @Test
    void storedPushMissingItsTip_refused() throws Exception {
        // A stored push whose pack does not contain the commit its ref update names.
        ObjectId before = tip(upstream);
        String pushId = UUID.randomUUID().toString();
        String missing = "1234567890123456789012345678901234567890";
        parkedPushStore.park(
                pushId,
                "github",
                "alice",
                upstreamUrl,
                List.of(new ParkedRefUpdate(MAIN, before.name(), missing, ReceiveCommand.Type.UPDATE)),
                InputStream.nullInputStream());
        pushStore.save(PushRecord.builder()
                .id(pushId)
                .status(PushStatus.PENDING)
                .deferred(true)
                .commitTo(missing)
                .build());
        approve(pushId);

        PushRecord record = forwardAndAwait(pushId);

        assertEquals(PushStatus.ERROR, record.getStatus());
        assertTrue(record.getErrorMessage().contains("missing " + missing), record.getErrorMessage());
        assertEquals(before, tip(upstream));
    }

    @Test
    void claimTakenOverMidForward_outcomeNotRecordedAndPackKept() throws Exception {
        commit("parked change");
        String pushId = pushThroughFogwall();
        approve(pushId);
        // Another instance holds the claim, so this one's result must not overwrite whatever that one records.
        assertTrue(pushStore.claimForward(pushId, "instance-b", Instant.now(), Duration.ofMinutes(15), false));

        forwarder.forwardClaimed(pushId);

        assertEquals(
                PushStatus.APPROVED, pushStore.findById(pushId).orElseThrow().getStatus());
        assertTrue(parkedPushStore.find(pushId).isPresent());
    }

    // ---- sweep ----

    @Test
    void sweep_forwardsApprovedPushesNobodyStarted() throws Exception {
        RevCommit change = commit("parked change");
        String pushId = pushThroughFogwall();
        approve(pushId);

        assertEquals(1, forwarder.sweep());

        assertEquals(PushStatus.FORWARDED, awaitTerminal(pushId).getStatus());
        assertEquals(change.getId(), tip(upstream));
    }

    @Test
    void sweep_deletesPacksNothingCanForward() throws Exception {
        commit("parked change");
        String pushId = pushThroughFogwall();
        pushStore.reject(
                pushId,
                Attestation.builder()
                        .pushId(pushId)
                        .type(Attestation.Type.REJECTION)
                        .reviewerUsername("reviewer")
                        .build());

        assertEquals(0, forwarder.sweep());

        assertTrue(parkedPushStore.find(pushId).isEmpty());
    }

    // ---- replaying forced updates and deletes ----

    /** Pushes a ref straight to upstream, bypassing fogwall, and brings the mirror current. */
    private void pushUpstreamDirectly(String refSpec) throws Exception {
        client.push().setRemote(upstreamUrl).setRefSpecs(new RefSpec(refSpec)).call();
        Git.wrap(mirror)
                .fetch()
                .setRemote(upstreamUrl)
                .setRefSpecs(new RefSpec("+refs/heads/*:refs/heads/*"))
                .setRemoveDeletedRefs(true)
                .call();
    }

    @Test
    void parkedDelete_isReplayed() throws Exception {
        commit("doomed");
        pushUpstreamDirectly("HEAD:refs/heads/doomed");
        URIish uri = protocol.register(credentialRequest(), mirror);
        var result = client.push()
                .setRemote(uri.toString())
                .setRefSpecs(new RefSpec(":refs/heads/doomed"))
                .call();
        assertEquals(
                RemoteRefUpdate.Status.OK,
                result.iterator().next().getRemoteUpdate("refs/heads/doomed").getStatus());
        String pushId = pushStore.find(PushQuery.builder().build()).stream()
                .filter(r -> "refs/heads/doomed".equals(r.getBranch()))
                .map(PushRecord::getId)
                .findFirst()
                .orElseThrow();
        approve(pushId);

        assertEquals(PushStatus.FORWARDED, forwardAndAwait(pushId).getStatus());
        assertNull(upstream.exactRef("refs/heads/doomed"));
    }

    @Test
    void parkedForcedUpdate_isReplayedOverTheTipTheDeveloperSaw() throws Exception {
        commit("will be rewritten");
        pushUpstreamDirectly("HEAD:" + MAIN);
        client.reset().setMode(ResetCommand.ResetType.HARD).setRef("HEAD~1").call();
        RevCommit rewritten = commit("rewritten");
        URIish uri = protocol.register(credentialRequest(), mirror);
        var result = client.push()
                .setRemote(uri.toString())
                .setRefSpecs(new RefSpec("+HEAD:" + MAIN))
                .call();
        assertEquals(
                RemoteRefUpdate.Status.OK,
                result.iterator().next().getRemoteUpdate(MAIN).getStatus());
        String pushId = pushStore.find(PushQuery.builder().build()).stream()
                .filter(r -> rewritten.getName().equals(r.getCommitTo()))
                .map(PushRecord::getId)
                .findFirst()
                .orElseThrow();
        approve(pushId);

        assertEquals(PushStatus.FORWARDED, forwardAndAwait(pushId).getStatus());
        assertEquals(rewritten.getId(), tip(upstream));
    }

    @Test
    void parkedForcedUpdate_refusedIfUpstreamMovedSince() throws Exception {
        commit("will be rewritten");
        pushUpstreamDirectly("HEAD:" + MAIN);
        client.reset().setMode(ResetCommand.ResetType.HARD).setRef("HEAD~1").call();
        RevCommit rewritten = commit("rewritten");
        URIish uri = protocol.register(credentialRequest(), mirror);
        client.push()
                .setRemote(uri.toString())
                .setRefSpecs(new RefSpec("+HEAD:" + MAIN))
                .call();
        String pushId = pushStore.find(PushQuery.builder().build()).stream()
                .filter(r -> rewritten.getName().equals(r.getCommitTo()))
                .map(PushRecord::getId)
                .findFirst()
                .orElseThrow();
        // Someone else pushes to main while the forced update waits: it must not be overwritten unseen.
        Git other = Git.cloneRepository()
                .setURI(upstreamUrl)
                .setDirectory(tempDir.resolve("other").toFile())
                .call();
        other.getRepository().getConfig().setBoolean("commit", null, "gpgsign", false);
        other.getRepository().getConfig().save();
        Files.writeString(tempDir.resolve("other").resolve("other.txt"), "other\n");
        other.add().addFilepattern("other.txt").call();
        RevCommit theirs = other.commit()
                .setMessage("theirs")
                .setAuthor("Bob", "bob@example.com")
                .setCommitter("Bob", "bob@example.com")
                .call();
        other.push().setRefSpecs(new RefSpec("HEAD:" + MAIN)).call();
        approve(pushId);

        PushRecord record = forwardAndAwait(pushId);

        assertEquals(PushStatus.ERROR, record.getStatus());
        assertEquals(theirs.getId(), tip(upstream));
    }
}
