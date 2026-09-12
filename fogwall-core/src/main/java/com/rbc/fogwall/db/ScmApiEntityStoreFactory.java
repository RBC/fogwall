package com.rbc.fogwall.db;

import com.rbc.fogwall.db.jdbc.JdbcScmApiEntityStore;
import javax.sql.DataSource;

/** Factory for {@link ScmApiEntityStore} instances, mirroring {@link ScmApiActionStoreFactory}. */
public final class ScmApiEntityStoreFactory {

    private ScmApiEntityStoreFactory() {}

    public static ScmApiEntityStore fromDataSource(DataSource dataSource) {
        JdbcScmApiEntityStore store = new JdbcScmApiEntityStore(dataSource);
        store.initialize();
        return store;
    }
}
