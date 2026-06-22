package com.rbc.fogwall.approval;

import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.model.PushStatus;
import java.time.Duration;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Default approval gateway that waits for a human reviewer to approve or reject via the fogwall UI/API. Polls the push
 * store every 5 seconds and sends heartbeat sideband messages to keep the git client connection alive.
 */
@Slf4j
public class UiApprovalGateway implements ApprovalGateway {

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Set<PushStatus> TERMINAL_STATUSES =
            Set.of(PushStatus.APPROVED, PushStatus.REJECTED, PushStatus.CANCELED);

    private final PushStore pushStore;

    public UiApprovalGateway(PushStore pushStore) {
        this.pushStore = pushStore;
    }

    @Override
    public ApprovalResult waitForApproval(String pushId, ProgressSender progress, Duration timeout) {
        long deadlineMs = System.currentTimeMillis() + timeout.toMillis();
        long elapsedSecs = 0;

        while (System.currentTimeMillis() < deadlineMs) {
            var record = pushStore.findById(pushId).orElse(null);
            if (record != null && TERMINAL_STATUSES.contains(record.getStatus())) {
                return switch (record.getStatus()) {
                    case APPROVED -> ApprovalResult.APPROVED;
                    case REJECTED -> ApprovalResult.REJECTED;
                    case CANCELED -> ApprovalResult.CANCELED;
                    default -> ApprovalResult.TIMED_OUT;
                };
            }

            try {
                //noinspection BusyWait
                Thread.sleep(POLL_INTERVAL.toMillis());
                elapsedSecs += POLL_INTERVAL.toSeconds();
                long remainingSecs = (deadlineMs - System.currentTimeMillis()) / 1000;
                progress.send(
                        String.format("Awaiting review... (%ds elapsed, ~%ds remaining)", elapsedSecs, remainingSecs));
            } catch (ClientDisconnectedException e) {
                log.debug("Client disconnected during approval wait for push {}", pushId);
                return ApprovalResult.CANCELED;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ApprovalResult.TIMED_OUT;
            }
        }

        return ApprovalResult.TIMED_OUT;
    }
}
