package com.rbc.fogwall.dashboard.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.db.model.MatchType;
import com.rbc.fogwall.permission.GroupPermissionRule;
import com.rbc.fogwall.permission.GroupPermissionStore;
import com.rbc.fogwall.permission.PermissionGroup;
import com.rbc.fogwall.permission.RepoPermission;
import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.user.ReadOnlyUserStore;
import com.rbc.fogwall.user.UserEntry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class GroupControllerTest {

    @InjectMocks
    GroupController controller;

    @Mock
    RepoPermissionService permissionService;

    @Mock
    ReadOnlyUserStore userStore;

    @Mock
    GroupPermissionStore groupStore;

    private static final PermissionGroup DB_GROUP = PermissionGroup.builder()
            .name("devs")
            .description("Developers")
            .source(PermissionGroup.Source.DB)
            .build();

    private static final PermissionGroup CONFIG_GROUP = PermissionGroup.builder()
            .name("admins")
            .description("Admins from config")
            .source(PermissionGroup.Source.CONFIG)
            .build();

    private static final UserEntry ALICE = UserEntry.builder()
            .username("alice")
            .passwordHash("{noop}pw")
            .emails(List.of())
            .scmIdentities(List.of())
            .roles(List.of("USER"))
            .build();

    @BeforeEach
    void setUp() {
        lenient().when(permissionService.getGroupStore()).thenReturn(groupStore);
    }

    // ── GET /api/groups ──────────────────────────────────────────────────────────

    @Test
    void list_returnsAllGroups() {
        when(groupStore.findAllGroups()).thenReturn(List.of(DB_GROUP));
        when(groupStore.findMembers(DB_GROUP.getId())).thenReturn(List.of("alice"));
        when(groupStore.findRulesForGroup(DB_GROUP.getId())).thenReturn(List.of());

        var resp = controller.list();

        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    // ── POST /api/groups ─────────────────────────────────────────────────────────

    @Test
    void create_missingName_returns400() {
        var resp = controller.create(new GroupController.CreateGroupRequest("  ", null));
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        verify(groupStore, never()).saveGroup(any());
    }

    @Test
    void create_duplicateName_returns400() {
        when(groupStore.findGroupByName("devs")).thenReturn(Optional.of(DB_GROUP));

        var resp = controller.create(new GroupController.CreateGroupRequest("devs", null));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        verify(groupStore, never()).saveGroup(any());
    }

    @Test
    void create_success_returns201() {
        when(groupStore.findGroupByName("newgroup")).thenReturn(Optional.empty());

        var resp = controller.create(new GroupController.CreateGroupRequest("newgroup", "A new group"));

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        verify(groupStore).saveGroup(any(PermissionGroup.class));
    }

    // ── GET /api/groups/{id} ─────────────────────────────────────────────────────

    @Test
    void get_unknownId_returns404() {
        when(groupStore.findGroupById("missing")).thenReturn(Optional.empty());

        var resp = controller.get("missing");

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    @Test
    void get_knownId_returnsDetail() {
        when(groupStore.findGroupById(DB_GROUP.getId())).thenReturn(Optional.of(DB_GROUP));
        when(groupStore.findMembers(DB_GROUP.getId())).thenReturn(List.of("alice"));
        when(groupStore.findRulesForGroup(DB_GROUP.getId())).thenReturn(List.of());

        var resp = controller.get(DB_GROUP.getId());

        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    // ── PATCH /api/groups/{id} ───────────────────────────────────────────────────

    @Test
    void update_unknownId_returns404() {
        when(groupStore.findGroupById("missing")).thenReturn(Optional.empty());

        var resp = controller.update("missing", new GroupController.CreateGroupRequest("newname", null));

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    @Test
    void update_configGroup_returns403() {
        when(groupStore.findGroupById(CONFIG_GROUP.getId())).thenReturn(Optional.of(CONFIG_GROUP));

        var resp = controller.update(CONFIG_GROUP.getId(), new GroupController.CreateGroupRequest("newname", null));

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        verify(groupStore, never()).updateGroup(any(), any(), any());
    }

    @Test
    void update_blankName_returns400() {
        when(groupStore.findGroupById(DB_GROUP.getId())).thenReturn(Optional.of(DB_GROUP));

        var resp = controller.update(DB_GROUP.getId(), new GroupController.CreateGroupRequest("", null));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void update_nameConflict_returns400() {
        when(groupStore.findGroupById(DB_GROUP.getId())).thenReturn(Optional.of(DB_GROUP));
        when(groupStore.findGroupByName("taken")).thenReturn(Optional.of(CONFIG_GROUP));

        var resp = controller.update(DB_GROUP.getId(), new GroupController.CreateGroupRequest("taken", null));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void update_success_callsUpdateAndReturnsDetail() {
        when(groupStore.findGroupById(DB_GROUP.getId()))
                .thenReturn(Optional.of(DB_GROUP))
                .thenReturn(Optional.of(DB_GROUP));
        when(groupStore.findGroupByName("renamed")).thenReturn(Optional.empty());
        when(groupStore.findMembers(DB_GROUP.getId())).thenReturn(List.of());
        when(groupStore.findRulesForGroup(DB_GROUP.getId())).thenReturn(List.of());

        var resp = controller.update(DB_GROUP.getId(), new GroupController.CreateGroupRequest("renamed", "new desc"));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        verify(groupStore).updateGroup(DB_GROUP.getId(), "renamed", "new desc");
    }

    // ── DELETE /api/groups/{id} ──────────────────────────────────────────────────

    @Test
    void delete_unknownId_returns404() {
        when(groupStore.findGroupById("missing")).thenReturn(Optional.empty());

        var resp = controller.delete("missing");

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    @Test
    void delete_configGroup_returns403() {
        when(groupStore.findGroupById(CONFIG_GROUP.getId())).thenReturn(Optional.of(CONFIG_GROUP));

        var resp = controller.delete(CONFIG_GROUP.getId());

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        verify(groupStore, never()).deleteGroup(any());
    }

    @Test
    void delete_dbGroup_returns204() {
        when(groupStore.findGroupById(DB_GROUP.getId())).thenReturn(Optional.of(DB_GROUP));

        var resp = controller.delete(DB_GROUP.getId());

        assertEquals(HttpStatus.NO_CONTENT, resp.getStatusCode());
        verify(groupStore).deleteGroup(DB_GROUP.getId());
    }

    // ── POST /api/groups/{id}/members ────────────────────────────────────────────

    @Test
    void addMember_unknownGroup_returns404() {
        when(groupStore.findGroupById("missing")).thenReturn(Optional.empty());

        var resp = controller.addMember("missing", new GroupController.MemberRequest("alice"));

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    @Test
    void addMember_configGroup_returns403() {
        when(groupStore.findGroupById(CONFIG_GROUP.getId())).thenReturn(Optional.of(CONFIG_GROUP));

        var resp = controller.addMember(CONFIG_GROUP.getId(), new GroupController.MemberRequest("alice"));

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    void addMember_unknownUser_returns400() {
        when(groupStore.findGroupById(DB_GROUP.getId())).thenReturn(Optional.of(DB_GROUP));
        when(userStore.findByUsername("nobody")).thenReturn(Optional.empty());

        var resp = controller.addMember(DB_GROUP.getId(), new GroupController.MemberRequest("nobody"));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void addMember_alreadyMember_returns400() {
        when(groupStore.findGroupById(DB_GROUP.getId())).thenReturn(Optional.of(DB_GROUP));
        when(userStore.findByUsername("alice")).thenReturn(Optional.of(ALICE));
        when(groupStore.findMembers(DB_GROUP.getId())).thenReturn(List.of("alice"));

        var resp = controller.addMember(DB_GROUP.getId(), new GroupController.MemberRequest("alice"));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        verify(groupStore, never()).addMember(any(), any());
    }

    @Test
    void addMember_success_returns201() {
        when(groupStore.findGroupById(DB_GROUP.getId())).thenReturn(Optional.of(DB_GROUP));
        when(userStore.findByUsername("alice")).thenReturn(Optional.of(ALICE));
        when(groupStore.findMembers(DB_GROUP.getId())).thenReturn(List.of());

        var resp = controller.addMember(DB_GROUP.getId(), new GroupController.MemberRequest("alice"));

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        verify(groupStore).addMember(DB_GROUP.getId(), "alice");
    }

    // ── DELETE /api/groups/{id}/members/{username} ───────────────────────────────

    @Test
    void removeMember_configGroup_returns403() {
        when(groupStore.findGroupById(CONFIG_GROUP.getId())).thenReturn(Optional.of(CONFIG_GROUP));

        var resp = controller.removeMember(CONFIG_GROUP.getId(), "alice");

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        verify(groupStore, never()).removeMember(any(), any());
    }

    @Test
    void removeMember_success_returns204() {
        when(groupStore.findGroupById(DB_GROUP.getId())).thenReturn(Optional.of(DB_GROUP));

        var resp = controller.removeMember(DB_GROUP.getId(), "alice");

        assertEquals(HttpStatus.NO_CONTENT, resp.getStatusCode());
        verify(groupStore).removeMember(DB_GROUP.getId(), "alice");
    }

    // ── POST /api/groups/{id}/permissions ────────────────────────────────────────

    private GroupController.AddRuleRequest ruleReq(String provider, String value, String matchType, String grant) {
        return new GroupController.AddRuleRequest(provider, null, value, matchType, grant);
    }

    @Test
    void addRule_configGroup_returns403() {
        when(groupStore.findGroupById(CONFIG_GROUP.getId())).thenReturn(Optional.of(CONFIG_GROUP));

        var resp = controller.addRule(CONFIG_GROUP.getId(), ruleReq("github", "/a/b", null, null));

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    void addRule_missingProvider_returns400() {
        when(groupStore.findGroupById(DB_GROUP.getId())).thenReturn(Optional.of(DB_GROUP));

        var resp = controller.addRule(DB_GROUP.getId(), ruleReq("", "/a/b", null, null));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void addRule_invalidMatchType_returns400() {
        when(groupStore.findGroupById(DB_GROUP.getId())).thenReturn(Optional.of(DB_GROUP));

        var resp = controller.addRule(DB_GROUP.getId(), ruleReq("github", "/a/b", "WILDCARD", null));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void addRule_success_returns201() {
        when(groupStore.findGroupById(DB_GROUP.getId())).thenReturn(Optional.of(DB_GROUP));

        var resp = controller.addRule(DB_GROUP.getId(), ruleReq("github", "/acme/repo", "LITERAL", "PUSH"));

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        verify(groupStore).saveRule(any(GroupPermissionRule.class));
    }

    // ── DELETE /api/groups/{id}/permissions/{ruleId} ─────────────────────────────

    @Test
    void deleteRule_configGroup_returns403() {
        when(groupStore.findGroupById(CONFIG_GROUP.getId())).thenReturn(Optional.of(CONFIG_GROUP));

        var resp = controller.deleteRule(CONFIG_GROUP.getId(), "some-rule-id");

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        verify(groupStore, never()).deleteRule(any());
    }

    @Test
    void deleteRule_ruleNotFound_returns404() {
        when(groupStore.findGroupById(DB_GROUP.getId())).thenReturn(Optional.of(DB_GROUP));
        when(groupStore.findRuleById("missing")).thenReturn(Optional.empty());

        var resp = controller.deleteRule(DB_GROUP.getId(), "missing");

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    @Test
    void deleteRule_ruleBelongsToDifferentGroup_returns403() {
        GroupPermissionRule rule = GroupPermissionRule.builder()
                .groupId("other-group-id")
                .provider("github")
                .value("/a/b")
                .matchType(MatchType.LITERAL)
                .grant(RepoPermission.Grant.PUSH)
                .build();
        when(groupStore.findGroupById(DB_GROUP.getId())).thenReturn(Optional.of(DB_GROUP));
        when(groupStore.findRuleById(rule.getId())).thenReturn(Optional.of(rule));

        var resp = controller.deleteRule(DB_GROUP.getId(), rule.getId());

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        verify(groupStore, never()).deleteRule(any());
    }

    @Test
    void deleteRule_success_returns204() {
        GroupPermissionRule rule = GroupPermissionRule.builder()
                .groupId(DB_GROUP.getId())
                .provider("github")
                .value("/acme/repo")
                .matchType(MatchType.LITERAL)
                .grant(RepoPermission.Grant.PUSH)
                .build();
        when(groupStore.findGroupById(DB_GROUP.getId())).thenReturn(Optional.of(DB_GROUP));
        when(groupStore.findRuleById(rule.getId())).thenReturn(Optional.of(rule));

        var resp = controller.deleteRule(DB_GROUP.getId(), rule.getId());

        assertEquals(HttpStatus.NO_CONTENT, resp.getStatusCode());
        verify(groupStore).deleteRule(rule.getId());
    }
}
