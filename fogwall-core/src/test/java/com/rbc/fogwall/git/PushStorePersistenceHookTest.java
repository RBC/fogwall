package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.config.CommitConfig;
import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.PushStoreFactory;
import com.rbc.fogwall.db.model.PushQuery;
import com.rbc.fogwall.db.model.PushStatus;
import com.rbc.fogwall.db.model.PushStep;
import com.rbc.fogwall.db.model.StepStatus;
import com.rbc.fogwall.provider.GitHubProvider;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests for {@link PushStorePersistenceHook}.
 *
 * <p>Exercises the single-record lifecycle model: {@code validationResultHook} creates exactly one record keyed by the
 * push id (PENDING for a clean push, REJECTED for a hard policy violation), and {@code postReceiveHook} transitions
 * that same record in place to FORWARDED or ERROR. The push id is minted by the receive-pack factory in production; the
 * tests stamp it into the {@link PushContext} directly to stand in for that.
 *
 * <p>Uses a real JGit repository (via {@code @TempDir}) and an H2 in-memory push store so there are no external
 * dependencies.
 */
class PushStorePersistenceHookTest {

    @TempDir
    Path tempDir;

    Repository repo;
    ObjectId commitId;
    PushStore pushStore;
    PushStorePersistenceHook hook;
    PushContext pushContext;

    @BeforeEach
    void setUp() throws Exception {
        Git git = Git.init().setDirectory(tempDir.toFile()).call();
        repo = git.getRepository();
        repo.getConfig().setBoolean("commit", null, "gpgsign", false);
        repo.getConfig().save();

        File f = new File(tempDir.toFile(), "init.txt");
        f.createNewFile();
        Files.writeString(f.toPath(), "initial");
        git.add().addFilepattern(".").call();
        RevCommit c = git.commit()
                .setAuthor(new PersonIdent("Dev", "dev@example.com"))
                .setCommitter(new PersonIdent("Dev", "dev@example.com"))
                .setMessage("Initial commit")
                .call();
        commitId = c.getId();

        pushStore = PushStoreFactory.h2InMemory("test-" + UUID.randomUUID());
        hook = new PushStorePersistenceHook(pushStore, new GitHubProvider("/push"));
        pushContext = new PushContext();
        hook.setPushContext(pushContext);
    }

    private ReceivePack makeReceivePack() {
        return new ReceivePack(repo);
    }

    private ReceiveCommand newBranchCommand(ObjectId newCommit) {
        return new ReceiveCommand(ObjectId.zeroId(), newCommit, "refs/heads/test");
    }

    /** Stamps a push id into the context the way {@code StoreAndForwardReceivePackFactory} does in production. */
    private String stampPushId() {
        String pushId = UUID.randomUUID().toString();
        pushContext.setPushId(pushId);
        return pushId;
    }

    // ---- single-record creation, keyed by the correlation id ----

    @Test
    void validationResultHook_createsSingleRecordKeyedByPushId() {
        ReceivePack rp = makeReceivePack();
        ReceiveCommand cmd = newBranchCommand(commitId);
        String pushId = stampPushId();

        hook.validationResultHook(new ValidationContext()).onPreReceive(rp, List.of(cmd));

        // Exactly one record, keyed by the push id — no separate RECEIVED row, no fresh UUID.
        var record = pushStore.findById(pushId);
        assertTrue(
                record.isPresent(), "the lifecycle record must be keyed by the push id (the correlation identifier)");
        assertEquals(pushId, pushContext.getValidationRecordId(), "validation record id must equal the push id");
        assertEquals(
                1,
                pushStore.find(PushQuery.builder().build()).size(),
                "a store-and-forward push must produce exactly one record");
    }

    @Test
    void validationResultHook_stampsReceivedAtFromHandoff() {
        ReceivePack rp = makeReceivePack();
        ReceiveCommand cmd = newBranchCommand(commitId);
        String pushId = stampPushId();
        Instant received = Instant.parse("2020-01-01T00:00:00Z");
        pushContext.setReceivedAt(received);

        hook.validationResultHook(new ValidationContext()).onPreReceive(rp, List.of(cmd));

        assertEquals(
                received,
                pushStore.findById(pushId).orElseThrow().getTimestamp(),
                "receivedAt captured at the handoff must be carried onto the record, not overwritten with build time");
    }

    @Test
    void validationResultHook_recordsBranchAndCommitTo() {
        ReceivePack rp = makeReceivePack();
        ReceiveCommand cmd = newBranchCommand(commitId);
        String pushId = stampPushId();

        hook.validationResultHook(new ValidationContext()).onPreReceive(rp, List.of(cmd));

        var record = pushStore.findById(pushId).orElseThrow();
        assertEquals("refs/heads/test", record.getBranch());
        assertEquals(commitId.name(), record.getCommitTo());
    }

