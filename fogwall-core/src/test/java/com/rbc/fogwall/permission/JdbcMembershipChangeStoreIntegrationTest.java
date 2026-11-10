package com.rbc.fogwall.permission;

import com.rbc.fogwall.db.jdbc.DataSourceFactory;
import com.rbc.fogwall.db.jdbc.DatabaseMigrator;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;

/** {@link JdbcMembershipChangeStore} against an H2 in-memory database. */
class JdbcMembershipChangeStoreIntegrationTest extends MembershipChangeStoreContract {

    private JdbcMembershipChangeStore store;

    @BeforeEach
    void setUp() {
        DataSource ds = DataSourceFactory.h2InMemory("membership-change-test-" + UUID.randomUUID());
        DatabaseMigrator.migrate(ds);
        store = new JdbcMembershipChangeStore(ds);
    }

    @Override
    protected MembershipChangeStore store() {
        return store;
    }
}
