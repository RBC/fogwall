package com.rbc.fogwall.db.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.PushStoreFactory;
import com.rbc.fogwall.db.model.*;
import com.rbc.fogwall.db.model.PushSummary;
import com.rbc.fogwall.db.model.StepStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link JdbcPushStore} backed by an H2 in-memory database.
 *
 * <p>Each test gets its own isolated H2 database (unique name) to prevent state leakage between tests.
 *
 * <p>Exercises the full SQL path: schema initialization, INSERT, SELECT, UPDATE, DELETE, and relationship tables
 * (steps, commits, attestations).
 */
class JdbcPushStoreIntegrationTest {

    PushStore store;

    @BeforeEach
    void setUp() {
        // Unique DB name per test so H2 in-memory instances are fully isolated
        store = PushStoreFactory.h2InMemory("test-" + UUID.randomUUID());
    }

    // ---- helpers ----

    private static PushRecord record(String commitTo, String branch, String repoName) {
        return PushRecord.builder()
                .commitTo(commitTo)
                .branch(branch)
                .repoName(repoName)
                .user("dev")
                .authorEmail("dev@example.com")
                .build();
    }

    private static PushRecord recordAt(String commitTo, Instant timestamp) {
        return PushRecord.builder()
                .commitTo(commitTo)
                .branch("refs/heads/main")
                .repoName("repo")
                .user("dev")
                .authorEmail("dev@example.com")
                .timestamp(timestamp)
                .build();
    }

    private static Attestation approvalFor(String pushId) {
        return Attestation.builder()
                .pushId(pushId)
                .type(Attestation.Type.APPROVAL)
                .reviewerUsername("reviewer")
                .reason("LGTM")
                .build();
    }

    // ---- save / findById ----

    @Test
    void saveAndFindById_returnsRecord() {
        PushRecord r = record("abc123", "refs/heads/main", "my-repo");
        store.save(r);

        Optional<PushRecord> found = store.findById(r.getId());

        assertTrue(found.isPresent());
        assertEquals(r.getId(), found.get().getId());
        assertEquals("abc123", found.get().getCommitTo());
        assertEquals("refs/heads/main", found.get().getBranch());
        assertEquals("my-repo", found.get().getRepoName());
    }

    @Test
    void findById_unknownId_returnsEmpty() {
        assertTrue(store.findById("no-such-id").isEmpty());
    }

    // The JDBC store is append-only - each save() INSERTs a new event record.
    // Status transitions (approve/reject/cancel) use UPDATE, not save() - there is no upsert/overwrite contract.

    // ---- delete ----

    @Test
    void delete_removesRecord() {
        PushRecord r = record("abc", "refs/heads/main", "repo");
        store.save(r);
        store.delete(r.getId());

        assertTrue(store.findById(r.getId()).isEmpty());
    }

    @Test
    void delete_nonExistentId_doesNotThrow() {
        assertDoesNotThrow(() -> store.delete("does-not-exist"));
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
        assertEquals("refs/heads/feature", results.get(0).getBranch());
    }

    @Test
    void find_byNewerThan_returnsOnlyRecordsAtOrAfterTheBound() {
        Instant cutoff = Instant.parse("2026-09-05T00:00:00Z");
        store.save(recordAt("old", cutoff.minusSeconds(3600)));
        store.save(recordAt("new", cutoff.plusSeconds(3600)));

        List<PushRecord> results =
                store.find(PushQuery.builder().newerThan(cutoff).build());

        assertEquals(1, results.size());
        assertEquals("new", results.get(0).getCommitTo());
    }

