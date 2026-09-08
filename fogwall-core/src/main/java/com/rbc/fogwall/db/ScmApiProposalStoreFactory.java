package com.rbc.fogwall.db;

import com.rbc.fogwall.db.jdbc.JdbcScmApiProposalStore;
import javax.sql.DataSource;

/** Factory for {@link ScmApiProposalStore} instances, mirroring {@link ScmApiActionStoreFactory}. */
public final class ScmApiProposalStoreFactory {

    private ScmApiProposalStoreFactory() {}

    public static ScmApiProposalStore fromDataSource(DataSource dataSource) {
        JdbcScmApiProposalStore store = new JdbcScmApiProposalStore(dataSource);
        store.initialize();
        return store;
    }
}
