package com.rbc.fogwall.db.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.PackChunks;
import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.PushStoreFactory;
import com.rbc.fogwall.db.model.FetchActivity;
import com.rbc.fogwall.db.model.FetchActivityQuery;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStatus;
import com.rbc.fogwall.git.ProxyMode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Smoke test proving the MySQL-family migration files ({@code db/migration-mysql/}) apply cleanly against a real
 * MariaDB server and that {@link JdbcPushStore} works on top of the resulting schema.
 *
 * <p>MariaDB and MySQL share one migration family ({@link DatabaseMigrator}'s {@code MYSQL_ONLY} vendor tag covers
 * both), but the two engines diverge on some DDL details (e.g. MariaDB supports {@code CREATE INDEX IF NOT EXISTS};
 * MySQL does not) — this test exists specifically to catch a case where the shared mysql-family SQL happens to work on
 * one engine but not the other. See {@link JdbcMysqlSmokeTest} for the MySQL counterpart.
 */
@Testcontainers
@Tag("integration")
class JdbcMariadbSmokeTest {

    @Container
    static final MariaDBContainer<?> MARIADB = new MariaDBContainer<>(
            DockerImageName.parse("docker.io/mariadb:11.4").asCompatibleSubstituteFor("mariadb"));

    DataSource dataSource;
    PushStore store;

    @BeforeEach
    void setUp() {
        dataSource = DataSourceFactory.fromUrl(MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword());
        store = PushStoreFactory.fromDataSource(dataSource);
    }

    @Test
    void migrate_appliesCleanlyAndPushStoreRoundTrips() {
        String id = UUID.randomUUID().toString();
        store.save(PushRecord.builder()
                .id(id)
                .project("org")
                .repoName("repo")
                .branch("refs/heads/main")
                .status(PushStatus.RECEIVED)
                .timestamp(Instant.now())
                .build());

        var found = store.findById(id);
        assertTrue(found.isPresent());
        assertEquals("repo", found.get().getRepoName());
    }

    @Test
    void migrate_widenedProviderColumn_acceptsLongValue() {
        String longProvider = "github/" + "a".repeat(280);
        String id = UUID.randomUUID().toString();
        store.save(PushRecord.builder()
                .id(id)
                .provider(longProvider)
                .project("org")
                .repoName("repo")
                .branch("refs/heads/main")
                .status(PushStatus.RECEIVED)
                .timestamp(Instant.now())
                .build());

        assertEquals(longProvider, store.findById(id).orElseThrow().getProvider());
    }

    @Test
    void migrate_parkedPushChunks_holdFullChunk() throws IOException {
        // V20.1 (mysql variant) stores chunks as MEDIUMBLOB; a BLOB column holds 64 KB, less than one chunk.
        JdbcParkedPushStore parked = new JdbcParkedPushStore(dataSource);
        byte[] pack = new byte[PackChunks.CHUNK_SIZE + 1];
        new Random(7).nextBytes(pack);
        String id = UUID.randomUUID().toString();

        parked.park(
                id, "github", "alice", "https://github.com/acme/repo.git", List.of(), new ByteArrayInputStream(pack));

        try (InputStream in = parked.openPack(id)) {
            assertArrayEquals(pack, in.readAllBytes());
        }
    }

    @Test
    void migrate_springSessionTables_exist() throws SQLException {
        try (Connection conn = dataSource.getConnection();
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM SPRING_SESSION_ATTRIBUTES")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
        }
    }

    @Test
    void migrate_unifiedRuleShapeColumns_areNotNull() throws SQLException {
        try (Connection conn = dataSource.getConnection();
                Statement st = conn.createStatement()) {
            assertThrows(
                    SQLException.class,
                    () -> st.executeUpdate("INSERT INTO access_rules (id, access, operation) VALUES ('"
                            + UUID.randomUUID() + "', 'ALLOW', 'BOTH')"));
        }
    }

    /** V21 (mysql variant) gives the timestamps an explicit default, so an increment never rewrites the hour. */
    @Test
    void migrate_fetchActivity_incrementKeepsTheHour() {
        var fetchStore = new JdbcFetchStore(dataSource);
        Instant hour = Instant.parse("2026-09-30T14:00:00Z");
        var key = new FetchActivity.Key(
                hour,
                "github/" + "a".repeat(280),
                "acme",
                "widgets",
                FetchActivity.Transport.SSH,
                ProxyMode.SERVER,
                FetchActivity.Result.ALLOWED,
                null,
                null);

        fetchStore.add(List.of(key.toActivity(1, hour.plusSeconds(10))));
        fetchStore.add(List.of(key.toActivity(2, hour.plusSeconds(20))));

        FetchActivity row =
                fetchStore.find(FetchActivityQuery.builder().build()).getFirst();
        assertEquals(3, row.getFetchCount());
        assertEquals(hour, row.getBucketStart());
        assertEquals(hour.plusSeconds(20), row.getLastSeen());
    }
}
