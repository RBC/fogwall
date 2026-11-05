package com.rbc.fogwall.permission;

class InMemoryPermissionChangeStoreTest extends PermissionChangeStoreContract {

    private final InMemoryPermissionChangeStore store = new InMemoryPermissionChangeStore();

    @Override
    protected PermissionChangeStore store() {
        return store;
    }
}
