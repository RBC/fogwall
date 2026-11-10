package com.rbc.fogwall.db.jdbc;

import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** The activity aggregates' SQL on MySQL. */
@Testcontainers
@Tag("integration")
class JdbcMysqlActivityAggregateTest extends SharedDatabaseActivityAggregateContract {

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("docker.io/mysql:8.4").asCompatibleSubstituteFor("mysql"));

    private static DataSource dataSource;

    @Override
    protected DataSource dataSource() {
        if (dataSource == null) {
            dataSource = DataSourceFactory.fromUrl(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        }
        return dataSource;
    }
}
