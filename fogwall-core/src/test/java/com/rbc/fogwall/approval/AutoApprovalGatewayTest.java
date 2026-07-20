package com.rbc.fogwall.approval;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.PushStoreFactory;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AutoApprovalGatewayTest {

    private PushStore pushStore;
    private AutoApprovalGateway gateway;

    @BeforeEach
    void setUp() {
        pushStore = PushStoreFactory.h2InMemory("test-" + UUID.randomUUID());
        gateway = new AutoApprovalGateway(pushStore);
    }

    private PushRecord blockedRecord() {
        PushRecord r = PushRecord.builder().build();
        r.setStatus(PushStatus.PENDING);
        pushStore.save(r);
        return r;
    }

    @Test
    void returnsApproved_immediately() {
        PushRecord r = blockedRecord();

        ApprovalResult result = gateway.waitForApproval(
                r.getId(), msg -> {}, ClientLivenessCheck.alwaysConnected(), Duration.ofSeconds(30));

        assertEquals(ApprovalResult.APPROVED, result);
    }

    @Test
    void recordsApprovalInStore() {
        PushRecord r = blockedRecord();

        gateway.waitForApproval(r.getId(), msg -> {}, ClientLivenessCheck.alwaysConnected(), Duration.ofSeconds(30));

        PushRecord updated = pushStore.findById(r.getId()).orElseThrow();
        assertEquals(PushStatus.APPROVED, updated.getStatus());
    }

    @Test
    void attestation_isMarkedAutomated() {
        PushRecord r = blockedRecord();

        gateway.waitForApproval(r.getId(), msg -> {}, ClientLivenessCheck.alwaysConnected(), Duration.ofSeconds(30));

        PushRecord updated = pushStore.findById(r.getId()).orElseThrow();
        assertNotNull(updated.getAttestation(), "Attestation should be set");
        assertTrue(updated.getAttestation().isAutomated(), "Attestation should be automated");
        assertEquals("auto-approval", updated.getAttestation().getReviewerUsername());
    }

    @Test
    void sendsNoProgressMessages() {
        PushRecord r = blockedRecord();
        List<String> messages = new ArrayList<>();

        gateway.waitForApproval(
                r.getId(), messages::add, ClientLivenessCheck.alwaysConnected(), Duration.ofSeconds(30));

        assertTrue(messages.isEmpty(), "AutoApprovalGateway should not send any progress messages");
    }

    @Test
    void returnsApproved_evenWhenStoreUpdateFails() {
        // Gateway should still return APPROVED if the store throws (e.g. record not found)
        ApprovalResult result = gateway.waitForApproval(
                "no-such-id", msg -> {}, ClientLivenessCheck.alwaysConnected(), Duration.ofSeconds(30));

        assertEquals(ApprovalResult.APPROVED, result);
    }
}
