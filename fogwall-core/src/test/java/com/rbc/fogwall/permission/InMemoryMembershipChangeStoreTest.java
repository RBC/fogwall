package com.rbc.fogwall.permission;

class InMemoryMembershipChangeStoreTest extends MembershipChangeStoreContract {

    private final InMemoryMembershipChangeStore store = new InMemoryMembershipChangeStore();

    @Override
    protected MembershipChangeStore store() {
        return store;
    }
}
