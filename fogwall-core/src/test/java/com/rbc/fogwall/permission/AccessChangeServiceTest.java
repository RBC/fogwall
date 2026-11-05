package com.rbc.fogwall.permission;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.jdbc.DataSourceFactory;
import com.rbc.fogwall.db.jdbc.DatabaseMigrator;
import com.rbc.fogwall.db.model.MatchTarget;
import com.rbc.fogwall.db.model.MatchType;
import com.rbc.fogwall.permission.MembershipChangeEvent.Action;
import com.rbc.fogwall.service.JdbcScmTokenCache;
import com.rbc.fogwall.user.JdbcUserStore;
import com.rbc.fogwall.user.UserEntry;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link AccessChangeService} over the JDBC grant, group and user stores on H2, so the foreign keys the service works
 * around are real, and in-memory event stores.
 */
class AccessChangeServiceTest {

    private static final ChangeContext BY_ADMIN = ChangeContext.by(ChangeOrigin.DASHBOARD, "admin");

    private PermissionStore<RepoPermission> grants;
    private GroupPermissionStore groups;
    private JdbcUserStore users;
    private RepoPermissionService permissions;
    private InMemoryPermissionChangeStore permissionChanges;
    private InMemoryMembershipChangeStore membershipChanges;
    private InMemoryRoleChangeStore roleChanges;
    private AccessChangeService service;
    private PermissionGroup devs;

    @BeforeEach
    void setUp() {
        DataSource ds = DataSourceFactory.h2InMemory("access-change-test-" + UUID.randomUUID());
        DatabaseMigrator.migrate(ds);
        grants = new JdbcRepoPermissionStore(ds);
        groups = new JdbcGroupPermissionStore(ds);
        users = new JdbcUserStore(ds, new JdbcScmTokenCache(ds, Duration.ofDays(1)));
        users.createUser("alice", "{noop}pw", "USER");
        users.createUser("bob", "{noop}pw", "USER");
        permissions = new RepoPermissionService(grants, groups, users);
        permissionChanges = new InMemoryPermissionChangeStore();
        membershipChanges = new InMemoryMembershipChangeStore();
        roleChanges = new InMemoryRoleChangeStore();
        service = serviceWith(permissionChanges, membershipChanges, roleChanges);
        devs = PermissionGroup.builder().name("devs").build();
        groups.saveGroup(devs);
    }

    private AccessChangeService serviceWith(
            PermissionChangeStore permissionEvents,
            MembershipChangeStore membershipEvents,
            RoleChangeStore roleEvents) {
        return new AccessChangeService(permissions, users, permissionEvents, membershipEvents, roleEvents);
    }

    private static RepoPermission grant(String username, String value) {
        return grant(username, value, MatchType.LITERAL, RepoPermission.Source.DB);
    }

    private static RepoPermission configGrant(String username, String value) {
        return grant(username, value, MatchType.LITERAL, RepoPermission.Source.CONFIG);
    }

    private static RepoPermission grant(
            String username, String value, MatchType matchType, RepoPermission.Source source) {
        return RepoPermission.builder()
                .username(username)
                .provider("github")
                .target(MatchTarget.SLUG)
                .value(value)
                .matchType(matchType)
                .grant(RepoPermission.Grant.PUSH)
                .source(source)
                .build();
    }

    private GroupPermissionRule rule(String value) {
        return GroupPermissionRule.builder()
                .groupId(devs.getId())
                .provider("github")
                .value(value)
                .matchType(MatchType.LITERAL)
                .grant(RepoPermission.Grant.REVIEW)
                .build();
    }

    private List<PermissionChangeEvent> permissionEvents() {
        return permissionChanges.find(
                PermissionChangeQuery.builder().newestFirst(false).build());
    }

    private List<MembershipChangeEvent> membershipEvents() {
        return membershipChanges.find(
                MembershipChangeQuery.builder().newestFirst(false).build());
    }