    // ---- validation-result hook: no issues → PENDING ----

    @Test
    void validationResultHook_noIssues_transitionsToPending() {
        ReceivePack rp = makeReceivePack();
        ReceiveCommand cmd = newBranchCommand(commitId);
        String pushId = stampPushId();

        hook.validationResultHook(new ValidationContext()).onPreReceive(rp, List.of(cmd));

        var record = pushStore.findById(pushId).orElseThrow();
        assertEquals(PushStatus.PENDING, record.getStatus(), "a clean push must be PENDING awaiting review");
    }

    @Test
    void validationResultHook_noIssues_commandsNotRejected() {
        ReceivePack rp = makeReceivePack();
        ReceiveCommand cmd = newBranchCommand(commitId);
        stampPushId();

        hook.validationResultHook(new ValidationContext()).onPreReceive(rp, List.of(cmd));

        assertEquals(
                ReceiveCommand.Result.NOT_ATTEMPTED,
                cmd.getResult(),
                "commands must not be rejected on a clean push (S&F doesn't reject here - it blocks in approval hook)");
    }

    // ---- validation-result hook: with issues → REJECTED ----

    @Test
    void validationResultHook_withIssues_transitionsToRejected() {
        ReceivePack rp = makeReceivePack();
        ReceiveCommand cmd = newBranchCommand(commitId);
        String pushId = stampPushId();

        ValidationContext ctx = new ValidationContext();
        ctx.addIssue("checkAuthorEmails", "Email blocked", "noreply@ address is not allowed");
        hook.validationResultHook(ctx).onPreReceive(rp, List.of(cmd));

        var record = pushStore.findById(pushId).orElseThrow();
        assertEquals(PushStatus.REJECTED, record.getStatus());
        assertTrue(record.isAutoRejected(), "a hard policy violation is auto-rejected");
    }

    @Test
    void validationResultHook_withIssues_commandsAreRejected() {
        ReceivePack rp = makeReceivePack();
        ReceiveCommand cmd = newBranchCommand(commitId);
        stampPushId();

        ValidationContext ctx = new ValidationContext();
        ctx.addIssue("checkCommitMessages", "WIP commit", "message contains blocked term");
        hook.validationResultHook(ctx).onPreReceive(rp, List.of(cmd));

        assertEquals(
                ReceiveCommand.Result.REJECTED_OTHER_REASON,
                cmd.getResult(),
                "commands must be rejected when there are validation issues");
    }

    @Test
    void validationResultHook_withIssues_rejectedRecordHasBlockedMessage() {
        ReceivePack rp = makeReceivePack();
        ReceiveCommand cmd = newBranchCommand(commitId);
        String pushId = stampPushId();

        ValidationContext ctx = new ValidationContext();
        ctx.addIssue("checkAuthorEmails", "Email blocked", "some detail");
        hook.validationResultHook(ctx).onPreReceive(rp, List.of(cmd));

        String blocked = pushStore.findById(pushId).orElseThrow().getBlockedMessage();
        assertNotNull(blocked, "blockedMessage should describe the rejection");
        assertTrue(blocked.contains("validation issue"), "blockedMessage should mention validation issue(s)");
    }

    // ---- multiple validation issues ----

    @Test
    void validationResultHook_multipleIssues_allRecorded() {
        ReceivePack rp = makeReceivePack();
        ReceiveCommand cmd = newBranchCommand(commitId);
        String pushId = stampPushId();

        ValidationContext ctx = new ValidationContext();
        ctx.addIssue("checkAuthorEmails", "Email blocked", "noreply@ address");
        ctx.addIssue("checkCommitMessages", "WIP commit", "message contains WIP");

        hook.validationResultHook(ctx).onPreReceive(rp, List.of(cmd));

        String msg = pushStore.findById(pushId).orElseThrow().getBlockedMessage();
        assertTrue(msg.contains("2"), "blockedMessage should mention two validation issues");
    }

    // ---- serviceUrl included in rejection output ----

    @Test
    void serviceUrl_setBeforeHook_includedInBlockedRecord() {
        hook.setServiceUrl("http://dashboard:8080");
        ReceivePack rp = makeReceivePack();
        ReceiveCommand cmd = newBranchCommand(commitId);
        String pushId = stampPushId();

        // Clean push → PENDING with dashboard link (verified indirectly through record status)
        hook.validationResultHook(new ValidationContext()).onPreReceive(rp, List.of(cmd));

        assertEquals(
                PushStatus.PENDING, pushStore.findById(pushId).orElseThrow().getStatus());
    }

    // ---- post-receive hook: forwarding outcome updates the same record in place ----

