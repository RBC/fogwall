package com.rbc.fogwall.dashboard.controller;

import com.rbc.fogwall.db.model.MatchTarget;
import com.rbc.fogwall.db.model.MatchType;
import com.rbc.fogwall.permission.GroupPermissionRule;
import com.rbc.fogwall.permission.GroupPermissionStore;
import com.rbc.fogwall.permission.PermissionGroup;
import com.rbc.fogwall.permission.RepoPermission;
import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.user.ReadOnlyUserStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Groups", description = "Permission group management — requires ROLE_ADMIN")
@RestController
@RequestMapping("/api/groups")
public class GroupController {

    @Autowired
    private RepoPermissionService permissionService;

    @Autowired
    private ReadOnlyUserStore userStore;

    private GroupPermissionStore groupStore() {
        return permissionService.getGroupStore();
    }

    // ---- groups ----

    @Operation(operationId = "listGroups", summary = "List all permission groups")
    @GetMapping
    public ResponseEntity<?> list() {
        List<PermissionGroup> groups = groupStore().findAllGroups();
        List<GroupSummary> summaries = groups.stream()
                .map(g -> new GroupSummary(
                        g.getId(),
                        g.getName(),
                        g.getDescription(),
                        g.getSource().name(),
                        groupStore().findMembers(g.getId()).size(),
                        groupStore().findRulesForGroup(g.getId()).size()))
                .toList();
        return ResponseEntity.ok(summaries);
    }

