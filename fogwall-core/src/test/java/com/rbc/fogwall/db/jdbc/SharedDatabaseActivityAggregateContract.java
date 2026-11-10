package com.rbc.fogwall.db.jdbc;

import com.rbc.fogwall.db.ActivityAggregateContract;
import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.PushStoreFactory;
import com.rbc.fogwall.db.ScmApiActionStore;
import com.rbc.fogwall.db.ScmApiEntityStore;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The activity aggregates against a database server one container shares across tests, emptied of the tables they read
 * before each.
 */
abstract class SharedDatabaseActivityAggregateContract extends ActivityAggregateContract {

    private PushStore pushStore;
    private JdbcScmApiActionStore actionStore;
    private JdbcScmApiEntityStore entityStore;

    protected abstract DataSource dataSource();

    @BeforeEach
    void setUp() {
        DataSource dataSource = dataSource();
        pushStore = PushStoreFactory.fromDataSource(dataSource);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM scm_api_action_records");
        jdbc.update("DELETE FROM scm_api_entities");
        jdbc.update("DELETE FROM push_records");
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