    /** A store that refuses every write, standing in for an unavailable audit database. */
    private static final class FailingPermissionChangeStore implements PermissionChangeStore {
        @Override
        public void record(PermissionChangeEvent event) {
            throw new IllegalStateException("audit store unavailable");
        }

        @Override
        public List<PermissionChangeEvent> find(PermissionChangeQuery query) {
            return List.of();
        }
    }

    private static final class FailingMembershipChangeStore implements MembershipChangeStore {
        @Override
        public void record(MembershipChangeEvent event) {
            throw new IllegalStateException("audit store unavailable");
        }

        @Override
        public List<MembershipChangeEvent> find(MembershipChangeQuery query) {
            return List.of();
        }
    }

    // ---- user grants ----

    @Test
    void grant_recordsWhoGrantedWhatToWhom_andSavesTheGrant() {
        RepoPermission p = grant("alice", "/acme/widgets");

        service.grant(p, BY_ADMIN);

        assertEquals(List.of(p), grants.findAll());
        PermissionChangeEvent e = permissionEvents().getFirst();
        assertEquals(PermissionChangeEvent.Action.GRANTED, e.action());
        assertEquals(ChangeOrigin.DASHBOARD, e.origin());
        assertEquals("admin", e.actor());
        assertEquals(PermissionChangeEvent.SubjectType.USER, e.subjectType());
        assertEquals("alice", e.subject());
        assertEquals(p.getId(), e.permissionId());
        assertEquals("/acme/widgets", e.value());
        assertEquals(RepoPermission.Grant.PUSH, e.grant());
    }

    @Test
    void grant_storesWhoGrantedItAndWhen_matchingTheEvent() {
        RepoPermission p = grant("alice", "/acme/widgets");

        service.grant(p, BY_ADMIN);

        RepoPermission stored = grants.findById(p.getId()).orElseThrow();
        assertEquals("admin", stored.getGrantedBy());
        assertNotNull(stored.getGrantedAt());
        assertEquals(stored.getGrantedAt(), permissionEvents().getFirst().occurredAt());
    }

    @Test
    void addGroupRule_storesWhoGrantedItAndWhen_matchingTheEvent() {
        GroupPermissionRule r = rule("/acme/widgets");

        service.addGroupRule(devs, r, BY_ADMIN);

        GroupPermissionRule stored = groups.findRuleById(r.getId()).orElseThrow();
        assertEquals("admin", stored.getGrantedBy());
        assertEquals(stored.getGrantedAt(), permissionEvents().getFirst().occurredAt());
    }

    @Test
    void reloadConfigPermissions_stampsTheTimeButNoGranter_onWhatItAdds() {
        service.reloadConfigPermissions(List.of(configGrant("alice", "/acme/a")), ChangeContext.config("4f2c9e1"));

        RepoPermission stored = grants.findByUsername("alice").getFirst();
        assertNull(stored.getGrantedBy());
        assertEquals(stored.getGrantedAt(), permissionEvents().getFirst().occurredAt());
    }

    @Test
    void grant_whenTheEventCannotBeRecorded_grantsNothing() {
        var failing = serviceWith(new FailingPermissionChangeStore(), membershipChanges, roleChanges);

        assertThrows(IllegalStateException.class, () -> failing.grant(grant("alice", "/acme/widgets"), BY_ADMIN));

        assertTrue(grants.findAll().isEmpty());
    }

    @Test
    void revoke_deletesTheGrant_andRecordsTheRevocationAgainstTheSameId() {
        RepoPermission p = grant("alice", "/acme/widgets");
        service.grant(p, BY_ADMIN);

        service.revoke(p, BY_ADMIN);

        assertTrue(grants.findAll().isEmpty());
        // Both events can share a millisecond, which the store orders by id, so compare without order.
        List<PermissionChangeEvent> events = permissionEvents();
        assertEquals(
                Set.of(PermissionChangeEvent.Action.GRANTED, PermissionChangeEvent.Action.REVOKED),
                events.stream().map(PermissionChangeEvent::action).collect(Collectors.toSet()));
        events.forEach(e -> assertEquals(p.getId(), e.permissionId()));
    }