    @Operation(operationId = "createGroup", summary = "Create a permission group")
    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateGroupRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "name is required"));
        }
        if (groupStore().findGroupByName(req.name().trim()).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "group name already exists: " + req.name()));
        }
        PermissionGroup group = PermissionGroup.builder()
                .name(req.name().trim())
                .description(req.description() != null ? req.description().trim() : null)
                .source(PermissionGroup.Source.DB)
                .build();
        groupStore().saveGroup(group);
        return ResponseEntity.status(HttpStatus.CREATED).body(group);
    }

    @Operation(operationId = "getGroup", summary = "Get group details including members and rules")
    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable String id) {
        return groupStore()
                .findGroupById(id)
                .map(g -> ResponseEntity.ok(new GroupDetail(
                        g.getId(),
                        g.getName(),
                        g.getDescription(),
                        g.getSource().name(),
                        groupStore().findMembers(g.getId()),
                        groupStore().findRulesForGroup(g.getId()))))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(operationId = "updateGroup", summary = "Update a group's name and description")
    @PatchMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id, @RequestBody CreateGroupRequest req) {
        var existing = groupStore().findGroupById(id);
        if (existing.isEmpty()) return ResponseEntity.notFound().build();
        if (existing.get().getSource() == PermissionGroup.Source.CONFIG) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Cannot modify config-defined groups"));
        }
        if (req.name() == null || req.name().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "name is required"));
        }
        var nameConflict = groupStore().findGroupByName(req.name().trim());
        if (nameConflict.isPresent() && !nameConflict.get().getId().equals(id)) {
            return ResponseEntity.badRequest().body(Map.of("error", "group name already exists: " + req.name()));
        }
        groupStore()
                .updateGroup(
                        id,
                        req.name().trim(),
                        req.description() != null ? req.description().trim() : null);
        return groupStore()
                .findGroupById(id)
                .map(g -> ResponseEntity.ok(new GroupDetail(
                        g.getId(),
                        g.getName(),
                        g.getDescription(),
                        g.getSource().name(),
                        groupStore().findMembers(g.getId()),
                        groupStore().findRulesForGroup(g.getId()))))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(operationId = "deleteGroup", summary = "Delete a permission group")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        var existing = groupStore().findGroupById(id);
        if (existing.isEmpty()) return ResponseEntity.notFound().build();
        if (existing.get().getSource() == PermissionGroup.Source.CONFIG) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Cannot delete config-defined groups"));
        }
        groupStore().deleteGroup(id);
        return ResponseEntity.noContent().build();
    }

    // ---- members ----

    @Operation(operationId = "addGroupMember", summary = "Add a user to a group")
    @PostMapping("/{id}/members")
    public ResponseEntity<?> addMember(@PathVariable String id, @RequestBody MemberRequest req) {
        var group = groupStore().findGroupById(id);
        if (group.isEmpty()) return ResponseEntity.notFound().build();
        if (group.get().getSource() == PermissionGroup.Source.CONFIG) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Cannot modify members of config-defined groups"));
        }
        if (req.username() == null || req.username().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "username is required"));
        }
        if (userStore.findByUsername(req.username()).isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "user not found: " + req.username()));
        }
        List<String> current = groupStore().findMembers(id);
        if (current.contains(req.username())) {
            return ResponseEntity.badRequest().body(Map.of("error", "user already a member of this group"));
        }
        groupStore().addMember(id, req.username());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("username", req.username()));
    }

    @Operation(operationId = "removeGroupMember", summary = "Remove a user from a group")
    @DeleteMapping("/{id}/members/{username}")
    public ResponseEntity<?> removeMember(@PathVariable String id, @PathVariable String username) {
        var group = groupStore().findGroupById(id);
        if (group.isEmpty()) return ResponseEntity.notFound().build();
        if (group.get().getSource() == PermissionGroup.Source.CONFIG) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Cannot modify members of config-defined groups"));
        }
        groupStore().removeMember(id, username);
        return ResponseEntity.noContent().build();
    }

    // ---- rules ----

    @Operation(operationId = "listGroupPermissions", summary = "List permission rules for a group")
    @GetMapping("/{id}/permissions")
    public ResponseEntity<?> listRules(@PathVariable String id) {
        if (groupStore().findGroupById(id).isEmpty())
            return ResponseEntity.notFound().build();
        return ResponseEntity.ok(groupStore().findRulesForGroup(id));
    }

    @Operation(operationId = "addGroupPermission", summary = "Add a permission rule to a group")
    @PostMapping("/{id}/permissions")
    public ResponseEntity<?> addRule(@PathVariable String id, @RequestBody AddRuleRequest req) {
        var group = groupStore().findGroupById(id);
        if (group.isEmpty()) return ResponseEntity.notFound().build();
        if (group.get().getSource() == PermissionGroup.Source.CONFIG) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Cannot modify rules of config-defined groups"));
        }
        if (req.provider() == null || req.provider().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "provider is required"));
        }
        if (req.value() == null || req.value().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "value is required"));
        }

        MatchTarget target;
        try {
            target = req.target() != null ? MatchTarget.valueOf(req.target().toUpperCase()) : MatchTarget.SLUG;
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid target: " + req.target()));
        }

        MatchType matchType;
        try {
            matchType =
                    req.matchType() != null ? MatchType.valueOf(req.matchType().toUpperCase()) : MatchType.LITERAL;
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid matchType: " + req.matchType()));
        }

        RepoPermission.Grant grant;
        try {
            grant = req.grant() != null
                    ? RepoPermission.Grant.valueOf(req.grant().toUpperCase())
                    : RepoPermission.Grant.PUSH;
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid grant: " + req.grant()));
        }

        GroupPermissionRule rule = GroupPermissionRule.builder()
                .groupId(id)
                .provider(req.provider().trim())
                .target(target)
                .value(req.value().trim())
                .matchType(matchType)
                .grant(grant)
                .build();
        groupStore().saveRule(rule);
        return ResponseEntity.status(HttpStatus.CREATED).body(rule);
    }

    @Operation(operationId = "deleteGroupPermission", summary = "Remove a permission rule from a group")
    @DeleteMapping("/{id}/permissions/{ruleId}")
    public ResponseEntity<?> deleteRule(@PathVariable String id, @PathVariable String ruleId) {
        var group = groupStore().findGroupById(id);
        if (group.isEmpty()) return ResponseEntity.notFound().build();
        if (group.get().getSource() == PermissionGroup.Source.CONFIG) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Cannot modify rules of config-defined groups"));
        }
        var rule = groupStore().findRuleById(ruleId);
        if (rule.isEmpty()) return ResponseEntity.notFound().build();
        if (!id.equals(rule.get().getGroupId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Rule does not belong to this group"));
        }
        groupStore().deleteRule(ruleId);
        return ResponseEntity.noContent().build();
    }

    // ---- DTOs ----

    public record CreateGroupRequest(String name, String description) {}

    public record MemberRequest(String username) {}

    public record AddRuleRequest(String provider, String target, String value, String matchType, String grant) {}

    public record GroupSummary(
            String id, String name, String description, String source, int memberCount, int ruleCount) {}

    public record GroupDetail(
            String id,
            String name,
            String description,
            String source,
            List<String> members,
            List<GroupPermissionRule> rules) {}
}
