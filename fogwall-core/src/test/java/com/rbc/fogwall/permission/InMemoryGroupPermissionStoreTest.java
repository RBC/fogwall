package com.rbc.fogwall.permission;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.model.MatchType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryGroupPermissionStoreTest {

    InMemoryGroupPermissionStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryGroupPermissionStore();
    }

    private PermissionGroup dbGroup(String name) {
        PermissionGroup g = PermissionGroup.builder()
                .name(name)
                .source(PermissionGroup.Source.DB)
                .build();
        store.saveGroup(g);
        return g;
    }

    private GroupPermissionRule rule(String groupId, String provider, String value) {
        GroupPermissionRule r = GroupPermissionRule.builder()
                .groupId(groupId)
                .provider(provider)
                .value(value)
                .matchType(MatchType.LITERAL)
                .grant(RepoPermission.Grant.PUSH)
                .build();
        store.saveRule(r);
        return r;
    }

    // ---- groups ----

    @Test
    void saveGroup_findById_roundTrip() {
        PermissionGroup g = dbGroup("devs");
        var found = store.findGroupById(g.getId());
        assertTrue(found.isPresent());
        assertEquals("devs", found.get().getName());
        assertEquals(PermissionGroup.Source.DB, found.get().getSource());
    }

    @Test
    void findGroupByName_returnsGroup() {
        PermissionGroup g = dbGroup("devs");
        assertTrue(store.findGroupByName("devs").isPresent());
        assertEquals(g.getId(), store.findGroupByName("devs").get().getId());
    }

    @Test
    void findGroupByName_unknownName_empty() {
        assertTrue(store.findGroupByName("nobody").isEmpty());
    }

    @Test
    void findAllGroups_sortedByName() {
        dbGroup("zebra");
        dbGroup("alpha");
        dbGroup("middle");
        var all = store.findAllGroups();
        assertEquals(
                List.of("alpha", "middle", "zebra"),
                all.stream().map(PermissionGroup::getName).toList());
    }

    @Test
    void updateGroup_changesNameAndDescription() {
        PermissionGroup g = dbGroup("devs");
        store.updateGroup(g.getId(), "engineers", "All engineers");
        var updated = store.findGroupById(g.getId()).orElseThrow();
        assertEquals("engineers", updated.getName());
        assertEquals("All engineers", updated.getDescription());
        assertEquals(PermissionGroup.Source.DB, updated.getSource());
    }

    @Test
    void deleteGroup_removesGroupMembersAndRules() {
        PermissionGroup g = dbGroup("devs");
        store.addMember(g.getId(), "alice");
        rule(g.getId(), "github", "/acme/repo");

        store.deleteGroup(g.getId());

        assertTrue(store.findGroupById(g.getId()).isEmpty());
        assertTrue(store.findMembers(g.getId()).isEmpty());
        assertTrue(store.findRulesForGroup(g.getId()).isEmpty());
    }

    // ---- members ----

    @Test
    void addMember_findMembers_roundTrip() {
        PermissionGroup g = dbGroup("devs");
        store.addMember(g.getId(), "alice");
        store.addMember(g.getId(), "bob");

        var members = store.findMembers(g.getId());
        assertEquals(2, members.size());
        assertTrue(members.contains("alice"));
        assertTrue(members.contains("bob"));
    }

    @Test
    void removeMember_noLongerReturned() {
        PermissionGroup g = dbGroup("devs");
        store.addMember(g.getId(), "alice");
        store.removeMember(g.getId(), "alice");
        assertTrue(store.findMembers(g.getId()).isEmpty());
    }

    @Test
    void findGroupIdsForUser_returnsAllGroupsUserBelongsTo() {
        PermissionGroup g1 = dbGroup("devs");
        PermissionGroup g2 = dbGroup("admins");
        store.addMember(g1.getId(), "alice");
        store.addMember(g2.getId(), "alice");
        store.addMember(g1.getId(), "bob");

        var aliceGroups = store.findGroupIdsForUser("alice");
        assertEquals(2, aliceGroups.size());
        assertTrue(aliceGroups.contains(g1.getId()));
        assertTrue(aliceGroups.contains(g2.getId()));

        var bobGroups = store.findGroupIdsForUser("bob");
        assertEquals(1, bobGroups.size());
        assertEquals(g1.getId(), bobGroups.get(0));
    }

    @Test
    void findGroupIdsForUser_notMember_empty() {
        assertTrue(store.findGroupIdsForUser("nobody").isEmpty());
    }

    // ---- rules ----

    @Test
    void saveRule_findRuleById_roundTrip() {
        PermissionGroup g = dbGroup("devs");
        GroupPermissionRule r = rule(g.getId(), "github", "/acme/repo");

        var found = store.findRuleById(r.getId());
        assertTrue(found.isPresent());
        assertEquals("github", found.get().getProvider());
        assertEquals("/acme/repo", found.get().getValue());
    }

    @Test
    void findRulesForGroup_returnsOnlyThatGroup() {
        PermissionGroup g1 = dbGroup("devs");
        PermissionGroup g2 = dbGroup("ops");
        rule(g1.getId(), "github", "/acme/repo");
        rule(g2.getId(), "github", "/other/repo");

        var rules = store.findRulesForGroup(g1.getId());
        assertEquals(1, rules.size());
        assertEquals("/acme/repo", rules.get(0).getValue());
    }

    @Test
    void findRulesByProvider_returnsAcrossGroups() {
        PermissionGroup g1 = dbGroup("devs");
        PermissionGroup g2 = dbGroup("ops");
        rule(g1.getId(), "github", "/acme/repo");
        rule(g2.getId(), "github", "/other/repo");
        rule(g1.getId(), "gitlab", "/acme/repo");

        var githubRules = store.findRulesByProvider("github");
        assertEquals(2, githubRules.size());
    }

    @Test
    void deleteRule_removedFromStore() {
        PermissionGroup g = dbGroup("devs");
        GroupPermissionRule r = rule(g.getId(), "github", "/acme/repo");
        store.deleteRule(r.getId());
        assertTrue(store.findRuleById(r.getId()).isEmpty());
        assertTrue(store.findRulesForGroup(g.getId()).isEmpty());
    }
}
