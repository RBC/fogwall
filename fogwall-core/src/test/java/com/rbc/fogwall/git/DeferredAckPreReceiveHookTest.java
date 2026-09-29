package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.approval.ApprovalGateway;
import com.rbc.fogwall.db.ParkedPushStore;
import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStatus;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The acknowledgement is what tells a client its push succeeded without anything moving, so it must only be given for a
 * push whose PENDING record exists. Without one there is no approval state for the push to wait on.
 */
class DeferredAckPreReceiveHookTest {

    private static final String PUSH_ID = "push-1";

    @TempDir
    Path tempDir;

    private final PushStore pushStore = mock(PushStore.class);
    private final ParkedPushStore parkedPushStore = mock(ParkedPushStore.class);
    private final ApprovalGateway approvalGateway = mock(ApprovalGateway.class);
    private final PushContext pushContext = new PushContext();
    private ReceivePack rp;
    private ReceiveCommand cmd;

    @BeforeEach
    void setUp() throws Exception {
        Repository repo =
                Git.init().setBare(true).setDirectory(tempDir.toFile()).call().getRepository();
        rp = new ReceivePack(repo);
        cmd = new ReceiveCommand(
                ObjectId.zeroId(), ObjectId.fromString("1234567890123456789012345678901234567890"), "refs/heads/b");
        pushContext.setPushId(PUSH_ID);
    }

    private DeferredAckPreReceiveHook hook(String serviceUrl) {
        return new DeferredAckPreReceiveHook(
                pushStore, parkedPushStore, approvalGateway, Duration.ofMinutes(1), serviceUrl, pushContext);
    }

    private void recordWithStatus(PushStatus status) {
        pushContext.setValidationRecordId(PUSH_ID);
        when(pushStore.findById(PUSH_ID))
                .thenReturn(Optional.of(
                        PushRecord.builder().id(PUSH_ID).status(status).build()));
    }

    @Test
    void pendingRecord_acknowledgesWithoutApplying() {
        recordWithStatus(PushStatus.PENDING);

        hook("https://fogwall.example.com").onPreReceive(rp, List.of(cmd));

        assertEquals(ReceiveCommand.Result.OK, cmd.getResult());
        verify(parkedPushStore, never()).delete(anyString());
        verify(approvalGateway, never()).waitForApproval(anyString(), any(), any(), any());
    }

    @Test
    void noRecord_rejectsAndDiscardsThePack() {
        // The persistence hook failed to save the record, so nothing can ever approve this push.
        hook(null).onPreReceive(rp, List.of(cmd));

        assertEquals(ReceiveCommand.Result.REJECTED_OTHER_REASON, cmd.getResult());
        verify(parkedPushStore).delete(PUSH_ID);
    }

    @Test
    void recordMissingFromStore_rejectsAndDiscardsThePack() {
        pushContext.setValidationRecordId(PUSH_ID);
        when(pushStore.findById(PUSH_ID)).thenReturn(Optional.empty());

        hook(null).onPreReceive(rp, List.of(cmd));

        assertEquals(ReceiveCommand.Result.REJECTED_OTHER_REASON, cmd.getResult());
        verify(parkedPushStore).delete(PUSH_ID);
    }

    @Test
    void recordNotPending_rejects() {
        recordWithStatus(PushStatus.REJECTED);

        hook(null).onPreReceive(rp, List.of(cmd));

        assertEquals(ReceiveCommand.Result.REJECTED_OTHER_REASON, cmd.getResult());
        verify(parkedPushStore).delete(PUSH_ID);
    }

    @Test
    void discardFailure_stillRejects() {
        doThrow(new IllegalStateException("database down"))
                .when(parkedPushStore)
                .delete(PUSH_ID);

        hook(null).onPreReceive(rp, List.of(cmd));

        assertEquals(ReceiveCommand.Result.REJECTED_OTHER_REASON, cmd.getResult());
    }

    @Test
    void autoApprovingGateway_approvesOnReceipt() {
        recordWithStatus(PushStatus.PENDING);
        when(approvalGateway.approvesImmediately()).thenReturn(true);

        hook("https://fogwall.example.com").onPreReceive(rp, List.of(cmd));

        verify(approvalGateway).waitForApproval(eq(PUSH_ID), any(), any(), any());
        assertEquals(ReceiveCommand.Result.OK, cmd.getResult());
    }

    @Test
    void alreadyRejectedCommand_isLeftAlone() {
        recordWithStatus(PushStatus.PENDING);
        cmd.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "earlier");

        hook(null).onPreReceive(rp, List.of(cmd));

        assertEquals(ReceiveCommand.Result.REJECTED_OTHER_REASON, cmd.getResult());
        assertEquals("earlier", cmd.getMessage());
    }
}
