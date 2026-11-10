package com.rbc.fogwall.permission;

class InMemoryRoleChangeStoreTest extends RoleChangeStoreContract {

    private final InMemoryRoleChangeStore store = new InMemoryRoleChangeStore();

    @Override
    protected RoleChangeStore store() {
        return store;
    }
}
