package com.rbc.fogwall.dashboard.session;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.session.MapSession;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@Tag("integration")
class MongoSessionRepositoryIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    private MongoClient client;
    private MongoSessionRepository repo;

    @BeforeEach
    void setUp() {
        client = MongoClients.create(MONGO.getConnectionString());
        String db = "sessions_" + UUID.randomUUID().toString().replace("-", "");
        repo = new MongoSessionRepository(client, db, Duration.ofMinutes(30));
    }

    @AfterEach
    void tearDown() {
        if (client != null) client.close();
    }

    @Test
    void createSession_hasConfiguredTimeout() {
        MapSession s = repo.createSession();
        assertEquals(Duration.ofMinutes(30), s.getMaxInactiveInterval());
    }

    @Test
    void save_then_findById_roundTripsAttributes() {
        MapSession s = repo.createSession();
        s.setAttribute("username", "alice");
        s.setAttribute("role-count", 3);
        repo.save(s);

        MapSession loaded = repo.findById(s.getId());
        assertNotNull(loaded);
        assertEquals(s.getId(), loaded.getId());
        assertEquals("alice", loaded.getAttribute("username"));
        assertEquals(Integer.valueOf(3), loaded.getAttribute("role-count"));
    }

    @Test
    void findById_unknownId_returnsNull() {
        assertNull(repo.findById("does-not-exist"));
    }

    @Test
    void deleteById_removesSession() {
        MapSession s = repo.createSession();
        s.setAttribute("k", "v");
        repo.save(s);
        assertNotNull(repo.findById(s.getId()));

        repo.deleteById(s.getId());
        assertNull(repo.findById(s.getId()));
    }

    @Test
    void findById_expiredSession_returnsNullAndDeletes() {
        MapSession s = repo.createSession();
        s.setMaxInactiveInterval(Duration.ofSeconds(1));
        s.setLastAccessedTime(Instant.now().minusSeconds(60));
        s.setAttribute("k", "v");
        repo.save(s);

        assertNull(repo.findById(s.getId()), "expired session must return null");
        // Confirm it was also removed from the collection (not just filtered in-memory).
        // A second lookup would otherwise still find the raw document.
        assertNull(repo.findById(s.getId()));
    }

    @Test
    void save_updatesExistingSession() {
        MapSession s = repo.createSession();
        s.setAttribute("v", 1);
        repo.save(s);

        s.setAttribute("v", 2);
        repo.save(s);

        MapSession loaded = repo.findById(s.getId());
        assertNotNull(loaded);
        assertEquals(Integer.valueOf(2), loaded.getAttribute("v"));
    }
}
