package com.rbc.fogwall.permission;

import com.rbc.fogwall.db.jdbc.DataSourceFactory;
import com.rbc.fogwall.db.jdbc.DatabaseMigrator;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;

/** {@link JdbcRoleChangeStore} against an H2 in-memory database. */
class JdbcRoleChangeStoreIntegrationTest extends RoleChangeStoreContract {

    private JdbcRoleChangeStore store;

    @BeforeEach
    void setUp() {
        DataSource ds = DataSourceFactory.h2InMemory("role-change-test-" + UUID.randomUUID());
        DatabaseMigrator.migrate(ds);
        store = new JdbcRoleChangeStore(ds);
    }

    @Override
    protected RoleChangeStore store() {
        return store;
    }
}
