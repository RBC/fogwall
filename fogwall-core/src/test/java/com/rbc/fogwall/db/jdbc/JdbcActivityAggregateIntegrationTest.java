package com.rbc.fogwall.db.jdbc;

import com.rbc.fogwall.db.ActivityAggregateContract;
import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.ScmApiActionStore;
import com.rbc.fogwall.db.ScmApiEntityStore;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;

/** The JDBC stores' activity aggregates against an H2 in-memory database. */
class JdbcActivityAggregateIntegrationTest extends ActivityAggregateContract {

    private JdbcPushStore pushStore;
    private JdbcScmApiActionStore actionStore;
    private JdbcScmApiEntityStore entityStore;

    @BeforeEach
    void setUp() {
        DataSource dataSource = DataSourceFactory.h2InMemory("activity-test-" + UUID.randomUUID());
        pushStore = new JdbcPushStore(dataSource);
        pushStore.initialize();
        actionStore = new JdbcScmApiActionStore(dataSource);
        entityStore = new JdbcScmApiEntityStore(dataSource);
    }

    @Override
    protected PushStore pushStore() {
        return pushStore;
    }

    @Override
    protected ScmApiActionStore actionStore() {
        return actionStore;
    }

    @Override
    protected ScmApiEntityStore entityStore() {
        return entityStore;
    }
}