    @Test
    void revoke_whenTheEventCannotBeRecorded_stillRemovesTheGrant() {
        RepoPermission p = grant("alice", "/acme/widgets");
        grants.save(p);
        var failing = serviceWith(new FailingPermissionChangeStore(), membershipChanges, roleChanges);

        assertThrows(IllegalStateException.class, () -> failing.revoke(p, BY_ADMIN));

        assertTrue(grants.findAll().isEmpty());
    }

    // ---- config reload ----

    @Test
    void reloadConfigPermissions_recordsOnlyTheDifference_atTheSourceRevision() {
        permissions.seedFromConfig(List.of(configGrant("alice", "/acme/a"), configGrant("bob", "/acme/b")));
        String droppedId = permissions.findByUsername("bob").getFirst().getId();

        // A reload builds every grant afresh with a new id, as the config loader does.
        service.reloadConfigPermissions(
                List.of(configGrant("alice", "/acme/a"), configGrant("bob", "/acme/c")),
                ChangeContext.config("4f2c9e1"));

        List<PermissionChangeEvent> events = permissionEvents();
        assertEquals(2, events.size());
        PermissionChangeEvent revoked = events.stream()
                .filter(e -> e.action() == PermissionChangeEvent.Action.REVOKED)
                .findFirst()
                .orElseThrow();
        assertEquals(droppedId, revoked.permissionId());
        assertEquals("/acme/b", revoked.value());
        PermissionChangeEvent granted = events.stream()
                .filter(e -> e.action() == PermissionChangeEvent.Action.GRANTED)
                .findFirst()
                .orElseThrow();
        assertEquals("/acme/c", granted.value());
        events.forEach(e -> {
            assertEquals(ChangeOrigin.CONFIG, e.origin());
            assertNull(e.actor());
            assertEquals("4f2c9e1", e.sourceRevision());
        });
    }

    @Test
    void reloadConfigPermissions_aConflictingConfig_recordsNothing() {
        RepoPermission a = grant("alice", "/acme/*", MatchType.GLOB, RepoPermission.Source.CONFIG);
        RepoPermission b = configGrant("alice", "/acme/widgets");

        assertThrows(
                IllegalStateException.class,
                () -> service.reloadConfigPermissions(List.of(a, b), ChangeContext.config("4f2c9e1")));

        assertTrue(permissionEvents().isEmpty());
        assertTrue(grants.findAll().isEmpty());
    }

    // ---- group rules ----

    @Test
    void addAndRemoveGroupRule_recordAgainstTheGroupName() {
        GroupPermissionRule r = rule("/acme/widgets");

        service.addGroupRule(devs, r, BY_ADMIN);
        service.removeGroupRule(devs, r, BY_ADMIN);

        assertTrue(groups.findRulesForGroup(devs.getId()).isEmpty());
        List<PermissionChangeEvent> events = permissionEvents();
        assertEquals(
                Set.of(PermissionChangeEvent.Action.GRANTED, PermissionChangeEvent.Action.REVOKED),
                events.stream().map(PermissionChangeEvent::action).collect(Collectors.toSet()));
        events.forEach(e -> {
            assertEquals(PermissionChangeEvent.SubjectType.GROUP, e.subjectType());
            assertEquals("devs", e.subject());
            assertEquals(r.getId(), e.permissionId());
            assertEquals(RepoPermission.Grant.REVIEW, e.grant());
        });
    }

    @Test
    void addGroupRule_whenTheEventCannotBeRecorded_grantsNothing() {
        var failing = serviceWith(new FailingPermissionChangeStore(), membershipChanges, roleChanges);

        assertThrows(IllegalStateException.class, () -> failing.addGroupRule(devs, rule("/acme/a"), BY_ADMIN));

        assertTrue(groups.findRulesForGroup(devs.getId()).isEmpty());
    }

    // ---- membership ----

