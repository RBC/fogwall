package com.rbc.fogwall.db.mongo;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.rbc.fogwall.db.PackChunks;
import com.rbc.fogwall.db.model.ParkedPush;
import com.rbc.fogwall.db.model.ParkedRefUpdate;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStatus;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Mirrors {@code JdbcParkedPushStoreIntegrationTest}: the two stores are peers and must behave the same, so a
 * deployment's database family does not decide whether a parked push can be forwarded.
 */
@Testcontainers
@Tag("integration")
class MongoParkedPushStoreIntegrationTest {

    @Container
    static final MongoDBContainer MONGO =
            new MongoDBContainer(DockerImageName.parse("docker.io/mongo:7.0").asCompatibleSubstituteFor("mongo"));

    private static final List<ParkedRefUpdate> REFS = List.of(
            new ParkedRefUpdate("refs/heads/main", "a".repeat(40), "b".repeat(40), ReceiveCommand.Type.UPDATE),
            new ParkedRefUpdate("refs/tags/v1", "0".repeat(40), "c".repeat(40), ReceiveCommand.Type.CREATE));

    MongoClient client;
    MongoPushStore pushStore;
    MongoParkedPushStore store;

    @BeforeEach
    void setUp() {
        client = MongoClients.create(MONGO.getConnectionString());
        String db = "testdb_" + UUID.randomUUID().toString().replace("-", "");
        pushStore = new MongoPushStore(client, db);
        pushStore.initialize();
        store = new MongoParkedPushStore(client, db);
        store.initialize();
    }

    @AfterEach
    void tearDown() {
        client.close();
    }

    private static byte[] pack(int size) {
        byte[] data = new byte[size];
        new Random(7).nextBytes(data);
        return data;
    }

    private ParkedPush park(String pushId, byte[] pack) throws IOException {
        return store.park(
                pushId, "github", "alice", "https://github.com/acme/repo.git", REFS, new ByteArrayInputStream(pack));
    }

    private void record(String pushId, PushStatus status) {
        pushStore.save(
                PushRecord.builder().id(pushId).status(status).deferred(true).build());
    }

    @Test
    void park_thenFindAndOpen_roundTripsMultiChunkPack() throws IOException {
        byte[] pack = pack(PackChunks.CHUNK_SIZE * 2 + 99);

        ParkedPush parked = park("push-1", pack);

        ParkedPush found = store.find("push-1").orElseThrow();
        assertEquals(parked.packBytes(), found.packBytes());
        assertEquals(pack.length, found.packBytes());
        assertEquals("github", found.providerName());
        assertEquals("alice", found.forwardUser());
        assertEquals("https://github.com/acme/repo.git", found.upstreamUrl());
        assertEquals(REFS, found.refs());
        assertEquals(
                parked.parkedAt().truncatedTo(ChronoUnit.MILLIS),
                found.parkedAt().truncatedTo(ChronoUnit.MILLIS));
        try (InputStream in = store.openPack("push-1")) {
            assertArrayEquals(pack, in.readAllBytes());
        }
    }

    @Test
    void park_emptyPack_storesRefUpdatesOnly() throws IOException {
        park("push-1", new byte[0]);

        assertEquals(0, store.find("push-1").orElseThrow().packBytes());
        try (InputStream in = store.openPack("push-1")) {
            assertEquals(-1, in.read());
        }
    }

    @Test
    void park_unreadablePack_leavesNothingBehind() {
        InputStream failing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("client went away");
            }
        };

        assertThrows(
                IOException.class,
                () -> store.park("push-1", "github", "alice", "https://github.com/acme/repo.git", REFS, failing));

        assertTrue(store.find("push-1").isEmpty());
        assertEquals(List.of(), store.findReclaimable(Instant.MAX, Instant.MAX, 10));
    }

    @Test
    void delete_removesPushAndPack() throws IOException {
        park("push-1", pack(1024));

        store.delete("push-1");

        assertTrue(store.find("push-1").isEmpty());
        assertThrows(IOException.class, () -> store.openPack("push-1"));
    }

    @Test
    void findReclaimable_keepsForwardablePushes() throws IOException {
        park("pending", pack(10));
        record("pending", PushStatus.PENDING);
        park("approved", pack(10));
        record("approved", PushStatus.APPROVED);
        park("failed", pack(10));
        record("failed", PushStatus.ERROR);
        park("unrecorded", pack(10));
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);

        assertEquals(List.of(), store.findReclaimable(past, past, 10));
    }

    @Test
    void findReclaimable_returnsFinishedExpiredAndOrphanedPushes() throws IOException {
        park("forwarded", pack(10));
        record("forwarded", PushStatus.FORWARDED);
        park("rejected", pack(10));
        record("rejected", PushStatus.REJECTED);
        park("canceled", pack(10));
        record("canceled", PushStatus.CANCELED);
        park("failed", pack(10));
        record("failed", PushStatus.ERROR);
        park("unrecorded", pack(10));
        park("pending", pack(10));
        record("pending", PushStatus.PENDING);
        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);

        assertEquals(
                Set.of("forwarded", "rejected", "canceled", "failed", "unrecorded"),
                Set.copyOf(store.findReclaimable(future, future, 10)));
    }
}
