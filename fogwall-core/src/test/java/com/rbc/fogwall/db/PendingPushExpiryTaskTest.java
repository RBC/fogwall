package com.rbc.fogwall.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rbc.fogwall.db.model.Attestation;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PendingPushExpiryTaskTest {

    @Test
    void stalePendingRecord_isCanceledWithTimeoutReason() {
        PushStore store = PushStoreFactory.h2InMemory("test-" + UUID.randomUUID());
        PushRecord stale = savePending(store, Instant.now().minus(Duration.ofDays(31)));

        new PendingPushExpiryTask(store, Duration.ofDays(30), Duration.ofMinutes(5)).sweep();

        PushRecord reloaded = store.findById(stale.getId()).orElseThrow();
        assertEquals(PushStatus.CANCELED, reloaded.getStatus());
        assertTrue(reloaded.getAttestation().isAutomated());
        assertTrue(reloaded.getAttestation().getReason().contains("30 days"));
    }

    @Test
    void recentPendingRecord_isNotCanceled() {
        PushStore store = PushStoreFactory.h2InMemory("test-" + UUID.randomUUID());
        PushRecord recent = savePending(store, Instant.now().minus(Duration.ofDays(1)));

        new PendingPushExpiryTask(store, Duration.ofDays(30), Duration.ofMinutes(5)).sweep();

        PushRecord reloaded = store.findById(recent.getId()).orElseThrow();
        assertEquals(PushStatus.PENDING, reloaded.getStatus());
    }

    @Test
    void approvedRecord_isNeverCanceled_regardlessOfAge() {
        PushStore store = PushStoreFactory.h2InMemory("test-" + UUID.randomUUID());
        PushRecord approved = savePending(store, Instant.now().minus(Duration.ofDays(365)));
        store.approve(
                approved.getId(),
                Attestation.builder()
                        .pushId(approved.getId())
                        .type(Attestation.Type.APPROVAL)
                        .reviewerUsername("admin")
                        .build());

        new PendingPushExpiryTask(store, Duration.ofDays(30), Duration.ofMinutes(5)).sweep();

        PushRecord reloaded = store.findById(approved.getId()).orElseThrow();
        assertEquals(PushStatus.APPROVED, reloaded.getStatus());
    }

    @Test
    void sweepFailure_isCaughtAndDoesNotPropagate() {
        PushStore failingStore = (PushStore) java.lang.reflect.Proxy.newProxyInstance(
                PushStore.class.getClassLoader(), new Class<?>[] {PushStore.class}, (proxy, method, args) -> {
                    if (method.getName().equals("find")) {
                        throw new RuntimeException("boom");
                    }
                    return null;
                });

        new PendingPushExpiryTask(failingStore, Duration.ofDays(30), Duration.ofMinutes(5)).sweep();
    }

    private static PushRecord savePending(PushStore store, Instant timestamp) {
        PushRecord record = PushRecord.builder()
                .commitTo("sha-" + UUID.randomUUID())
                .branch("refs/heads/main")
                .provider("github")
                .project("owner")
                .repoName("my-repo")
                .status(PushStatus.PENDING)
                .timestamp(timestamp)
                .build();
        store.save(record);
        return record;
    }
}
