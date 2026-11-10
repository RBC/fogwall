package com.rbc.fogwall.db.jdbc;

import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** The activity aggregates' SQL on PostgreSQL. */
@Testcontainers
@Tag("integration")
class JdbcPostgresActivityAggregateTest extends SharedDatabaseActivityAggregateContract {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("docker.io/postgres:17-alpine").asCompatibleSubstituteFor("postgres"));

    private static DataSource dataSource;

    @Override
    protected DataSource dataSource() {
        if (dataSource == null) {
            dataSource =
                    DataSourceFactory.fromUrl(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        }
        return dataSource;
    }
}
