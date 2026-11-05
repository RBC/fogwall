package com.rbc.fogwall.permission;

import com.rbc.fogwall.db.jdbc.DataSourceFactory;
import com.rbc.fogwall.db.jdbc.DatabaseMigrator;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;

/** {@link JdbcPermissionChangeStore} against an H2 in-memory database. */
class JdbcPermissionChangeStoreIntegrationTest extends PermissionChangeStoreContract {

    private JdbcPermissionChangeStore store;

    @BeforeEach
    void setUp() {
        DataSource ds = DataSourceFactory.h2InMemory("permission-change-test-" + UUID.randomUUID());
        DatabaseMigrator.migrate(ds);
        store = new JdbcPermissionChangeStore(ds);
    }

    @Override
    protected PermissionChangeStore store() {
        return store;
    }
}
