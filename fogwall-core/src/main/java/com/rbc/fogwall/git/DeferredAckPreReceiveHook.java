package com.rbc.fogwall.git;

import static com.rbc.fogwall.git.GitClientUtils.AnsiColor.*;
import static com.rbc.fogwall.git.GitClientUtils.SymbolCodes.*;
import static com.rbc.fogwall.git.GitClientUtils.color;
import static com.rbc.fogwall.git.GitClientUtils.sym;

import com.rbc.fogwall.approval.ApprovalGateway;
import com.rbc.fogwall.approval.ClientLivenessCheck;
import com.rbc.fogwall.db.ParkedPushStore;
import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStatus;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.transport.PreReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

/**
 * Takes the place of {@link ApprovalPreReceiveHook} for a parked push: instead of holding the connection open until a
 * reviewer decides, it tells the client the push is queued and reports every ref update as successful. JGit applies
 * only updates still unattempted after pre-receive, so neither the mirror nor upstream moves; the push is forwarded
 * from its stored pack once approved.
 *
 * <p>Fails closed exactly as the approval gate does: without a PENDING push record there is no approval state for the
 * push to wait on, so it is rejected and its stored pack discarded.
 */
@Slf4j
public class DeferredAckPreReceiveHook implements PreReceiveHook {

    private final PushStore pushStore;
    private final ParkedPushStore parkedPushStore;
    private final ApprovalGateway approvalGateway;
    private final Duration approvalTimeout;
    private final String serviceUrl;
    private final PushContext pushContext;

    public DeferredAckPreReceiveHook(
            PushStore pushStore,
            ParkedPushStore parkedPushStore,
            ApprovalGateway approvalGateway,
            Duration approvalTimeout,
            String serviceUrl,
            PushContext pushContext) {
        this.pushStore = pushStore;
        this.parkedPushStore = parkedPushStore;
        this.approvalGateway = approvalGateway;
        this.approvalTimeout = approvalTimeout;
        this.serviceUrl = serviceUrl;
        this.pushContext = pushContext;
    }

    @Override
    public void onPreReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
        String pushId = pushContext.getPushId();
        Optional<PushRecord> pending = Optional.ofNullable(pushContext.getValidationRecordId())
                .flatMap(pushStore::findById)
                .filter(record -> record.getStatus() == PushStatus.PENDING);
        if (pending.isEmpty()) {
            log.error("Parked push {} has no PENDING record - rejecting, approval state cannot be established", pushId);
            discardPack(pushId);
            rp.sendMessage(color(RED, sym(NO_ENTRY) + "  Push blocked - approval state could not be recorded"));
            for (ReceiveCommand cmd : commands) {
                if (cmd.getResult() == ReceiveCommand.Result.NOT_ATTEMPTED) {
                    cmd.setResult(
                            ReceiveCommand.Result.REJECTED_OTHER_REASON,
                            "Approval state unavailable - push record was not created");
                }
            }
            return;
        }

        String recordId = pending.get().getId();
        boolean autoApproval = approvalGateway.approvesImmediately();
        if (autoApproval) {
            approvalGateway.waitForApproval(
                    recordId, msg -> {}, ClientLivenessCheck.alwaysConnected(), approvalTimeout);
        }

        rp.sendMessage(color(GREEN, sym(HEAVY_CHECK_MARK) + "  Push received and queued for review"));
        rp.sendMessage(color(CYAN, sym(KEY) + "  Push ID: " + recordId));
        if (serviceUrl != null && !autoApproval) {
            rp.sendMessage(color(CYAN, "   Review at: " + serviceUrl + "/dashboard/push/" + recordId));
        }
        rp.sendMessage("   fogwall forwards it upstream once approved; the push record shows the outcome.");

        for (ReceiveCommand cmd : commands) {
            if (cmd.getResult() == ReceiveCommand.Result.NOT_ATTEMPTED) {
                cmd.setResult(ReceiveCommand.Result.OK);
            }
        }
    }

    private void discardPack(String pushId) {
        if (pushId == null) {
            return;
        }
        try {
            parkedPushStore.delete(pushId);
        } catch (RuntimeException e) {
            log.warn("Could not discard the stored pack of rejected push {}: {}", pushId, e.getMessage());
        }
    }
}
