package com.rbc.fogwall.db.memory;

import com.rbc.fogwall.db.FetchStore;
import com.rbc.fogwall.db.FetchStoreContract;

class InMemoryFetchStoreTest extends FetchStoreContract {

    private final InMemoryFetchStore store = new InMemoryFetchStore();

    @Override
    protected FetchStore store() {
        return store;
    }
}