    @Test
    void addAndRemoveMember_recordEach() {
        service.addMember(devs, "alice", BY_ADMIN);
        assertTrue(service.removeMember(devs, "alice", BY_ADMIN));

        assertTrue(groups.findMembers(devs.getId()).isEmpty());
        List<MembershipChangeEvent> events = membershipEvents();
        assertEquals(
                Set.of(Action.ADDED, Action.REMOVED),
                events.stream().map(MembershipChangeEvent::action).collect(Collectors.toSet()));
        events.forEach(e -> {
            assertEquals(devs.getId(), e.groupId());
            assertEquals("devs", e.groupName());
            assertEquals("alice", e.member());
            assertEquals("admin", e.actor());
        });
    }

    @Test
    void addMember_whenTheEventCannotBeRecorded_addsNobody() {
        var failing = serviceWith(permissionChanges, new FailingMembershipChangeStore(), roleChanges);

        assertThrows(IllegalStateException.class, () -> failing.addMember(devs, "alice", BY_ADMIN));

        assertTrue(groups.findMembers(devs.getId()).isEmpty());
    }

    @Test
    void removeMember_whenTheEventCannotBeRecorded_stillRemovesThem() {
        groups.addMember(devs.getId(), "alice");
        var failing = serviceWith(permissionChanges, new FailingMembershipChangeStore(), roleChanges);

        assertThrows(IllegalStateException.class, () -> failing.removeMember(devs, "alice", BY_ADMIN));

        assertTrue(groups.findMembers(devs.getId()).isEmpty());
    }

    @Test
    void removeMember_ofSomeoneNotInTheGroup_recordsNothing() {
        assertFalse(service.removeMember(devs, "alice", BY_ADMIN));

        assertTrue(membershipEvents().isEmpty());
    }

    // ---- removals that take access with them ----

    @Test
    void deleteGroup_recordsTheRevocationOfEachRule_andTheRemovalOfEachMember() {
        GroupPermissionRule r1 = rule("/acme/a");
        GroupPermissionRule r2 = rule("/acme/b");
        groups.saveRule(r1);
        groups.saveRule(r2);
        groups.addMember(devs.getId(), "alice");
        groups.addMember(devs.getId(), "bob");

        service.deleteGroup(devs, BY_ADMIN);

        assertTrue(groups.findGroupById(devs.getId()).isEmpty());
        assertEquals(
                Stream.of(r1.getId(), r2.getId()).sorted().toList(),
                permissionEvents().stream()
                        .filter(e -> e.action() == PermissionChangeEvent.Action.REVOKED)
                        .map(PermissionChangeEvent::permissionId)
                        .sorted()
                        .toList());
        assertEquals(
                List.of("alice", "bob"),
                membershipEvents().stream()
                        .filter(e -> e.action() == Action.REMOVED)
                        .map(MembershipChangeEvent::member)
                        .sorted()
                        .toList());
    }

    @Test
    void deleteUser_recordsEachGrantRevoked_eachMembershipRemoved_andTheRolesLost() {
        RepoPermission p = grant("alice", "/acme/widgets");
        grants.save(p);
        groups.addMember(devs.getId(), "alice");
        groups.addMember(devs.getId(), "bob");
        UserEntry alice = users.findByUsername("alice").orElseThrow();

        service.deleteUser(alice, BY_ADMIN);

        assertTrue(users.findByUsername("alice").isEmpty());
        assertTrue(grants.findByUsername("alice").isEmpty());
        assertEquals(List.of("bob"), groups.findMembers(devs.getId()));
        assertEquals(
                List.of(p.getId()),
                permissionEvents().stream()
                        .map(PermissionChangeEvent::permissionId)
                        .toList());
        assertEquals(
                List.of("alice"),
                membershipEvents().stream().map(MembershipChangeEvent::member).toList());
        RoleChangeEvent roles = roleEvents().getFirst();
        assertEquals(List.of("USER"), roles.previousRoles());
        assertEquals(List.of(), roles.roles());
    }

    // ---- roles ----

