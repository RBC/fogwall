package com.rbc.fogwall.db.jdbc;

import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** The activity aggregates' SQL on MariaDB. */
@Testcontainers
@Tag("integration")
class JdbcMariadbActivityAggregateTest extends SharedDatabaseActivityAggregateContract {

    @Container
    static final MariaDBContainer<?> MARIADB = new MariaDBContainer<>(
            DockerImageName.parse("docker.io/mariadb:11.4").asCompatibleSubstituteFor("mariadb"));

    private static DataSource dataSource;

    @Override
    protected DataSource dataSource() {
        if (dataSource == null) {
            dataSource = DataSourceFactory.fromUrl(MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword());
        }
        return dataSource;
    }
}
