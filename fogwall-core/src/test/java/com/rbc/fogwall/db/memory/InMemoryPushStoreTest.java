package com.rbc.fogwall.db.memory;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.model.Attestation;
import com.rbc.fogwall.db.model.PushQuery;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryPushStoreTest {

    InMemoryPushStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryPushStore();
    }

    private static PushRecord record(String commitTo, String branch, String repoName) {
        return PushRecord.builder()
                .commitTo(commitTo)
                .branch(branch)
                .repoName(repoName)
                .user("dev")
                .authorEmail("dev@example.com")
                .build();
    }

    private static Attestation approvalFor(String pushId) {
        return Attestation.builder()
                .pushId(pushId)
                .type(Attestation.Type.APPROVAL)
                .reviewerUsername("reviewer")
                .build();
    }

    // ---- save / findById ----

    @Test
    void saveAndFindById_returnsRecord() {
        PushRecord r = record("abc", "refs/heads/main", "repo");
        store.save(r);
        Optional<PushRecord> found = store.findById(r.getId());
        assertTrue(found.isPresent());
        assertEquals(r.getId(), found.get().getId());
    }

    @Test
    void findById_unknownId_returnsEmpty() {
        assertTrue(store.findById("does-not-exist").isEmpty());
    }

    @Test
    void save_overwritesExistingRecord() {
        PushRecord r = record("abc", "refs/heads/main", "repo");
        store.save(r);
        r.setUser("updated-user");
        store.save(r);
        assertEquals("updated-user", store.findById(r.getId()).get().getUser());
    }

    // ---- delete ----

    @Test
    void delete_removesRecord() {
        PushRecord r = record("abc", "refs/heads/main", "repo");
        store.save(r);
        store.delete(r.getId());
        assertTrue(store.findById(r.getId()).isEmpty());
    }

    // ---- find with query ----

    @Test
    void find_byStatus_returnsMatchingRecords() {
        PushRecord pending = record("a", "refs/heads/main", "repo");
        PushRecord approved = record("b", "refs/heads/main", "repo");
        store.save(pending);
        store.save(approved);
        store.approve(approved.getId(), approvalFor(approved.getId()));

        List<PushRecord> results =
                store.find(PushQuery.builder().status(PushStatus.APPROVED).build());
        assertEquals(1, results.size());
        assertEquals(approved.getId(), results.get(0).getId());
    }

    @Test
    void find_byRepoName_returnsMatchingRecords() {
        store.save(record("a", "refs/heads/main", "repoA"));
        store.save(record("b", "refs/heads/main", "repoB"));

        List<PushRecord> results =
                store.find(PushQuery.builder().repoName("repoA").build());
        assertEquals(1, results.size());
        assertEquals("repoA", results.get(0).getRepoName());
    }

    @Test
    void find_byBranch_returnsMatchingRecords() {
        store.save(record("a", "refs/heads/feature", "repo"));
        store.save(record("b", "refs/heads/main", "repo"));

        List<PushRecord> results =
                store.find(PushQuery.builder().branch("refs/heads/feature").build());
        assertEquals(1, results.size());
    }

    @Test
    void find_byCommitTo_returnsMatchingRecord() {
        store.save(record("commitXYZ", "refs/heads/main", "repo"));
        store.save(record("commitABC", "refs/heads/main", "repo"));

        List<PushRecord> results =
                store.find(PushQuery.builder().commitTo("commitXYZ").build());
        assertEquals(1, results.size());
        assertEquals("commitXYZ", results.get(0).getCommitTo());
    }

    @Test
    void find_withLimit_returnsAtMostLimitRecords() {
        for (int i = 0; i < 10; i++) {
            store.save(record("commit" + i, "refs/heads/main", "repo"));
        }
        List<PushRecord> results = store.find(PushQuery.builder().limit(3).build());
        assertEquals(3, results.size());
    }

    @Test
    void find_emptyStore_returnsEmptyList() {
        assertTrue(store.find(PushQuery.builder().build()).isEmpty());
    }

    // ---- approve / reject / cancel ----

    @Test
    void approve_changesStatusToApproved() {
        PushRecord r = record("abc", "refs/heads/main", "repo");
        store.save(r);

        PushRecord updated = store.approve(r.getId(), approvalFor(r.getId()));

        assertEquals(PushStatus.APPROVED, updated.getStatus());
        assertEquals(PushStatus.APPROVED, store.findById(r.getId()).get().getStatus());
    }

    @Test
    void reject_changesStatusToRejected() {
        PushRecord r = record("abc", "refs/heads/main", "repo");
        store.save(r);

        Attestation rejection = Attestation.builder()
                .pushId(r.getId())
                .type(Attestation.Type.REJECTION)
                .reviewerUsername("reviewer")
                .reason("Policy violation")
                .build();
        PushRecord updated = store.reject(r.getId(), rejection);

        assertEquals(PushStatus.REJECTED, updated.getStatus());
    }

    @Test
    void cancel_changesStatusToCanceled() {
        PushRecord r = record("abc", "refs/heads/main", "repo");
        store.save(r);

        Attestation cancellation = Attestation.builder()
                .pushId(r.getId())
                .type(Attestation.Type.CANCELLATION)
                .reviewerUsername("dev")
                .build();
        PushRecord updated = store.cancel(r.getId(), cancellation);

        assertEquals(PushStatus.CANCELED, updated.getStatus());
    }

    @Test
    void approve_unknownId_throwsException() {
        assertThrows(Exception.class, () -> store.approve("not-a-real-id", approvalFor("not-a-real-id")));
    }

    // ---- initialize (no-op for in-memory) ----

    @Test
    void initialize_doesNotThrow() {
        assertDoesNotThrow(() -> store.initialize());
    }

    // ---- concurrent safety (basic) ----

    @Test
    void multipleSaves_allVisible() {
        for (int i = 0; i < 20; i++) {
            store.save(record("commit" + i, "refs/heads/main", "repo"));
        }
        List<PushRecord> all = store.find(PushQuery.builder().limit(100).build());
        assertEquals(20, all.size());
    }
}