    @Test
    void find_byDateWindow_intersectsNewerThanAndOlderThan() {
        store.save(recordAt("before", Instant.parse("2026-09-01T00:00:00Z")));
        store.save(recordAt("inside", Instant.parse("2026-09-05T00:00:00Z")));
        store.save(recordAt("after", Instant.parse("2026-09-10T00:00:00Z")));

        List<PushRecord> results = store.find(PushQuery.builder()
                .newerThan(Instant.parse("2026-09-03T00:00:00Z"))
                .olderThan(Instant.parse("2026-09-08T00:00:00Z"))
                .build());

        assertEquals(1, results.size());
        assertEquals("inside", results.get(0).getCommitTo());
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

    // ---- steps persistence ----

    @Test
    void save_withSteps_stepsRoundTripCorrectly() {
        PushRecord r = record("abc", "refs/heads/main", "repo");
        PushStep pass = PushStep.builder()
                .pushId(r.getId())
                .stepName("author-email")
                .stepOrder(2100)
                .status(StepStatus.PASS)
                .build();
        PushStep fail = PushStep.builder()
                .pushId(r.getId())
                .stepName("commit-message")
                .stepOrder(2200)
                .status(StepStatus.FAIL)
                .errorMessage("contains WIP")
                .content("blocked term: \"WIP\"")
                .build();
        r.setSteps(List.of(pass, fail));
        store.save(r);

        PushRecord loaded = store.findById(r.getId()).orElseThrow();

        assertEquals(2, loaded.getSteps().size());
        PushStep loadedPass = loaded.getSteps().stream()
                .filter(s -> s.getStepName().equals("author-email"))
                .findFirst()
                .orElseThrow();
        assertEquals(StepStatus.PASS, loadedPass.getStatus());

        PushStep loadedFail = loaded.getSteps().stream()
                .filter(s -> s.getStepName().equals("commit-message"))
                .findFirst()
                .orElseThrow();
        assertEquals(StepStatus.FAIL, loadedFail.getStatus());
        assertEquals("contains WIP", loadedFail.getErrorMessage());
    }

    // ---- attestation persistence ----

    @Test
    void approve_attestationPersistedAndReadable() {
        PushRecord r = record("abc", "refs/heads/main", "repo");
        store.save(r);

        Attestation att = Attestation.builder()
                .pushId(r.getId())
                .type(Attestation.Type.APPROVAL)
                .reviewerUsername("alice")
                .reason("Reviewed and approved")
                .build();
        store.approve(r.getId(), att);

        PushRecord loaded = store.findById(r.getId()).orElseThrow();
        assertNotNull(loaded.getAttestation(), "attestation should be persisted");
        assertEquals("alice", loaded.getAttestation().getReviewerUsername());
        assertEquals(Attestation.Type.APPROVAL, loaded.getAttestation().getType());
    }

    // ---- multiple saves ----

    @Test
    void multipleSaves_allVisible() {
        for (int i = 0; i < 20; i++) {
            store.save(record("commit" + i, "refs/heads/main", "repo"));
        }

        List<PushRecord> all = store.find(PushQuery.builder().limit(100).build());

        assertEquals(20, all.size());
    }

    // ---- commits persistence ----

    @Test
    void save_withCommits_commitsRoundTripCorrectly() {
        PushRecord r = record("abc", "refs/heads/main", "repo");
        PushCommit commit = PushCommit.builder()
                .pushId(r.getId())
                .sha("deadbeef")
                .parentSha("cafebabe")
                .authorName("Alice")
                .authorEmail("alice@example.com")
                .committerName("Alice")
                .committerEmail("alice@example.com")
                .message("feat: add thing")
                .signedOffBy(List.of("Alice <alice@example.com>", "Bob <bob@example.com>"))
                .coAuthoredBy(List.of("Carol <carol@example.com>"))
                .build();
        r.setCommits(List.of(commit));
        store.save(r);

        PushRecord loaded = store.findById(r.getId()).orElseThrow();

        assertEquals(1, loaded.getCommits().size());
        PushCommit loadedCommit = loaded.getCommits().get(0);
        assertEquals("deadbeef", loadedCommit.getSha());
        assertEquals("cafebabe", loadedCommit.getParentSha());
        assertEquals("Alice", loadedCommit.getAuthorName());
        assertEquals("alice@example.com", loadedCommit.getAuthorEmail());
        assertEquals("feat: add thing", loadedCommit.getMessage());
        assertEquals(List.of("Alice <alice@example.com>", "Bob <bob@example.com>"), loadedCommit.getSignedOffBy());
        assertEquals(List.of("Carol <carol@example.com>"), loadedCommit.getCoAuthoredBy());
    }

    // ---- find with search ----

    @Test
    void find_bySearch_matchesProjectAndRepoName() {
        PushRecord r1 = PushRecord.builder()
                .project("RBC")
                .repoName("fogwall")
                .commitTo("a")
                .branch("refs/heads/main")
                .build();
        PushRecord r2 = PushRecord.builder()
                .project("acme")
                .repoName("widget-service")
                .commitTo("b")
                .branch("refs/heads/main")
                .build();
        store.save(r1);
        store.save(r2);

        List<PushRecord> results = store.find(PushQuery.builder().search("RBC").build());
        assertEquals(1, results.size());
        assertEquals("RBC", results.get(0).getProject());

        List<PushRecord> byRepo =
                store.find(PushQuery.builder().search("widget").build());
        assertEquals(1, byRepo.size());
        assertEquals("widget-service", byRepo.get(0).getRepoName());
    }

    // ---- findSummaries ----

    @Test
    void findSummaries_returnsProjectionWithoutChildCollections() {
        PushRecord saved = PushRecord.builder()
                .commitTo("abc123")
                .branch("refs/heads/main")
                .repoName("repo")
                .project("acme")
                .upstreamUrl("https://github.com/acme/repo.git")
                .author("Alice")
                .user("alice")
                .resolvedUser("alice")
                .status(PushStatus.FORWARDED)
                .build();
        store.save(saved);

        List<PushSummary> summaries = store.findSummaries(PushQuery.builder().build());

        assertEquals(1, summaries.size());
        PushSummary s = summaries.get(0);
        assertEquals(saved.getId(), s.getId());
        assertEquals(PushStatus.FORWARDED, s.getStatus());
        assertEquals("https://github.com/acme/repo.git", s.getUpstreamUrl());
        assertEquals("refs/heads/main", s.getBranch());
        assertEquals("abc123", s.getCommitTo());
        assertEquals("Alice", s.getAuthor());
        assertEquals("alice", s.getUser());
        assertEquals("alice", s.getResolvedUser());
        assertNotNull(s.getTimestamp());
    }

    @Test
    void findSummaries_filtersApplied() {
        store.save(record("a", "refs/heads/main", "repoA"));
        store.save(record("b", "refs/heads/main", "repoB"));

        List<PushSummary> results =
                store.findSummaries(PushQuery.builder().repoName("repoA").build());

        assertEquals(1, results.size());
        assertEquals("repoA", results.get(0).getRepoName());
    }

    // ---- forwarding claim ----

    private static final Duration CLAIM_TTL = Duration.ofMinutes(15);

    private String approvedDeferred() {
        PushRecord r = record("abc", "refs/heads/main", "repo");
        r.setStatus(PushStatus.PENDING);
        r.setDeferred(true);
        store.save(r);
        store.approve(r.getId(), approvalFor(r.getId()));
        return r.getId();
    }

    @Test
    void claimForward_approvedDeferred_grantsOneClaimant() {
        String id = approvedDeferred();
        Instant now = Instant.now();

        assertTrue(store.claimForward(id, "instance-a", now, CLAIM_TTL, false));
        assertFalse(store.claimForward(id, "instance-b", now, CLAIM_TTL, false));
    }

    @Test
    void claimForward_expiredClaim_canBeTakenOver() {
        String id = approvedDeferred();
        Instant now = Instant.now();
        store.claimForward(id, "instance-a", now, CLAIM_TTL, false);

        assertTrue(store.claimForward(id, "instance-b", now.plus(CLAIM_TTL).plusSeconds(1), CLAIM_TTL, false));
        assertFalse(store.completeForward(id, "instance-a", PushStatus.FORWARDED, null));
        assertTrue(store.completeForward(id, "instance-b", PushStatus.FORWARDED, null));
    }

    @Test
    void claimForward_notDeferred_refused() {
        PushRecord r = record("abc", "refs/heads/main", "repo");
        r.setStatus(PushStatus.PENDING);
        store.save(r);
        store.approve(r.getId(), approvalFor(r.getId()));

        assertFalse(store.claimForward(r.getId(), "instance-a", Instant.now(), CLAIM_TTL, false));
    }

    @Test
    void claimForward_pending_refused() {
        PushRecord r = record("abc", "refs/heads/main", "repo");
        r.setStatus(PushStatus.PENDING);
        r.setDeferred(true);
        store.save(r);

        assertFalse(store.claimForward(r.getId(), "instance-a", Instant.now(), CLAIM_TTL, false));
    }

    @Test
    void claimForward_failedForward_claimableOnlyWhenRetrying() {
        String id = approvedDeferred();
        store.claimForward(id, "instance-a", Instant.now(), CLAIM_TTL, false);
        store.completeForward(id, "instance-a", PushStatus.ERROR, "upstream refused");

        assertFalse(store.claimForward(id, "instance-a", Instant.now(), CLAIM_TTL, false));
        assertTrue(store.claimForward(id, "instance-a", Instant.now(), CLAIM_TTL, true));
    }

    @Test
    void completeForward_recordsOutcomeAndReleasesClaim() {
        String id = approvedDeferred();
        store.claimForward(id, "instance-a", Instant.now(), CLAIM_TTL, false);

        assertTrue(store.completeForward(id, "instance-a", PushStatus.ERROR, "upstream refused"));

        PushRecord after = store.findById(id).orElseThrow();
        assertEquals(PushStatus.ERROR, after.getStatus());
        assertEquals("upstream refused", after.getErrorMessage());
        assertNotNull(after.getForwardedAt());
        assertTrue(after.isDeferred());
        assertTrue(store.claimForward(id, "instance-b", Instant.now(), CLAIM_TTL, true));
    }

    @Test
    void completeForward_withoutClaim_refused() {
        String id = approvedDeferred();

        assertFalse(store.completeForward(id, "instance-a", PushStatus.FORWARDED, null));
        assertEquals(PushStatus.APPROVED, store.findById(id).orElseThrow().getStatus());
    }

    @Test
    void findForwardable_returnsApprovedDeferredWithoutLiveClaim() {
        String unclaimed = approvedDeferred();
        String claimed = approvedDeferred();
        Instant now = Instant.now();
        store.claimForward(claimed, "instance-a", now, CLAIM_TTL, false);
        PushRecord pending = record("abc", "refs/heads/main", "repo");
        pending.setStatus(PushStatus.PENDING);
        pending.setDeferred(true);
        store.save(pending);

        assertEquals(List.of(unclaimed), store.findForwardable(now, 10));
        assertEquals(
                Set.of(unclaimed, claimed),
                Set.copyOf(store.findForwardable(now.plus(CLAIM_TTL).plusSeconds(1), 10)));
    }

    // ---- initialize idempotency ----

    @Test
    void initialize_calledTwice_doesNotThrow() {
        assertDoesNotThrow(store::initialize);
    }
}
