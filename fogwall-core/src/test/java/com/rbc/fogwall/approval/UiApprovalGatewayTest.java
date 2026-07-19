package com.rbc.fogwall.approval;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.memory.InMemoryPushStore;
import com.rbc.fogwall.db.model.Attestation;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UiApprovalGatewayTest {

    private InMemoryPushStore pushStore;
    private UiApprovalGateway gateway;

    @BeforeEach
    void setUp() {
        pushStore = new InMemoryPushStore();
        gateway = new UiApprovalGateway(pushStore);
    }

    private PushRecord blockedRecord() {
        PushRecord r = PushRecord.builder().build();
        r.setStatus(PushStatus.PENDING);
        pushStore.save(r);
        return r;
    }

    private Attestation dummyAttestation(String pushId) {
        return Attestation.builder()
                .pushId(pushId)
                .type(Attestation.Type.APPROVAL)
                .reviewerUsername("reviewer")
                .build();
    }

    // ---- immediate terminal status ----

    @Test
    void returnsApproved_whenRecordAlreadyApproved() {
        PushRecord r = PushRecord.builder().build();
        r.setStatus(PushStatus.APPROVED);
        pushStore.save(r);

        ApprovalResult result = gateway.waitForApproval(
                r.getId(), msg -> {}, ClientLivenessCheck.alwaysConnected(), Duration.ofSeconds(10));

        assertEquals(ApprovalResult.APPROVED, result);
    }

    @Test
    void returnsRejected_whenRecordAlreadyRejected() {
        PushRecord r = PushRecord.builder().build();
        r.setStatus(PushStatus.REJECTED);
        pushStore.save(r);

        ApprovalResult result = gateway.waitForApproval(
                r.getId(), msg -> {}, ClientLivenessCheck.alwaysConnected(), Duration.ofSeconds(10));

        assertEquals(ApprovalResult.REJECTED, result);
    }

    @Test
    void returnsCanceled_whenRecordAlreadyCanceled() {
        PushRecord r = PushRecord.builder().build();
        r.setStatus(PushStatus.CANCELED);
        pushStore.save(r);

        ApprovalResult result = gateway.waitForApproval(
                r.getId(), msg -> {}, ClientLivenessCheck.alwaysConnected(), Duration.ofSeconds(10));

        assertEquals(ApprovalResult.CANCELED, result);
    }

    @Test
    void returnsTimedOut_whenPushIdNotFound() {
        // Non-existent record - should time out immediately since no record ever becomes terminal
        ApprovalResult result = gateway.waitForApproval(
                "no-such-id", msg -> {}, ClientLivenessCheck.alwaysConnected(), Duration.ofMillis(50));

        assertEquals(ApprovalResult.TIMED_OUT, result);
    }

    @Test
    void returnsTimedOut_whenRecordStaysBlocked() {
        PushRecord r = blockedRecord();

        ApprovalResult result = gateway.waitForApproval(
                r.getId(), msg -> {}, ClientLivenessCheck.alwaysConnected(), Duration.ofMillis(60));

        assertEquals(ApprovalResult.TIMED_OUT, result);
    }

    // ---- async approval ----

    @Test
    void returnsApproved_afterAsyncApproval() throws Exception {
        PushRecord r = blockedRecord();
        CountDownLatch latch = new CountDownLatch(1);

        var executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> {
                try {
                    latch.await(2, TimeUnit.SECONDS);
                    pushStore.approve(r.getId(), dummyAttestation(r.getId()));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            latch.countDown();

            ApprovalResult result = gateway.waitForApproval(
                    r.getId(), msg -> {}, ClientLivenessCheck.alwaysConnected(), Duration.ofSeconds(10));

            assertEquals(ApprovalResult.APPROVED, result);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void returnsRejected_afterAsyncRejection() throws Exception {
        PushRecord r = blockedRecord();
        CountDownLatch latch = new CountDownLatch(1);

        var executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> {
                try {
                    latch.await(2, TimeUnit.SECONDS);
                    Attestation attestation = Attestation.builder()
                            .pushId(r.getId())
                            .type(Attestation.Type.REJECTION)
                            .reviewerUsername("reviewer")
                            .reason("not acceptable")
                            .build();
                    pushStore.reject(r.getId(), attestation);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            latch.countDown();

            ApprovalResult result = gateway.waitForApproval(
                    r.getId(), msg -> {}, ClientLivenessCheck.alwaysConnected(), Duration.ofSeconds(10));

            assertEquals(ApprovalResult.REJECTED, result);
        } finally {
            executor.shutdownNow();
        }
    }

    // ---- heartbeat messages ----

    @Test
    void sendsProgressMessages_whileWaiting() {
        PushRecord r = blockedRecord();
        List<String> messages = new ArrayList<>();

        // Short timeout with multiple poll intervals - should capture at least one progress message
        gateway.waitForApproval(r.getId(), messages::add, ClientLivenessCheck.alwaysConnected(), Duration.ofMillis(60));

        // After timeout, at least one heartbeat was sent
        assertFalse(messages.isEmpty(), "Expected at least one heartbeat message during polling");
        assertTrue(
                messages.stream().anyMatch(m -> m.contains("Awaiting review")),
                "Progress message should mention awaiting review");
    }

    // ---- client disconnect ----

    @Test
    void returnsCanceled_whenProgressSenderThrowsClientDisconnectedException() {
        PushRecord r = blockedRecord();

        ProgressSender disconnectingSender = msg -> {
            throw new ClientDisconnectedException(new java.io.IOException("Broken pipe"));
        };

        ApprovalResult result = gateway.waitForApproval(
                r.getId(), disconnectingSender, ClientLivenessCheck.alwaysConnected(), Duration.ofSeconds(10));

        assertEquals(ApprovalResult.CANCELED, result);
    }

    @Test
    void returnsCanceled_whenLivenessCheckIsFalseBeforeFirstPoll() {
        PushRecord r = blockedRecord();
        List<String> messages = new ArrayList<>();

        ApprovalResult result = gateway.waitForApproval(r.getId(), messages::add, () -> false, Duration.ofSeconds(10));

        assertEquals(ApprovalResult.CANCELED, result);
        assertTrue(messages.isEmpty(), "No progress message should be sent once the client is already gone");
    }

    @Test
    void returnsCanceled_whenLivenessCheckFlipsFalseMidWait() {
        PushRecord r = blockedRecord();
        var connected = new AtomicBoolean(true);
        List<String> messages = new ArrayList<>();

        // Simulate the client vanishing partway through the poll loop, ahead of any write attempt.
        ClientLivenessCheck liveness = () -> {
            boolean wasConnected = connected.get();
            connected.set(false);
            return wasConnected;
        };

        ApprovalResult result = gateway.waitForApproval(r.getId(), messages::add, liveness, Duration.ofSeconds(10));

        assertEquals(ApprovalResult.CANCELED, result);
    }

    // ---- interrupt handling ----

    @Test
    void returnsTimedOut_whenInterrupted() throws Exception {
        PushRecord r = blockedRecord();
        var resultHolder = new ApprovalResult[1];
        var thread = new Thread(() -> {
            resultHolder[0] = gateway.waitForApproval(
                    r.getId(), msg -> {}, ClientLivenessCheck.alwaysConnected(), Duration.ofSeconds(30));
        });
        thread.start();

        // Give the thread time to enter the poll loop then interrupt
        Thread.sleep(50);
        thread.interrupt();
        thread.join(2000);

        assertFalse(thread.isAlive(), "Thread should have finished after interrupt");
        assertEquals(ApprovalResult.TIMED_OUT, resultHolder[0]);
    }
}
