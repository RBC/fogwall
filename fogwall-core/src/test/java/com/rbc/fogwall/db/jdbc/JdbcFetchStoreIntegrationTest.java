package com.rbc.fogwall.db.jdbc;

import com.rbc.fogwall.db.FetchStore;
import com.rbc.fogwall.db.FetchStoreContract;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;

/** {@link JdbcFetchStore} against an H2 in-memory database. */
class JdbcFetchStoreIntegrationTest extends FetchStoreContract {

    private JdbcFetchStore store;

    @BeforeEach
    void setUp() {
        store = new JdbcFetchStore(DataSourceFactory.h2InMemory("fetch-store-test-" + UUID.randomUUID()));
        store.initialize();
    }

    @Override
    protected FetchStore store() {
        return store;
    }
}
