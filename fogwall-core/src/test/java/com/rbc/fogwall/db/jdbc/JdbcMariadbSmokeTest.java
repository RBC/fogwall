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
}
