package com.rbc.fogwall.dashboard.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rbc.fogwall.db.model.MatchTarget;
import com.rbc.fogwall.db.model.MatchType;
import com.rbc.fogwall.permission.RepoPermission;
import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.user.ReadOnlyUserStore;
import com.rbc.fogwall.user.UserEntry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class PermissionControllerTest {

    @InjectMocks
    PermissionController controller;

    @Mock
    RepoPermissionService permissionService;

    @Mock
    ReadOnlyUserStore userStore;

    private static final UserEntry ALICE = UserEntry.builder()
            .username("alice")
            .passwordHash("{noop}pw")
            .emails(List.of())
            .scmIdentities(List.of())
            .roles(List.of("USER"))
            .build();

    private static final RepoPermission DB_PERM = RepoPermission.builder()
            .username("alice")
            .provider("github")
            .target(MatchTarget.SLUG)
            .value("/acme/repo")
            .matchType(MatchType.LITERAL)
            .operations(RepoPermission.Operations.PUSH)
            .source(RepoPermission.Source.DB)
            .build();

    private static final RepoPermission CONFIG_PERM = RepoPermission.builder()
            .username("alice")
            .provider("github")
            .target(MatchTarget.OWNER)
            .value("acme")
            .matchType(MatchType.GLOB)
            .operations(RepoPermission.Operations.PUSH_AND_REVIEW)
            .source(RepoPermission.Source.CONFIG)
            .build();

    // ── GET /api/users/{username}/permissions ────────────────────────────────────

    @Test
    void list_unknownUser_returns404() {
        when(userStore.findByUsername("nobody")).thenReturn(Optional.empty());

        var resp = controller.list("nobody");

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    @Test
    void list_knownUser_returnsPermissions() {
        when(userStore.findByUsername("alice")).thenReturn(Optional.of(ALICE));
        when(permissionService.findByUsername("alice")).thenReturn(List.of(DB_PERM));

        var resp = controller.list("alice");

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(List.of(DB_PERM), resp.getBody());
    }

    // ── POST /api/users/{username}/permissions ───────────────────────────────────

    private static PermissionController.AddPermissionRequest req(
            String provider, String target, String value, String matchType, String operations) {
        return new PermissionController.AddPermissionRequest(provider, target, value, matchType, operations);
    }

    @Test
    void add_unknownUser_returns404() {
        when(userStore.findByUsername("nobody")).thenReturn(Optional.empty());

        var resp = controller.add("nobody", req("github", null, "/a/b", null, null));

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        verify(permissionService, never()).save(any());
    }

    @Test
    void add_missingProvider_returns400() {
        when(userStore.findByUsername("alice")).thenReturn(Optional.of(ALICE));

        var resp = controller.add("alice", req("", null, "/a/b", null, null));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void add_missingValue_returns400() {
        when(userStore.findByUsername("alice")).thenReturn(Optional.of(ALICE));

        var resp = controller.add("alice", req("github", null, "  ", null, null));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void add_invalidMatchType_returns400() {
        when(userStore.findByUsername("alice")).thenReturn(Optional.of(ALICE));

        var resp = controller.add("alice", req("github", null, "/a/b", "WILDCARD", null));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void add_invalidTarget_returns400() {
        when(userStore.findByUsername("alice")).thenReturn(Optional.of(ALICE));

        var resp = controller.add("alice", req("github", "BRANCH", "/a/b", null, null));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void add_invalidOperations_returns400() {
        when(userStore.findByUsername("alice")).thenReturn(Optional.of(ALICE));

        var resp = controller.add("alice", req("github", null, "/a/b", null, "READ"));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void add_defaults_slugLiteralAndPush() {
        when(userStore.findByUsername("alice")).thenReturn(Optional.of(ALICE));
        var captor = ArgumentCaptor.forClass(RepoPermission.class);

        var resp = controller.add("alice", req("github", null, "/a/b", null, null));

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        verify(permissionService).save(captor.capture());
        var saved = captor.getValue();
        assertEquals("alice", saved.getUsername());
        assertEquals("github", saved.getProvider());
        assertEquals("/a/b", saved.getValue());
        assertEquals(MatchTarget.SLUG, saved.getTarget());
        assertEquals(MatchType.LITERAL, saved.getMatchType());
        assertEquals(RepoPermission.Operations.PUSH, saved.getOperations());
        assertEquals(RepoPermission.Source.DB, saved.getSource());
    }

    @Test
    void add_conflictingPermission_returns400() {
        when(userStore.findByUsername("alice")).thenReturn(Optional.of(ALICE));
        when(permissionService.findConflict(any())).thenReturn(Optional.of(CONFIG_PERM));

        var resp = controller.add("alice", req("github", "SLUG", "/acme/repo", "LITERAL", "PUSH"));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        verify(permissionService, never()).save(any());
    }

    @Test
    void add_explicitOwnerGlobAndPush_saved() {
        when(userStore.findByUsername("alice")).thenReturn(Optional.of(ALICE));
        var captor = ArgumentCaptor.forClass(RepoPermission.class);

        var resp = controller.add("alice", req("github", "OWNER", "acme", "GLOB", "PUSH"));

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        verify(permissionService).save(captor.capture());
        var saved = captor.getValue();
        assertEquals(MatchTarget.OWNER, saved.getTarget());
        assertEquals(MatchType.GLOB, saved.getMatchType());
        assertEquals(RepoPermission.Operations.PUSH, saved.getOperations());
    }

    // ── DELETE /api/users/{username}/permissions/{id} ────────────────────────────

    @Test
    void delete_unknownUser_returns404() {
        when(userStore.findByUsername("nobody")).thenReturn(Optional.empty());

        var resp = controller.delete("nobody", "some-id");

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        verify(permissionService, never()).delete(any());
    }

    @Test
    void delete_unknownPermission_returns404() {
        when(userStore.findByUsername("alice")).thenReturn(Optional.of(ALICE));
        when(permissionService.findById("missing-id")).thenReturn(Optional.empty());

        var resp = controller.delete("alice", "missing-id");

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    @Test
    void delete_permissionBelongsToDifferentUser_returns403() {
        when(userStore.findByUsername("alice")).thenReturn(Optional.of(ALICE));
        var bobPerm = RepoPermission.builder()
                .username("bob")
                .provider("github")
                .target(MatchTarget.SLUG)
                .value("/a/b")
                .matchType(MatchType.LITERAL)
                .source(RepoPermission.Source.DB)
                .build();
        when(permissionService.findById(bobPerm.getId())).thenReturn(Optional.of(bobPerm));

        var resp = controller.delete("alice", bobPerm.getId());

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        verify(permissionService, never()).delete(any());
    }

    @Test
    void delete_configSourced_returns403() {
        when(userStore.findByUsername("alice")).thenReturn(Optional.of(ALICE));
        when(permissionService.findById(CONFIG_PERM.getId())).thenReturn(Optional.of(CONFIG_PERM));

        var resp = controller.delete("alice", CONFIG_PERM.getId());

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        verify(permissionService, never()).delete(any());
    }

    @Test
    void delete_dbSourced_returns204() {
        when(userStore.findByUsername("alice")).thenReturn(Optional.of(ALICE));
        when(permissionService.findById(DB_PERM.getId())).thenReturn(Optional.of(DB_PERM));

        var resp = controller.delete("alice", DB_PERM.getId());

        assertEquals(HttpStatus.NO_CONTENT, resp.getStatusCode());
        verify(permissionService).delete(DB_PERM.getId());
    }
}
