package com.rbc.fogwall.dashboard.controller;

import com.rbc.fogwall.db.model.MatchTarget;
import com.rbc.fogwall.db.model.MatchType;
import com.rbc.fogwall.permission.GroupPermissionStore;
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

@Tag(name = "Users", description = "User management — requires ROLE_ADMIN")
@RestController
@RequestMapping("/api/users/{username}/permissions")
public class PermissionController {

    @Autowired
    private RepoPermissionService permissionService;

    @Autowired
    private ReadOnlyUserStore userStore;

    @Operation(operationId = "listUserPermissions", summary = "List permissions for a user")
    @GetMapping
    public ResponseEntity<?> list(@PathVariable String username) {
        if (userStore.findByUsername(username).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(permissionService.findByUsername(username));
    }

    @Operation(
            operationId = "addUserPermission",
            summary = "Grant a permission to a user",
            description =
                    "target: SLUG (owner/repo slug, default), OWNER, REPO_FULL_PATH, PROVIDER. matchType: LITERAL (default), GLOB, REGEX. grant: PUSH (default), REVIEW, SELF_CERTIFY.")
    @PostMapping
    public ResponseEntity<?> add(@PathVariable String username, @RequestBody AddPermissionRequest req) {
        if (userStore.findByUsername(username).isEmpty()) {
            return ResponseEntity.notFound().build();
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

        var permission = RepoPermission.builder()
                .username(username)
                .provider(req.provider().trim())
                .target(target)
                .value(req.value().trim())
                .matchType(matchType)
                .grant(grant)
                .source(RepoPermission.Source.DB)
                .build();
        var conflict = permissionService.findConflict(permission);
        if (conflict.isPresent()) {
            var c = conflict.get();
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "error",
                            String.format(
                                    "Conflicts with existing permission: value '%s' (%s/%s)",
                                    c.getValue(), c.getTarget(), c.getMatchType())));
        }
        permissionService.save(permission);
        return ResponseEntity.status(HttpStatus.CREATED).body(permission);
    }

    @Operation(operationId = "deleteUserPermission", summary = "Revoke a permission from a user")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String username, @PathVariable String id) {
        if (userStore.findByUsername(username).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var existing = permissionService.findById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!username.equals(existing.get().getUsername())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Permission does not belong to this user"));
        }
        if (existing.get().getSource() == RepoPermission.Source.CONFIG) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Cannot delete config-defined permissions"));
        }
        permissionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "listUserGroups", summary = "List permission groups the user belongs to")
    @GetMapping("/groups")
    public ResponseEntity<?> listGroups(@PathVariable String username) {
        if (userStore.findByUsername(username).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        GroupPermissionStore groupStore = permissionService.getGroupStore();
        if (groupStore == null) {
            return ResponseEntity.ok(List.of());
        }
        List<UserGroupView> result = groupStore.findGroupIdsForUser(username).stream()
                .map(groupStore::findGroupById)
                .flatMap(java.util.Optional::stream)
                .map(g -> new UserGroupView(
                        g.getId(),
                        g.getName(),
                        g.getDescription(),
                        g.getSource().name(),
                        groupStore.findRulesForGroup(g.getId())))
                .toList();
        return ResponseEntity.ok(result);
    }

    public record AddPermissionRequest(String provider, String target, String value, String matchType, String grant) {}

    public record UserGroupView(String id, String name, String description, String source, List<?> rules) {}
}