    private List<RoleChangeEvent> roleEvents() {
        return roleChanges.find(RoleChangeQuery.builder().newestFirst(false).build());
    }

    /** A store that refuses every write, standing in for an unavailable audit database. */
    private static final class FailingRoleChangeStore implements RoleChangeStore {
        @Override
        public void record(RoleChangeEvent event) {
            throw new IllegalStateException("audit store unavailable");
        }

        @Override
        public List<RoleChangeEvent> find(RoleChangeQuery query) {
            return List.of();
        }
    }

    @Test
    void createUser_recordsTheRolesTheyStartWith() {
        service.createUser("carol", "{noop}pw", List.of("USER", "ADMIN"), BY_ADMIN);

        assertEquals(
                List.of("ADMIN", "USER"),
                RoleChangeEvent.normalize(
                        users.findByUsername("carol").orElseThrow().getRoles()));
        RoleChangeEvent e = roleEvents().getFirst();
        assertEquals("carol", e.username());
        assertEquals(List.of(), e.previousRoles());
        assertEquals(List.of("ADMIN", "USER"), e.roles());
        assertEquals("admin", e.actor());
    }

    @Test
    void createUser_anExistingUser_isRefusedAndRecordsNothing() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.createUser("alice", "{noop}pw", List.of("ADMIN"), BY_ADMIN));

        assertTrue(roleEvents().isEmpty());
        assertEquals(
                List.of("USER"), users.findByUsername("alice").orElseThrow().getRoles());
    }

    @Test
    void createUser_whenTheEventCannotBeRecorded_createsNobody() {
        var failing = serviceWith(permissionChanges, membershipChanges, new FailingRoleChangeStore());

        assertThrows(
                IllegalStateException.class, () -> failing.createUser("carol", "{noop}pw", List.of("ADMIN"), BY_ADMIN));

        assertTrue(users.findByUsername("carol").isEmpty());
    }

    @Test
    void syncIdpRoles_aChange_isRecordedAgainstTheProvider() {
        service.syncIdpRoles("alice", List.of("USER", "ADMIN"), "oidc");

        RoleChangeEvent e = roleEvents().getFirst();
        assertEquals(ChangeOrigin.IDP, e.origin());
        assertEquals("oidc", e.actor());
        assertEquals(List.of("USER"), e.previousRoles());
        assertEquals(List.of("ADMIN", "USER"), e.roles());
    }

    @Test
    void syncIdpRoles_theSameRolesInAnotherOrder_recordsNothing() {
        users.upsertUser("alice", List.of("USER", "ADMIN"));

        service.syncIdpRoles("alice", List.of("ADMIN", "USER"), "oidc");

        assertTrue(roleEvents().isEmpty());
    }

    @Test
    void syncIdpRoles_aFirstSignIn_isRecordedFromNoRoles() {
        service.syncIdpRoles("dave", List.of("USER"), "ldap");

        assertEquals(List.of(), roleEvents().getFirst().previousRoles());
        assertTrue(users.findByUsername("dave").isPresent());
    }

    @Test
    void syncIdpRoles_aGain_whenTheEventCannotBeRecorded_keepsTheStoredRoles() {
        var failing = serviceWith(permissionChanges, membershipChanges, new FailingRoleChangeStore());

        assertThrows(IllegalStateException.class, () -> failing.syncIdpRoles("alice", List.of("ADMIN"), "oidc"));

        assertEquals(
                List.of("USER"), users.findByUsername("alice").orElseThrow().getRoles());
    }

    @Test
    void syncIdpRoles_aPureLoss_whenTheEventCannotBeRecorded_stillDropsTheRole() {
        users.upsertUser("alice", List.of("USER", "ADMIN"));
        var failing = serviceWith(permissionChanges, membershipChanges, new FailingRoleChangeStore());

        assertThrows(IllegalStateException.class, () -> failing.syncIdpRoles("alice", List.of("USER"), "oidc"));

        assertEquals(
                List.of("USER"), users.findByUsername("alice").orElseThrow().getRoles());
    }
}
