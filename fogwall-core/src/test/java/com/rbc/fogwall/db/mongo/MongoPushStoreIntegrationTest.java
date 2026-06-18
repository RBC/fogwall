package com.rbc.fogwall.db.mongo;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.MongoClients;
import com.rbc.fogwall.db.model.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@Tag("integration")
class MongoPushStoreIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    MongoPushStore store;

    @BeforeEach
    void setUp() {
        store = new MongoPushStore(
                MongoClients.create(MONGO.getConnectionString()),
                "testdb_" + UUID.randomUUID().toString().replace("-", ""));
        store.initialize();
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    private PushRecord record(String id) {
        return PushRecord.builder()
                .id(id)
                .url("https://github.com/org/repo")
                .project("org")
                .repoName("repo")
                .branch("refs/heads/main")
                .commitFrom("abc")
                .commitTo("def")
                .message("feat: something")
                .author("Dev")
                .authorEmail("dev@example.com")
                .status(PushStatus.RECEIVED)
                .timestamp(Instant.now())
                .build();
    }

    @Test
    void saveAndFindById_returnsRecord() {
        String id = UUID.randomUUID().toString();
        store.save(record(id));

        var found = store.findById(id);
        assertTrue(found.isPresent());
        assertEquals(id, found.get().getId());
        assertEquals("repo", found.get().getRepoName());
    }

    @Test
    void findById_unknownId_returnsEmpty() {
        assertTrue(store.findById(UUID.randomUUID().toString()).isEmpty());
    }

    @Test
    void delete_removesRecord() {
        String id = UUID.randomUUID().toString();
        store.save(record(id));
        store.delete(id);
        assertTrue(store.findById(id).isEmpty());
    }

    @Test
    void delete_nonExistentId_doesNotThrow() {
        assertDoesNotThrow(() -> store.delete(UUID.randomUUID().toString()));
    }

    @Test
    void find_byStatus_returnsMatchingRecords() {
        String id1 = UUID.randomUUID().toString();
        String id2 = UUID.randomUUID().toString();
        store.save(record(id1)); // RECEIVED
        PushRecord blocked = PushRecord.builder()
                .id(id2)
                .status(PushStatus.PENDING)
                .repoName("repo")
                .project("org")
                .branch("refs/heads/main")
                .timestamp(Instant.now())
                .build();
        store.save(blocked);

        var results = store.find(PushQuery.builder().status(PushStatus.RECEIVED).build());
        assertTrue(results.stream().anyMatch(r -> r.getId().equals(id1)));
        assertTrue(results.stream().noneMatch(r -> r.getId().equals(id2)));
    }

    @Test
    void find_byRepoName_returnsMatchingRecords() {
        String id = UUID.randomUUID().toString();
        store.save(record(id));

        var results = store.find(PushQuery.builder().repoName("repo").build());
        assertTrue(results.stream().anyMatch(r -> r.getId().equals(id)));
    }

    @Test
    void find_byBranch_returnsMatchingRecords() {
        String id = UUID.randomUUID().toString();
        store.save(record(id));

        var results = store.find(PushQuery.builder().branch("refs/heads/main").build());
        assertTrue(results.stream().anyMatch(r -> r.getId().equals(id)));
    }

    @Test
    void find_withLimit_returnsAtMostLimitRecords() {
        for (int i = 0; i < 5; i++) {
            store.save(record(UUID.randomUUID().toString()));
        }
        var results = store.find(PushQuery.builder().limit(3).build());
        assertTrue(results.size() <= 3);
    }

    @Test
    void find_emptyStore_returnsEmptyList() {
        var results = store.find(
                PushQuery.builder().repoName("nonexistent-" + UUID.randomUUID()).build());
        assertTrue(results.isEmpty());
    }

    @Test
    void approve_changesStatusToApproved() {
        String id = UUID.randomUUID().toString();
        store.save(record(id));

        Attestation att = Attestation.builder()
                .type(Attestation.Type.APPROVAL)
                .reviewerUsername("reviewer")
                .timestamp(Instant.now())
                .build();
        PushRecord updated = store.approve(id, att);

        assertEquals(PushStatus.APPROVED, updated.getStatus());
        assertNotNull(updated.getAttestation());
        assertEquals("reviewer", updated.getAttestation().getReviewerUsername());
    }

    @Test
    void reject_changesStatusToRejected() {
        String id = UUID.randomUUID().toString();
        store.save(record(id));

        Attestation att = Attestation.builder()
                .type(Attestation.Type.REJECTION)
                .reviewerUsername("reviewer")
                .reason("policy violation")
                .timestamp(Instant.now())
                .build();
        PushRecord updated = store.reject(id, att);

        assertEquals(PushStatus.REJECTED, updated.getStatus());
        assertEquals("policy violation", updated.getAttestation().getReason());
    }

    @Test
    void cancel_changesStatusToCanceled() {
        String id = UUID.randomUUID().toString();
        store.save(record(id));

        PushRecord updated = store.cancel(id, null);
        assertEquals(PushStatus.CANCELED, updated.getStatus());
    }

    @Test
    void approve_unknownId_throwsException() {
        Attestation att = Attestation.builder()
                .type(Attestation.Type.APPROVAL)
                .reviewerUsername("reviewer")
                .timestamp(Instant.now())
                .build();
        assertThrows(
                IllegalArgumentException.class,
                () -> store.approve(UUID.randomUUID().toString(), att));
    }

    @Test
    void save_withSteps_stepsRoundTripCorrectly() {
        String id = UUID.randomUUID().toString();
        PushRecord r = record(id);
        r.setSteps(List.of(PushStep.builder()
                .id(UUID.randomUUID().toString())
                .stepName("checkAuthor")
                .stepOrder(1000)
                .status(StepStatus.PASS)
                .logs(List.of("Author OK"))
                .timestamp(Instant.now())
                .build()));
        store.save(r);

        PushRecord found = store.findById(id).orElseThrow();
        assertNotNull(found.getSteps());
        assertEquals(1, found.getSteps().size());
        assertEquals("checkAuthor", found.getSteps().get(0).getStepName());
        assertEquals(StepStatus.PASS, found.getSteps().get(0).getStatus());
    }

    @Test
    void approve_attestationPersistedAndReadable() {
        String id = UUID.randomUUID().toString();
        store.save(record(id));

        Attestation att = Attestation.builder()
                .type(Attestation.Type.APPROVAL)
                .reviewerUsername("alice")
                .reviewerEmail("alice@example.com")
                .reason("looks good")
                .automated(false)
                .timestamp(Instant.now())
                .build();
        store.approve(id, att);

        PushRecord found = store.findById(id).orElseThrow();
        assertEquals("alice", found.getAttestation().getReviewerUsername());
        assertEquals("looks good", found.getAttestation().getReason());
    }

    @Test
    void multipleSaves_allVisible() {
        for (int i = 0; i < 5; i++) {
            store.save(record(UUID.randomUUID().toString()));
        }
        var results = store.find(PushQuery.builder().repoName("repo").build());
        assertTrue(results.size() >= 5);
    }

    @Test
    void initialize_calledTwice_doesNotThrow() {
        assertDoesNotThrow(() -> store.initialize());
    }

    @Test
    void save_withProvider_providerRoundTrips() {
        String id = UUID.randomUUID().toString();
        PushRecord r = PushRecord.builder()
                .id(id)
                .provider("github")
                .project("org")
                .repoName("repo")
                .branch("refs/heads/main")
                .status(PushStatus.RECEIVED)
                .timestamp(Instant.now())
                .build();
        store.save(r);

        PushRecord found = store.findById(id).orElseThrow();
        assertEquals("github", found.getProvider());
    }

    @Test
    void save_withCommits_commitsRoundTripCompletely() {
        String id = UUID.randomUUID().toString();
        PushRecord r = record(id);
        r.setCommits(List.of(PushCommit.builder()
                .pushId(id)
                .sha("abc123")
                .parentSha("000000")
                .authorName("Dev")
                .authorEmail("dev@example.com")
                .committerName("Dev")
                .committerEmail("dev@example.com")
                .message("feat: add thing\n\nSigned-off-by: Dev <dev@example.com>")
                .commitDate(Instant.now())
                .signedOffBy(List.of("Dev <dev@example.com>"))
                .build()));
        store.save(r);

        PushRecord found = store.findById(id).orElseThrow();
        assertEquals(1, found.getCommits().size());
        PushCommit c = found.getCommits().get(0);
        assertEquals(id, c.getPushId());
        assertEquals("abc123", c.getSha());
        assertEquals(List.of("Dev <dev@example.com>"), c.getSignedOffBy());
    }

    @Test
    void approve_selfApprovalAndAnswersRoundTrip() {
        String id = UUID.randomUUID().toString();
        store.save(record(id));

        Attestation att = Attestation.builder()
                .pushId(id)
                .type(Attestation.Type.APPROVAL)
                .reviewerUsername("admin")
                .automated(false)
                .selfApproval(true)
                .answers(Map.of("q1", "yes", "q2", "text answer"))
                .timestamp(Instant.now())
                .build();
        store.approve(id, att);

        PushRecord found = store.findById(id).orElseThrow();
        Attestation saved = found.getAttestation();
        assertTrue(saved.isSelfApproval());
        assertNotNull(saved.getAnswers());
        assertEquals("yes", saved.getAnswers().get("q1"));
        assertEquals("text answer", saved.getAnswers().get("q2"));
    }

    @Test
    void find_byCommitTo_returnsMatchingRecords() {
        String id = UUID.randomUUID().toString();
        store.save(record(id)); // commitTo = "def"

        var results = store.find(PushQuery.builder().commitTo("def").build());
        assertTrue(results.stream().anyMatch(r -> r.getId().equals(id)));

        var noResults = store.find(PushQuery.builder().commitTo("zzz").build());
        assertTrue(noResults.stream().noneMatch(r -> r.getId().equals(id)));
    }

    @Test
    void find_bySearch_matchesProviderProjectAndRepoName() {
        String id = UUID.randomUUID().toString();
        PushRecord r = PushRecord.builder()
                .id(id)
                .provider("github")
                .project("RBC")
                .repoName("fogwall")
                .branch("refs/heads/main")
                .status(PushStatus.RECEIVED)
                .timestamp(Instant.now())
                .build();
        store.save(r);

        // matches repoName
        assertTrue(store.find(PushQuery.builder().search("fogwall").build()).stream()
                .anyMatch(x -> x.getId().equals(id)));
        // matches project
        assertTrue(store.find(PushQuery.builder().search("RBC").build()).stream()
                .anyMatch(x -> x.getId().equals(id)));
        // matches provider
        assertTrue(store.find(PushQuery.builder().search("github").build()).stream()
                .anyMatch(x -> x.getId().equals(id)));
        // case-insensitive
        assertTrue(store.find(PushQuery.builder().search("fogwall").build()).stream()
                .anyMatch(x -> x.getId().equals(id)));
        // no match
        assertTrue(store.find(PushQuery.builder().search("gitlab").build()).stream()
                .noneMatch(x -> x.getId().equals(id)));
    }

    @Test
    void find_withOffset_skipsPreviousPage() {
        // Insert 5 records with controlled timestamps so ordering is deterministic
        for (int i = 0; i < 5; i++) {
            store.save(PushRecord.builder()
                    .id("offset-test-" + i)
                    .project("org")
                    .repoName("paged-repo")
                    .branch("refs/heads/main")
                    .status(PushStatus.RECEIVED)
                    .timestamp(Instant.ofEpochSecond(1000 + i))
                    .build());
        }

        var page1 = store.find(PushQuery.builder()
                .repoName("paged-repo")
                .limit(3)
                .offset(0)
                .newestFirst(false)
                .build());
        var page2 = store.find(PushQuery.builder()
                .repoName("paged-repo")
                .limit(3)
                .offset(3)
                .newestFirst(false)
                .build());

        assertEquals(3, page1.size());
        assertEquals(2, page2.size());
        // pages must not overlap
        var page1Ids = page1.stream().map(PushRecord::getId).toList();
        var page2Ids = page2.stream().map(PushRecord::getId).toList();
        assertTrue(page1Ids.stream().noneMatch(page2Ids::contains));
    }
}