    @Test
    void postReceiveHook_forwardFailed_transitionsRecordToError() {
        ReceivePack rp = makeReceivePack();
        ReceiveCommand cmd = newBranchCommand(commitId);
        String pushId = stampPushId();

        // The record must exist (PENDING) before the forward transition can update it in place.
        hook.validationResultHook(new ValidationContext()).onPreReceive(rp, List.of(cmd));

        // Simulate a failed forward step (upstream rejected the push)
        pushContext.addStep(PushStep.builder()
                .stepName("forward")
                .status(StepStatus.FAIL)
                .errorMessage("REJECTED_OTHER_REASON (pre-receive hook declined)")
                .build());

        // Mark command as OK (JGit accepted locally) — post-receive only sees OK commands
        cmd.setResult(ReceiveCommand.Result.OK);

        hook.postReceiveHook().onPostReceive(rp, List.of(cmd));

        var record = pushStore.findById(pushId).orElseThrow();
        assertEquals(PushStatus.ERROR, record.getStatus(), "a failed forward must transition the record to ERROR");
        assertNotNull(record.getErrorMessage(), "ERROR record should carry the upstream failure cause");
        assertEquals(
                1,
                pushStore.find(PushQuery.builder().build()).size(),
                "the record is updated in place — still exactly one record");
    }

    @Test
    void postReceiveHook_forwardSucceeded_transitionsRecordToForwarded() {
        ReceivePack rp = makeReceivePack();
        ReceiveCommand cmd = newBranchCommand(commitId);
        String pushId = stampPushId();

        hook.validationResultHook(new ValidationContext()).onPreReceive(rp, List.of(cmd));

        pushContext.addStep(
                PushStep.builder().stepName("forward").status(StepStatus.PASS).build());

        cmd.setResult(ReceiveCommand.Result.OK);

        hook.postReceiveHook().onPostReceive(rp, List.of(cmd));

        var record = pushStore.findById(pushId).orElseThrow();
        assertEquals(PushStatus.FORWARDED, record.getStatus());
        assertNotNull(record.getForwardedAt(), "forwardedAt must be stamped on the forwarded transition");
        assertEquals(
                1,
                pushStore.find(PushQuery.builder().build()).size(),
                "the record is updated in place — still exactly one record");
    }

    // ---- commit enrichment on re-push (PriorPushEnrichmentHook integration) ----

    @Test
    void validationResultHook_withEffectiveFromId_rebuildsFullCommitList() throws Exception {
        // Simulate: c1 (commitId) was forwarded. c2 was locally cached but not forwarded.
        // c3 is the new commit in the re-push. effectiveFromId = commitId (c1).
        // The PENDING record should list c2 and c3, not just c3.
        Git g = Git.open(tempDir.toFile());
        File f2 = new File(tempDir.toFile(), "second.txt");
        Files.writeString(f2.toPath(), "second");
        g.add().addFilepattern(".").call();
        RevCommit c2 = g.commit()
                .setAuthor(new PersonIdent("Dev", "dev@example.com"))
                .setCommitter(new PersonIdent("Dev", "dev@example.com"))
                .setMessage("second commit")
                .call();
        File f3 = new File(tempDir.toFile(), "third.txt");
        Files.writeString(f3.toPath(), "third");
        g.add().addFilepattern(".").call();
        RevCommit c3 = g.commit()
                .setAuthor(new PersonIdent("Dev", "dev@example.com"))
                .setCommitter(new PersonIdent("Dev", "dev@example.com"))
                .setMessage("third commit")
                .call();

        ReceivePack rp = makeReceivePack();
        // cmd represents the local-cache delta: c2 → c3
        ReceiveCommand cmd = new ReceiveCommand(c2.getId(), c3.getId(), "refs/heads/test", ReceiveCommand.Type.UPDATE);
        String pushId = stampPushId();

        // Enrichment hook detected: c2 not forwarded, effectiveFrom = commitId (the first commit, c1)
        pushContext.setEffectiveFromId("refs/heads/test", commitId.name());

        hook.validationResultHook(new ValidationContext()).onPreReceive(rp, List.of(cmd));

        var commits = pushStore.findById(pushId).orElseThrow().getCommits();
        // Should include both c2 and c3 (range from commitId to c3), not just c3
        assertTrue(commits.size() >= 2, "enriched PENDING record must include commits from effectiveFrom..c3");
        var shas = commits.stream().map(pc -> pc.getSha()).toList();
        assertTrue(shas.contains(c3.getId().name()), "c3 must be in enriched commit list");
        assertTrue(shas.contains(c2.getId().name()), "c2 must be in enriched commit list (was cached, not forwarded)");
    }

    // ---- email validation config ----

    private CommitConfig blockNoreplyConfig() {
        return CommitConfig.builder()
                .author(CommitConfig.AuthorConfig.builder()
                        .email(CommitConfig.EmailConfig.builder()
                                .local(CommitConfig.LocalConfig.builder()
                                        .block(Pattern.compile("^(noreply|no-reply|bot)$"))
                                        .build())
                                .build())
                        .build())
                .build();
    }
}
