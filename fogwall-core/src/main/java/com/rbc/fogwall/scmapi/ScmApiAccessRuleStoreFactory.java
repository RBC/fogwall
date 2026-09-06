package com.rbc.fogwall.scmapi;

import javax.sql.DataSource;

/** Factory for creating {@link ScmApiAccessRuleStore} instances from a JDBC {@link DataSource}. */
public final class ScmApiAccessRuleStoreFactory {

    private ScmApiAccessRuleStoreFactory() {}

    public static ScmApiAccessRuleStore fromDataSource(DataSource dataSource) {
        JdbcScmApiAccessRuleStore store = new JdbcScmApiAccessRuleStore(dataSource);
        store.initialize();
        return store;
    }
}
