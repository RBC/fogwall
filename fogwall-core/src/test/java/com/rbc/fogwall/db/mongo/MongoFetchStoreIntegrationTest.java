package com.rbc.fogwall.db.mongo;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.MongoClients;
import com.rbc.fogwall.db.FetchStore.RepoFetchSummary;
import com.rbc.fogwall.db.model.FetchRecord;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@Tag("integration")
class MongoFetchStoreIntegrationTest {

    @Container
    static final MongoDBContainer MONGO =
            new MongoDBContainer(DockerImageName.parse("docker.io/mongo:7.0").asCompatibleSubstituteFor("mongo"));

    MongoFetchStore store;

    @BeforeEach
    void setUp() {
        store = new MongoFetchStore(
                MongoClients.create(MONGO.getConnectionString()),
                "testdb_" + UUID.randomUUID().toString().replace("-", ""));
        store.initialize();
    }

    private FetchRecord allowed(String provider, String owner, String repo) {
        return FetchRecord.builder()
                .provider(provider)
                .owner(owner)
                .repoName(repo)
                .result(FetchRecord.Result.ALLOWED)
                .pushUsername("me")
                .timestamp(Instant.now())
                .build();
    }

    private FetchRecord blocked(String provider, String owner, String repo) {
        return FetchRecord.builder()
                .provider(provider)
                .owner(owner)
                .repoName(repo)
                .result(FetchRecord.Result.BLOCKED)
                .pushUsername("me")
                .timestamp(Instant.now())
                .build();
    }

    @Test
    void record_andFindRecent_roundTripsAllFields() {
        FetchRecord r = FetchRecord.builder()
                .provider("github")
                .owner("org")
                .repoName("repo")
                .result(FetchRecord.Result.ALLOWED)
                .pushUsername("me")
                .resolvedUser("alice")
                .timestamp(Instant.now())
                .build();
        store.record(r);

        List<FetchRecord> recent = store.findRecent(10);
        assertEquals(1, recent.size());
        FetchRecord found = recent.get(0);
        assertEquals(r.getId(), found.getId());
        assertEquals("github", found.getProvider());
        assertEquals("org", found.getOwner());
        assertEquals("repo", found.getRepoName());
        assertEquals(FetchRecord.Result.ALLOWED, found.getResult());
        assertEquals("me", found.getPushUsername());
        assertEquals("alice", found.getResolvedUser());
    }

    @Test
    void findRecent_respectsLimit() {
        for (int i = 0; i < 5; i++) store.record(allowed("github", "org", "repo"));
        assertEquals(3, store.findRecent(3).size());
    }

    @Test
    void findRecent_newestFirst() {
        store.record(FetchRecord.builder()
                .provider("github")
                .owner("org")
                .repoName("repo")
                .result(FetchRecord.Result.ALLOWED)
                .timestamp(Instant.ofEpochSecond(1000))
                .build());
        store.record(FetchRecord.builder()
                .provider("github")
                .owner("org")
                .repoName("repo")
                .result(FetchRecord.Result.BLOCKED)
                .timestamp(Instant.ofEpochSecond(2000))
                .build());

        List<FetchRecord> recent = store.findRecent(2);
        assertEquals(FetchRecord.Result.BLOCKED, recent.get(0).getResult());
        assertEquals(FetchRecord.Result.ALLOWED, recent.get(1).getResult());
    }

    @Test
    void findByRepo_returnsOnlyMatchingRepo() {
        store.record(allowed("github", "org", "repo-a"));
        store.record(allowed("github", "org", "repo-b"));

        List<FetchRecord> results = store.findByRepo("github", "org", "repo-a", 10);
        assertEquals(1, results.size());
        assertEquals("repo-a", results.get(0).getRepoName());
    }

    @Test
    void findByRepo_respectsLimit() {
        for (int i = 0; i < 5; i++) store.record(allowed("github", "org", "repo"));
        assertEquals(2, store.findByRepo("github", "org", "repo", 2).size());
    }

    @Test
    void summarizeByRepo_countsCorrectly() {
        store.record(allowed("github", "org", "repo"));
        store.record(allowed("github", "org", "repo"));
        store.record(blocked("github", "org", "repo"));
        store.record(allowed("github", "org", "other-repo"));

        List<RepoFetchSummary> summaries = store.summarizeByRepo();

        RepoFetchSummary repoSummary = summaries.stream()
                .filter(s -> "repo".equals(s.repoName()))
                .findFirst()
                .orElseThrow();
        assertEquals(3, repoSummary.total());
        assertEquals(1, repoSummary.blocked());

        RepoFetchSummary otherSummary = summaries.stream()
                .filter(s -> "other-repo".equals(s.repoName()))
                .findFirst()
                .orElseThrow();
        assertEquals(1, otherSummary.total());
        assertEquals(0, otherSummary.blocked());
    }

    @Test
    void summarizeByRepo_orderedByTotalDesc() {
        store.record(allowed("github", "org", "busy-repo"));
        store.record(allowed("github", "org", "busy-repo"));
        store.record(allowed("github", "org", "quiet-repo"));

        List<RepoFetchSummary> summaries = store.summarizeByRepo();
        assertEquals("busy-repo", summaries.get(0).repoName());
    }

    @Test
    void summarizeByRepo_emptyStore_returnsEmptyList() {
        assertTrue(store.summarizeByRepo().isEmpty());
    }
}
