package com.rbc.fogwall.dashboard.export;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.model.MatchType;
import com.rbc.fogwall.permission.GroupPermissionRule;
import com.rbc.fogwall.permission.InMemoryGroupPermissionStore;
import com.rbc.fogwall.permission.InMemoryRepoPermissionStore;
import com.rbc.fogwall.permission.PermissionGroup;
import com.rbc.fogwall.permission.RepoPermission;
import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.user.StaticUserStore;
import com.rbc.fogwall.user.UserEntry;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GrantSnapshotTest {

    private InMemoryRepoPermissionStore grants;
    private InMemoryGroupPermissionStore groups;
    private RepoPermissionService permissions;
    private StaticUserStore users;

    @BeforeEach
    void setUp() {
        grants = new InMemoryRepoPermissionStore();
        groups = new InMemoryGroupPermissionStore();
        users = new StaticUserStore(List.of(
                UserEntry.builder().username("alice").build(),
                UserEntry.builder().username("bob").build(),
                UserEntry.builder().username("audrey").roles(List.of("AUDITOR")).build()));
        permissions = new RepoPermissionService(grants, groups, users);
    }

    @Test
    void holdings_listEachDirectGrant_andEachGroupRuleOncePerMember() {
        RepoPermission direct = RepoPermission.builder()
                .username("alice")
                .provider("github")
                .value("/acme/a")
                .matchType(MatchType.LITERAL)
                .grantedBy("admin")
                .grantedAt(Instant.parse("2026-11-05T14:00:00Z"))
                .build();
        grants.save(direct);
        PermissionGroup devs = PermissionGroup.builder().name("devs").build();
        groups.saveGroup(devs);
        groups.addMember(devs.getId(), "alice");
        groups.addMember(devs.getId(), "bob");
        GroupPermissionRule rule = GroupPermissionRule.builder()
                .groupId(devs.getId())
                .provider("github")
                .value("/acme/*")
                .grant(RepoPermission.Grant.REVIEW)
                .build();
        groups.saveRule(rule);

        List<GrantSnapshot.Holding> rows = GrantSnapshot.holdings(permissions, users);

        assertEquals(3, rows.size());
        GrantSnapshot.Holding aliceDirect = rows.stream()
                .filter(h -> h.via() == GrantSnapshot.Via.DIRECT)
                .findFirst()
                .orElseThrow();
        assertEquals("alice", aliceDirect.holder());
        assertEquals("admin", aliceDirect.grantedBy());
        assertEquals(direct.getId(), aliceDirect.permissionId());
        assertNull(aliceDirect.group());
        assertEquals(
                List.of("alice", "bob"),
                rows.stream()
                        .filter(h -> h.via() == GrantSnapshot.Via.GROUP)
                        .map(GrantSnapshot.Holding::holder)
                        .toList());
        rows.stream().filter(h -> h.via() == GrantSnapshot.Via.GROUP).forEach(h -> {
            assertEquals("devs", h.group());
            assertEquals(rule.getId(), h.permissionId());
            assertEquals(RepoPermission.Grant.REVIEW, h.grant());
        });
    }

    @Test
    void holdings_markAGrantInert_whenTheHoldersRolesCannotAct_orTheHolderIsUnknown() {
        for (String holder : List.of("audrey", "nobody", "bob")) {
            grants.save(RepoPermission.builder()
                    .username(holder)
                    .provider("github")
                    .value("/acme/a")
                    .build());
        }

        List<GrantSnapshot.Holding> rows = GrantSnapshot.holdings(permissions, users);

        assertEquals(
                List.of("audrey=false", "bob=true", "nobody=false"),
                rows.stream().map(h -> h.holder() + "=" + h.effective()).toList());
    }

    @Test
    void memberships_listEveryMemberOfEveryGroup() {
        PermissionGroup ops = PermissionGroup.builder().name("ops").build();
        PermissionGroup devs = PermissionGroup.builder().name("devs").build();
        groups.saveGroup(ops);
        groups.saveGroup(devs);
        groups.addMember(ops.getId(), "bob");
        groups.addMember(devs.getId(), "bob");
        groups.addMember(devs.getId(), "alice");

        assertEquals(
                List.of("devs/alice", "devs/bob", "ops/bob"),
                GrantSnapshot.memberships(permissions).stream()
                        .map(m -> m.group() + "/" + m.member())
                        .toList());
    }
}
