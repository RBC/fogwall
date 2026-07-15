package com.rbc.fogwall.permission;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.jdbc.DataSourceFactory;
import com.rbc.fogwall.db.jdbc.DatabaseMigrator;
import com.rbc.fogwall.db.model.MatchType;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Integration tests for {@link JdbcGroupPermissionStore} backed by an H2 in-memory database.
 *
 * <p>Each test gets its own isolated H2 database via a unique name to prevent state leakage.
 */
class JdbcGroupPermissionStoreIntegrationTest {

    JdbcGroupPermissionStore store;
    NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        DataSource ds = DataSourceFactory.h2InMemory("group-test-" + UUID.randomUUID());
        DatabaseMigrator.migrate(ds);
        store = new JdbcGroupPermissionStore(ds);
        jdbc = new NamedParameterJdbcTemplate(ds);
        seedUser("alice");
        seedUser("bob");
    }

    private void seedUser(String username) {
        jdbc.update("INSERT INTO proxy_users (username, roles) VALUES (:u, 'USER')", Map.of("u", username));
    }

    private PermissionGroup dbGroup(String name) {
        PermissionGroup g = PermissionGroup.builder()
                .name(name)
                .source(PermissionGroup.Source.DB)
                .build();
        store.saveGroup(g);
        return g;
    }

    private GroupPermissionRule rule(String groupId, String provider, String value, RepoPermission.Grant grant) {
        GroupPermissionRule r = GroupPermissionRule.builder()
                .groupId(groupId)
                .provider(provider)
                .value(value)
                .matchType(MatchType.LITERAL)
                .grant(grant)
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
        var found = store.findGroupByName("devs");
        assertTrue(found.isPresent());
        assertEquals(g.getId(), found.get().getId());
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
        var names = store.findAllGroups().stream().map(PermissionGroup::getName).toList();
        assertEquals(java.util.List.of("alpha", "middle", "zebra"), names);
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
    void deleteGroup_cascadesRulesAndMembers() {
        PermissionGroup g = dbGroup("devs");
        store.addMember(g.getId(), "alice");
        rule(g.getId(), "github", "/acme/repo", RepoPermission.Grant.PUSH);

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
    void removeMember_removedFromList() {
        PermissionGroup g = dbGroup("devs");
        store.addMember(g.getId(), "alice");
        store.removeMember(g.getId(), "alice");
        assertTrue(store.findMembers(g.getId()).isEmpty());
    }

    @Test
    void findGroupIdsForUser_returnsAllGroups() {
        PermissionGroup g1 = dbGroup("devs");
        PermissionGroup g2 = dbGroup("admins");
        store.addMember(g1.getId(), "alice");
        store.addMember(g2.getId(), "alice");
        store.addMember(g1.getId(), "bob");

        var aliceGroups = store.findGroupIdsForUser("alice");
        assertEquals(2, aliceGroups.size());
        assertTrue(aliceGroups.contains(g1.getId()));
        assertTrue(aliceGroups.contains(g2.getId()));
    }

    @Test
    void findGroupIdsForUser_notMember_empty() {
        assertTrue(store.findGroupIdsForUser("alice").isEmpty());
    }

    // ---- rules ----

    @Test
    void saveRule_findRuleById_roundTrip() {
        PermissionGroup g = dbGroup("devs");
        GroupPermissionRule r = rule(g.getId(), "github", "/acme/repo", RepoPermission.Grant.PUSH);

        var found = store.findRuleById(r.getId());
        assertTrue(found.isPresent());
        assertEquals("github", found.get().getProvider());
        assertEquals("/acme/repo", found.get().getValue());
        assertEquals(RepoPermission.Grant.PUSH, found.get().getGrant());
    }

    @Test
    void findRulesForGroup_returnsOnlyThatGroup() {
        PermissionGroup g1 = dbGroup("devs");
        PermissionGroup g2 = dbGroup("ops");
        rule(g1.getId(), "github", "/acme/repo", RepoPermission.Grant.PUSH);
        rule(g2.getId(), "github", "/other/repo", RepoPermission.Grant.REVIEW);

        var rules = store.findRulesForGroup(g1.getId());
        assertEquals(1, rules.size());
        assertEquals("/acme/repo", rules.get(0).getValue());
    }

    @Test
    void findRulesByProvider_returnsAcrossGroups() {
        PermissionGroup g1 = dbGroup("devs");
        PermissionGroup g2 = dbGroup("ops");
        rule(g1.getId(), "github", "/acme/a", RepoPermission.Grant.PUSH);
        rule(g2.getId(), "github", "/acme/b", RepoPermission.Grant.PUSH);
        rule(g1.getId(), "gitlab", "/acme/a", RepoPermission.Grant.PUSH);

        var githubRules = store.findRulesByProvider("github");
        assertEquals(2, githubRules.size());
        assertTrue(githubRules.stream().allMatch(r -> "github".equals(r.getProvider())));
    }

    @Test
    void deleteRule_removedFromGroup() {
        PermissionGroup g = dbGroup("devs");
        GroupPermissionRule r = rule(g.getId(), "github", "/acme/repo", RepoPermission.Grant.PUSH);
        store.deleteRule(r.getId());
        assertTrue(store.findRuleById(r.getId()).isEmpty());
        assertTrue(store.findRulesForGroup(g.getId()).isEmpty());
    }

    @Test
    void allGrantTypes_roundTrip() {
        PermissionGroup g = dbGroup("devs");
        for (var grant : RepoPermission.Grant.values()) {
            GroupPermissionRule r =
                    rule(g.getId(), "github", "/acme/" + grant.name().toLowerCase(), grant);
            assertEquals(grant, store.findRuleById(r.getId()).orElseThrow().getGrant());
        }
    }
}
