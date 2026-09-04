package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.approval.ApprovalGateway;
import com.rbc.fogwall.approval.ApprovalResult;
import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.model.Attestation;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStatus;
import com.rbc.fogwall.permission.RepoPermissionService;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApprovalPreReceiveHookTest {

    @TempDir
    Path tempDir;

    Git git;
    Repository repo;
    PushStore pushStore;
    ApprovalGateway approvalGateway;

    @BeforeEach
    void setUp() throws Exception {
        git = Git.init().setDirectory(tempDir.toFile()).call();
        repo = git.getRepository();
        repo.getConfig().setBoolean("commit", null, "gpgsign", false);
        repo.getConfig().save();
        pushStore = mock(PushStore.class);
        approvalGateway = mock(ApprovalGateway.class);
    }

    private RevCommit createCommit(String msg) throws Exception {
        File f = new File(tempDir.toFile(), UUID.randomUUID() + ".txt");
        Files.writeString(f.toPath(), msg);
        git.add().addFilepattern(".").call();
        return git.commit()
                .setAuthor(new PersonIdent("Dev", "dev@example.com"))
                .setCommitter(new PersonIdent("Dev", "dev@example.com"))
                .setMessage(msg)
                .call();
    }

    /**
     * No validationRecordId means the push record was never written — the initial create failed, or
     * {@code validationResultHook} could not save. The hook cannot establish that this push was approved, so it must
     * reject. Returning normally would forward it upstream unapproved on nothing more than a transient store failure,
     * and the missing record is also the audit evidence, so the bypass would leave no trace but a log line.
     */
    @Test
    void noValidationRecordId_rejectsPush() throws Exception {
        RevCommit c1 = createCommit("init");
        RevCommit c2 = createCommit("second");
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(c1.getId(), c2.getId(), "refs/heads/main", ReceiveCommand.Type.UPDATE);

        new ApprovalPreReceiveHook(pushStore, approvalGateway).onPreReceive(rp, List.of(cmd));

        assertEquals(
                ReceiveCommand.Result.REJECTED_OTHER_REASON,
                cmd.getResult(),
                "a push whose approval state cannot be established must be rejected, not forwarded");
        verifyNoInteractions(approvalGateway);
    }

    /** Same reasoning when the id exists but the record cannot be read back — e.g. the store is unavailable. */
    @Test
    void validationRecordNotInStore_rejectsPush() throws Exception {
        String recordId = UUID.randomUUID().toString();
        PushContext pushContext = new PushContext();
        pushContext.setValidationRecordId(recordId);
        when(pushStore.findById(recordId)).thenReturn(Optional.empty());

        RevCommit c1 = createCommit("init");
        RevCommit c2 = createCommit("second");
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(c1.getId(), c2.getId(), "refs/heads/main", ReceiveCommand.Type.UPDATE);

        new ApprovalPreReceiveHook(pushStore, approvalGateway, Duration.ofMinutes(30), null, null, pushContext)
                .onPreReceive(rp, List.of(cmd));

        assertEquals(
                ReceiveCommand.Result.REJECTED_OTHER_REASON,
                cmd.getResult(),
                "an unreadable approval record must block the push, not let it through");
        verifyNoInteractions(approvalGateway);
    }

    @Test
    void alreadyApproved_passesImmediately() throws Exception {
        String recordId = UUID.randomUUID().toString();
        PushContext pushContext = new PushContext();
        pushContext.setValidationRecordId(recordId);
        PushRecord record =
                PushRecord.builder().id(recordId).status(PushStatus.APPROVED).build();
        when(pushStore.findById(recordId)).thenReturn(Optional.of(record));

        RevCommit c1 = createCommit("init");
        RevCommit c2 = createCommit("second");
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(c1.getId(), c2.getId(), "refs/heads/main", ReceiveCommand.Type.UPDATE);

        new ApprovalPreReceiveHook(pushStore, approvalGateway, Duration.ofMinutes(30), null, null, pushContext)
                .onPreReceive(rp, List.of(cmd));

        assertEquals(ReceiveCommand.Result.NOT_ATTEMPTED, cmd.getResult());
        verifyNoInteractions(approvalGateway);
    }

    @Test
    void blockedPush_gatewayApproves_commandNotRejected() throws Exception {
        String recordId = UUID.randomUUID().toString();
        PushContext pushContext = new PushContext();
        pushContext.setValidationRecordId(recordId);
        PushRecord record =
                PushRecord.builder().id(recordId).status(PushStatus.PENDING).build();
        when(pushStore.findById(recordId)).thenReturn(Optional.of(record));
        when(approvalGateway.waitForApproval(eq(recordId), any(), any(), any(Duration.class)))
                .thenReturn(ApprovalResult.APPROVED);

        RevCommit c1 = createCommit("init");
        RevCommit c2 = createCommit("second");
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(c1.getId(), c2.getId(), "refs/heads/main", ReceiveCommand.Type.UPDATE);

        new ApprovalPreReceiveHook(pushStore, approvalGateway, Duration.ofSeconds(5), null, null, pushContext)
                .onPreReceive(rp, List.of(cmd));

        assertEquals(ReceiveCommand.Result.NOT_ATTEMPTED, cmd.getResult());
    }

    @Test
    void blockedPush_gatewayRejects_commandRejected() throws Exception {
        String recordId = UUID.randomUUID().toString();
        PushContext pushContext = new PushContext();
        pushContext.setValidationRecordId(recordId);
        PushRecord record =
                PushRecord.builder().id(recordId).status(PushStatus.PENDING).build();
        PushRecord updatedRecord =
                PushRecord.builder().id(recordId).status(PushStatus.REJECTED).build();
        when(pushStore.findById(recordId)).thenReturn(Optional.of(record)).thenReturn(Optional.of(updatedRecord));
        when(approvalGateway.waitForApproval(eq(recordId), any(), any(), any(Duration.class)))
                .thenReturn(ApprovalResult.REJECTED);

        RevCommit c1 = createCommit("init");
        RevCommit c2 = createCommit("second");
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(c1.getId(), c2.getId(), "refs/heads/main", ReceiveCommand.Type.UPDATE);

        new ApprovalPreReceiveHook(pushStore, approvalGateway, Duration.ofSeconds(5), null, null, pushContext)
                .onPreReceive(rp, List.of(cmd));

        assertEquals(ReceiveCommand.Result.REJECTED_OTHER_REASON, cmd.getResult());
    }

    // ── Defense-in-depth: hook re-verifies SELF_CERTIFY perm when approver == pusher ─────────────

    @Test
    void selfApproved_alreadyApprovedAtHookStart_noPerm_rejected() throws Exception {
        String recordId = UUID.randomUUID().toString();
        PushContext pushContext = new PushContext();
        pushContext.setValidationRecordId(recordId);
        Attestation att = Attestation.builder()
                .pushId(recordId)
                .type(Attestation.Type.APPROVAL)
                .reviewerUsername("alice")
                .build();
        PushRecord record = PushRecord.builder()
                .id(recordId)
                .status(PushStatus.APPROVED)
                .resolvedUser("alice")
                .provider("github")
                .url("/owner/repo")
                .attestation(att)
                .build();
        when(pushStore.findById(recordId)).thenReturn(Optional.of(record));
        RepoPermissionService perms = mock(RepoPermissionService.class);
        when(perms.isBypassReviewAllowed("alice", "github", "/owner/repo")).thenReturn(false);

        RevCommit c1 = createCommit("init");
        RevCommit c2 = createCommit("second");
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(c1.getId(), c2.getId(), "refs/heads/main", ReceiveCommand.Type.UPDATE);

        new ApprovalPreReceiveHook(pushStore, approvalGateway, Duration.ofSeconds(5), null, perms, pushContext)
                .onPreReceive(rp, List.of(cmd));

        assertEquals(ReceiveCommand.Result.REJECTED_OTHER_REASON, cmd.getResult());
        verify(perms).isBypassReviewAllowed("alice", "github", "/owner/repo");
    }

    @Test
    void selfApproved_noPermissionServiceWired_rejected() throws Exception {
        // Fail closed: a self-approval whose entitlement cannot be verified (no RepoPermissionService in the
        // wiring) must be rejected, not waved through.
        String recordId = UUID.randomUUID().toString();
        PushContext pushContext = new PushContext();
        pushContext.setValidationRecordId(recordId);
        Attestation att = Attestation.builder()
                .pushId(recordId)
                .type(Attestation.Type.APPROVAL)
                .reviewerUsername("alice")
                .build();
        PushRecord record = PushRecord.builder()
                .id(recordId)
                .status(PushStatus.APPROVED)
                .resolvedUser("alice")
                .provider("github")
                .url("/owner/repo")
                .attestation(att)
                .build();
        when(pushStore.findById(recordId)).thenReturn(Optional.of(record));

        RevCommit c1 = createCommit("init");
        RevCommit c2 = createCommit("second");
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(c1.getId(), c2.getId(), "refs/heads/main", ReceiveCommand.Type.UPDATE);

        new ApprovalPreReceiveHook(pushStore, approvalGateway, Duration.ofSeconds(5), null, null, pushContext)
                .onPreReceive(rp, List.of(cmd));

        assertEquals(ReceiveCommand.Result.REJECTED_OTHER_REASON, cmd.getResult());
    }

    @Test
    void selfApproved_alreadyApprovedAtHookStart_withPerm_passes() throws Exception {
        String recordId = UUID.randomUUID().toString();
        PushContext pushContext = new PushContext();
        pushContext.setValidationRecordId(recordId);
        Attestation att = Attestation.builder()
                .pushId(recordId)
                .type(Attestation.Type.APPROVAL)
                .reviewerUsername("alice")
                .build();
        PushRecord record = PushRecord.builder()
                .id(recordId)
                .status(PushStatus.APPROVED)
                .resolvedUser("alice")
                .provider("github")
                .url("/owner/repo")
                .attestation(att)
                .build();
        when(pushStore.findById(recordId)).thenReturn(Optional.of(record));
        RepoPermissionService perms = mock(RepoPermissionService.class);
        when(perms.isBypassReviewAllowed("alice", "github", "/owner/repo")).thenReturn(true);

        RevCommit c1 = createCommit("init");
        RevCommit c2 = createCommit("second");
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(c1.getId(), c2.getId(), "refs/heads/main", ReceiveCommand.Type.UPDATE);

        new ApprovalPreReceiveHook(pushStore, approvalGateway, Duration.ofSeconds(5), null, perms, pushContext)
                .onPreReceive(rp, List.of(cmd));

        assertEquals(ReceiveCommand.Result.NOT_ATTEMPTED, cmd.getResult());
    }

    @Test
    void selfApproved_viaWaitForApproval_noPerm_rejected() throws Exception {
        String recordId = UUID.randomUUID().toString();
        PushContext pushContext = new PushContext();
        pushContext.setValidationRecordId(recordId);
        // Initial fetch returns PENDING (no attestation yet); after approval, returns APPROVED with attestation
        // showing the pusher self-approved.
        PushRecord pending = PushRecord.builder()
                .id(recordId)
                .status(PushStatus.PENDING)
                .resolvedUser("alice")
                .provider("github")
                .url("/owner/repo")
                .build();
        Attestation att = Attestation.builder()
                .pushId(recordId)
                .type(Attestation.Type.APPROVAL)
                .reviewerUsername("alice")
                .build();
        PushRecord approved = PushRecord.builder()
                .id(recordId)
                .status(PushStatus.APPROVED)
                .resolvedUser("alice")
                .provider("github")
                .url("/owner/repo")
                .attestation(att)
                .build();
        when(pushStore.findById(recordId)).thenReturn(Optional.of(pending)).thenReturn(Optional.of(approved));
        when(approvalGateway.waitForApproval(eq(recordId), any(), any(), any(Duration.class)))
                .thenReturn(ApprovalResult.APPROVED);
        RepoPermissionService perms = mock(RepoPermissionService.class);
        when(perms.isBypassReviewAllowed("alice", "github", "/owner/repo")).thenReturn(false);

        RevCommit c1 = createCommit("init");
        RevCommit c2 = createCommit("second");
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(c1.getId(), c2.getId(), "refs/heads/main", ReceiveCommand.Type.UPDATE);

        new ApprovalPreReceiveHook(pushStore, approvalGateway, Duration.ofSeconds(5), null, perms, pushContext)
                .onPreReceive(rp, List.of(cmd));

        assertEquals(ReceiveCommand.Result.REJECTED_OTHER_REASON, cmd.getResult());
        verify(perms).isBypassReviewAllowed("alice", "github", "/owner/repo");
    }

    @Test
    void differentApproverThanPusher_noReVerifyNeeded() throws Exception {
        // Approver != pusher → defense-in-depth check skipped; push is forwarded.
        String recordId = UUID.randomUUID().toString();
        PushContext pushContext = new PushContext();
        pushContext.setValidationRecordId(recordId);
        Attestation att = Attestation.builder()
                .pushId(recordId)
                .type(Attestation.Type.APPROVAL)
                .reviewerUsername("bob")
                .build();
        PushRecord record = PushRecord.builder()
                .id(recordId)
                .status(PushStatus.APPROVED)
                .resolvedUser("alice")
                .provider("github")
                .url("/owner/repo")
                .attestation(att)
                .build();
        when(pushStore.findById(recordId)).thenReturn(Optional.of(record));
        RepoPermissionService perms = mock(RepoPermissionService.class);

        RevCommit c1 = createCommit("init");
        RevCommit c2 = createCommit("second");
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(c1.getId(), c2.getId(), "refs/heads/main", ReceiveCommand.Type.UPDATE);

        new ApprovalPreReceiveHook(pushStore, approvalGateway, Duration.ofSeconds(5), null, perms, pushContext)
                .onPreReceive(rp, List.of(cmd));

        assertEquals(ReceiveCommand.Result.NOT_ATTEMPTED, cmd.getResult());
        verifyNoInteractions(perms);
    }

    @Test
    void blockedPush_gatewayTimesOut_commandRejectedAndPushStoreCanceled() throws Exception {
        String recordId = UUID.randomUUID().toString();
        PushContext pushContext = new PushContext();
        pushContext.setValidationRecordId(recordId);
        PushRecord record =
                PushRecord.builder().id(recordId).status(PushStatus.PENDING).build();
        when(pushStore.findById(recordId)).thenReturn(Optional.of(record));
        when(approvalGateway.waitForApproval(eq(recordId), any(), any(), any(Duration.class)))
                .thenReturn(ApprovalResult.TIMED_OUT);

        RevCommit c1 = createCommit("init");
        RevCommit c2 = createCommit("second");
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(c1.getId(), c2.getId(), "refs/heads/main", ReceiveCommand.Type.UPDATE);

        new ApprovalPreReceiveHook(pushStore, approvalGateway, Duration.ofSeconds(5), null, null, pushContext)
                .onPreReceive(rp, List.of(cmd));

        assertEquals(ReceiveCommand.Result.REJECTED_OTHER_REASON, cmd.getResult());
        verify(pushStore).cancel(eq(recordId), any(Attestation.class));
    }

    @Test
    void blockedPush_clientDisconnects_pushStoreCanceledAndCommandRejected() throws Exception {
        String recordId = UUID.randomUUID().toString();
        PushContext pushContext = new PushContext();
        pushContext.setValidationRecordId(recordId);
        PushRecord pending =
                PushRecord.builder().id(recordId).status(PushStatus.PENDING).build();
        // First call: initial fetch; second call: re-fetch inside CANCELED branch
        when(pushStore.findById(recordId)).thenReturn(Optional.of(pending));
        when(approvalGateway.waitForApproval(eq(recordId), any(), any(), any(Duration.class)))
                .thenReturn(ApprovalResult.CANCELED);

        RevCommit c1 = createCommit("init");
        RevCommit c2 = createCommit("second");
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(c1.getId(), c2.getId(), "refs/heads/main", ReceiveCommand.Type.UPDATE);

        new ApprovalPreReceiveHook(pushStore, approvalGateway, Duration.ofSeconds(5), null, null, pushContext)
                .onPreReceive(rp, List.of(cmd));

        assertEquals(ReceiveCommand.Result.REJECTED_OTHER_REASON, cmd.getResult());
        verify(pushStore).cancel(eq(recordId), any(Attestation.class));
    }

    @Test
    void blockedPush_reviewerCancels_pushStoreAlreadyCanceled_noDuplicateCancel() throws Exception {
        String recordId = UUID.randomUUID().toString();
        PushContext pushContext = new PushContext();
        pushContext.setValidationRecordId(recordId);
        PushRecord pending =
                PushRecord.builder().id(recordId).status(PushStatus.PENDING).build();
        PushRecord alreadyCanceled =
                PushRecord.builder().id(recordId).status(PushStatus.CANCELED).build();
        // First call returns PENDING (hook start), second call returns CANCELED (re-fetch in CANCELED branch)
        when(pushStore.findById(recordId)).thenReturn(Optional.of(pending)).thenReturn(Optional.of(alreadyCanceled));
        when(approvalGateway.waitForApproval(eq(recordId), any(), any(), any(Duration.class)))
                .thenReturn(ApprovalResult.CANCELED);

        RevCommit c1 = createCommit("init");
        RevCommit c2 = createCommit("second");
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(c1.getId(), c2.getId(), "refs/heads/main", ReceiveCommand.Type.UPDATE);

        new ApprovalPreReceiveHook(pushStore, approvalGateway, Duration.ofSeconds(5), null, null, pushContext)
                .onPreReceive(rp, List.of(cmd));

        assertEquals(ReceiveCommand.Result.REJECTED_OTHER_REASON, cmd.getResult());
        verify(pushStore, never()).cancel(any(), any());
    }
}
