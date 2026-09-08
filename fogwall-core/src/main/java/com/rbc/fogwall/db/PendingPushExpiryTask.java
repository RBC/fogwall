package com.rbc.fogwall.db;

import com.rbc.fogwall.db.model.Attestation;
import com.rbc.fogwall.db.model.Attestation.Type;
import com.rbc.fogwall.db.model.PushQuery;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * Cancels PENDING push records that have sat unreviewed for longer than {@code expiryAge}. Only transparent proxy mode
 * needs this: server mode's held request already times out and cancels itself synchronously within the same connection
 * (see {@code ApprovalPreReceiveHook}), but a proxy-mode PENDING record has no held connection to bound — without this
 * sweep it would sit forever unless a human acts on it or a new push to the same branch supersedes it.
 *
 * <p>Runs on a fixed interval rather than a scheduled-per-record timer: candidates are found by querying for PENDING
 * records older than the cutoff, so a record that's expired between sweeps is caught on the next one rather than
 * missed. Cancellation is a state change, not a deletion — the record and its history remain visible to reviewers.
 */
@Slf4j
public class PendingPushExpiryTask {

    private static final Duration DEFAULT_CHECK_INTERVAL = Duration.ofMinutes(5);

    /** Records canceled per sweep before the rest wait for the next one, so one sweep can't run unboundedly long. */
    private static final int MAX_PER_SWEEP = 500;

    private final PushStore pushStore;
    private final Duration expiryAge;
    private final Duration checkInterval;
    private ScheduledExecutorService scheduler;

    public PendingPushExpiryTask(PushStore pushStore, Duration expiryAge) {
        this(pushStore, expiryAge, DEFAULT_CHECK_INTERVAL);
    }

    /**
     * Package-visible so a test can use a short check interval instead of waiting on {@link #DEFAULT_CHECK_INTERVAL}.
     */
    PendingPushExpiryTask(PushStore pushStore, Duration expiryAge, Duration checkInterval) {
        this.pushStore = pushStore;
        this.expiryAge = expiryAge;
        this.checkInterval = checkInterval;
    }

    /** Starts the periodic sweep. */
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("pending-push-expiry").factory());
        scheduler.scheduleAtFixedRate(this::sweep, 0, checkInterval.toSeconds(), TimeUnit.SECONDS);
        log.info("Pending-push expiry sweep started: age={} interval={}", expiryAge, checkInterval);
    }

    /** Stops the sweep. */
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    /** Runs one sweep. Exceptions are caught and logged so a bad query never kills the scheduled task permanently. */
    void sweep() {
        try {
            Instant cutoff = Instant.now().minus(expiryAge);
            List<PushRecord> stale = pushStore.find(PushQuery.builder()
                    .status(PushStatus.PENDING)
                    .olderThan(cutoff)
                    .limit(MAX_PER_SWEEP)
                    .build());
            for (PushRecord record : stale) {
                pushStore.cancel(
                        record.getId(),
                        Attestation.builder()
                                .pushId(record.getId())
                                .type(Type.CANCELLATION)
                                .automated(true)
                                .reason("Pending review timed out after " + expiryAge.toDays() + " days")
                                .build());
            }
            if (!stale.isEmpty()) {
                log.info("Pending-push expiry sweep canceled {} record(s) older than {}", stale.size(), cutoff);
            }
        } catch (Exception e) {
            log.error("Pending-push expiry sweep failed", e);
        }
    }
}
