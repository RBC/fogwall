package com.rbc.fogwall.approval;

import java.time.Duration;

/**
 * Abstraction for the human approval gate that can block a push pending review. The default implementation polls the
 * push store (UI-based approval). Custom implementations can integrate with external systems (e.g., ServiceNow).
 */
public interface ApprovalGateway {
    /**
     * Wait until the push is approved, rejected, or canceled, or until timeout expires. Implementations should send
     * heartbeat progress messages to keep the git client alive, and should treat a {@code false} result from
     * {@code liveness} as an immediate {@link ApprovalResult#CANCELED}.
     */
    ApprovalResult waitForApproval(
            String pushId, ProgressSender progress, ClientLivenessCheck liveness, Duration timeout);

    /**
     * Returns {@code true} if this gateway approves pushes immediately without requiring human review.
     *
     * <p>Used by the transparent-proxy finalizer to decide whether to forward the push on the first attempt or block it
     * pending a re-push after dashboard approval.
     */
    default boolean approvesImmediately() {
        return false;
    }
}
