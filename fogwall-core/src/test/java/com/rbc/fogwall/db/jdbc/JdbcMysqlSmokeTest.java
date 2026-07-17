package com.rbc.fogwall.db.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.PushStoreFactory;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStatus;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Smoke test proving the MySQL-family migration files ({@code db/migration-mysql/}) apply cleanly against a real MySQL
 * server and that {@link JdbcPushStore} works on top of the resulting schema.
 *
 * <p>Not a full contract test — {@link JdbcPushStoreIntegrationTest} covers CRUD semantics exhaustively against H2;
 * this only needs to prove MySQL-specific SQL (BLOB instead of BYTEA, MODIFY COLUMN instead of ALTER COLUMN ... SET NOT
 * NULL, plain CREATE INDEX instead of CREATE INDEX IF NOT EXISTS) doesn't break on the real engine.
 */
@Testcontainers
@Tag("integration")
class JdbcMysqlSmokeTest {

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("docker.io/mysql:8.4").asCompatibleSubstituteFor("mysql"));

    DataSource dataSource;
    PushStore store;

    @BeforeEach
    void setUp() {
        dataSource = DataSourceFactory.fromUrl(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
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
        // V2.1 (mysql variant) widens `provider` to VARCHAR(300) via MODIFY COLUMN; a fresh V1 schema alone caps
        // it at VARCHAR(100).
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
    void migrate_springSessionTables_exist() throws SQLException {
        // V4 (mysql variant) replaces Postgres's BYTEA with BLOB — confirm the table actually got created.
        try (Connection conn = dataSource.getConnection();
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM SPRING_SESSION_ATTRIBUTES")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
        }
    }

    @Test
    void migrate_unifiedRuleShapeColumns_areNotNull() throws SQLException {
        // V5 (mysql variant) uses MODIFY COLUMN to apply NOT NULL after backfill — confirm it stuck.
        try (Connection conn = dataSource.getConnection();
                Statement st = conn.createStatement()) {
            assertThrows(
                    SQLException.class,
                    () -> st.executeUpdate("INSERT INTO access_rules (id, access, operation) VALUES ('"
                            + UUID.randomUUID() + "', 'ALLOW', 'BOTH')"));
        }
    }
}
