package com.rbc.fogwall.e2e;

import com.rbc.fogwall.approval.ApprovalGateway;
import com.rbc.fogwall.approval.ApprovalResult;
import com.rbc.fogwall.approval.ClientLivenessCheck;
import com.rbc.fogwall.approval.ProgressSender;
import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.model.Attestation;
import java.time.Duration;

/**
 * Test-only {@link ApprovalGateway} that immediately approves every push without waiting for human input. Used in e2e
 * tests so that valid commits are forwarded without blocking.
 */
class AutoApproveGateway implements ApprovalGateway {

    private final PushStore pushStore;

    AutoApproveGateway(PushStore pushStore) {
        this.pushStore = pushStore;
    }

    @Override
    public ApprovalResult waitForApproval(
            String pushId, ProgressSender progress, ClientLivenessCheck liveness, Duration timeout) {
        pushStore.approve(
                pushId,
                Attestation.builder()
                        .pushId(pushId)
                        .type(Attestation.Type.APPROVAL)
                        .reviewerUsername("e2e-auto-approver")
                        .reason("Automatically approved by e2e test fixture")
                        .automated(true)
                        .build());
        return ApprovalResult.APPROVED;
    }
}
