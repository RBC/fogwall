package com.rbc.fogwall.dashboard.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rbc.fogwall.dashboard.export.ExportFields;
import com.rbc.fogwall.dashboard.export.ExportZip;
import com.rbc.fogwall.dashboard.export.GrantSnapshot;
import com.rbc.fogwall.db.FetchStore;
import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.ScmApiActionStore;
import com.rbc.fogwall.db.model.MatchTarget;
import com.rbc.fogwall.db.model.MatchType;
import com.rbc.fogwall.permission.AccessChangeService;
import com.rbc.fogwall.permission.ChangeOrigin;
import com.rbc.fogwall.permission.InMemoryGroupPermissionStore;
import com.rbc.fogwall.permission.InMemoryMembershipChangeStore;
import com.rbc.fogwall.permission.InMemoryPermissionChangeStore;
import com.rbc.fogwall.permission.InMemoryRepoPermissionStore;
import com.rbc.fogwall.permission.InMemoryRoleChangeStore;
import com.rbc.fogwall.permission.PermissionChangeEvent;
import com.rbc.fogwall.permission.PermissionGroup;
import com.rbc.fogwall.permission.RepoPermission;
import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.permission.RoleChangeEvent;
import com.rbc.fogwall.user.ReadOnlyUserStore;
import com.rbc.fogwall.user.UserEntry;
import com.rbc.fogwall.user.UserStore;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

/** {@link AuditController} over a real {@link AccessChangeService} on in-memory stores. */
class AuditControllerTest {

    private static final Instant T0 = Instant.parse("2026-11-05T14:00:00Z");

    private InMemoryPermissionChangeStore permissionChanges;
    private InMemoryRoleChangeStore roleChanges;
    private InMemoryRepoPermissionStore grants;
    private InMemoryGroupPermissionStore groups;
    private ReadOnlyUserStore users;
    private AuditController controller;

    @BeforeEach
    void setUp() {
        permissionChanges = new InMemoryPermissionChangeStore();
        roleChanges = new InMemoryRoleChangeStore();
        grants = new InMemoryRepoPermissionStore();
        groups = new InMemoryGroupPermissionStore();
        users = mock(ReadOnlyUserStore.class);
        var permissions = new RepoPermissionService(grants, groups, users);
        controller = new AuditController(
                new AccessChangeService(
                        permissions,
                        mock(UserStore.class),
                        permissionChanges,
                        new InMemoryMembershipChangeStore(),
                        roleChanges),
                permissions,
                users,
                mock(PushStore.class),
                mock(ScmApiActionStore.class),
                mock(FetchStore.class));
    }

    private void grant(String id, Instant at, String value, MatchType matchType) {
        permissionChanges.record(PermissionChangeEvent.builder()
                .id(id)
                .occurredAt(at)
                .action(PermissionChangeEvent.Action.GRANTED)
                .origin(ChangeOrigin.DASHBOARD)
                .actor("admin")
                .subjectType(PermissionChangeEvent.SubjectType.USER)
                .subject("alice")
                .permissionId("perm-" + id)
                .provider("github")
                .target(MatchTarget.SLUG)
                .value(value)
                .matchType(matchType)
                .grant(RepoPermission.Grant.PUSH)
                .build());
    }

    /** Calls the permission change listing with only the named parameters set, as {@code name, value} pairs. */
    private ResponseEntity<List<PermissionChangeEvent>> permissionChanges(String... nameValues) {
        Map<String, String> p = pairs(nameValues);
        return controller.permissionChanges(
                p.get("action"),
                p.get("origin"),
                p.get("subjectType"),
                p.get("subject"),
                p.get("user"),
                p.get("provider"),
                p.get("owner"),
                p.get("repo"),
                p.get("search"),
                p.get("from"),
                p.get("to"),
                p.get("after"),
                Integer.parseInt(p.getOrDefault("limit", "50")),
                Integer.parseInt(p.getOrDefault("offset", "0")),
                true);
    }

    private static Map<String, String> pairs(String... nameValues) {
        Map<String, String> p = new HashMap<>();
        for (int i = 0; i < nameValues.length; i += 2) {
            p.put(nameValues[i], nameValues[i + 1]);
        }
        return p;
    }

    private static List<String> ids(ResponseEntity<List<PermissionChangeEvent>> page) {
        return page.getBody().stream().map(PermissionChangeEvent::id).toList();
    }

    private static void assertBadRequest(Runnable call) {
        var ex = assertThrows(ResponseStatusException.class, call::run);
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void ownerAndRepo_findTheGrantsWhosePatternAppliesToThatRepository() {
        grant("exact", T0, "/acme/widgets", MatchType.LITERAL);
        grant("glob", T0.plusSeconds(1), "/acme/*", MatchType.GLOB);
        grant("other", T0.plusSeconds(2), "/other/widgets", MatchType.LITERAL);
        grant("sibling", T0.plusSeconds(3), "/acme/gadgets", MatchType.LITERAL);

        assertEquals(List.of("glob", "exact"), ids(permissionChanges("owner", "acme", "repo", "widgets")));
    }

    @Test
    void ownerAndRepo_pageExactlyAcrossScanBatches_withTheCursorResumingAfterTheLastMatch() {
        // Matches are sparse and spread past several store batches; each page must still be full and the walk exact.
        List<String> expected = new ArrayList<>();
        int total = AuditController.REPO_SCAN_BATCH * 3;
        for (int i = 0; i < total; i++) {
            boolean match = i % 250 == 0;
            String id = String.format("e-%05d", i);
            grant(id, T0.plusSeconds(i), match ? "/acme/widgets" : "/acme/other-" + i, MatchType.LITERAL);
            if (match) {
                expected.addFirst(id);
            }
        }

        List<String> walked = new ArrayList<>();
        String after = null;
        while (true) {
            var page = after == null
                    ? permissionChanges("owner", "acme", "repo", "widgets", "limit", "2")
                    : permissionChanges("owner", "acme", "repo", "widgets", "limit", "2", "after", after);
            walked.addAll(ids(page));
            after = page.getHeaders().getFirst(ListingParams.NEXT_CURSOR_HEADER);
            if (after == null) {
                break;
            }
            assertEquals(2, page.getBody().size(), "a page with a next cursor is full");
        }
        assertEquals(expected, walked);
    }

    @Test
    void ownerOrRepoAlone_isA400_onPermissionChanges() {
        assertBadRequest(() -> permissionChanges("owner", "acme"));
        assertBadRequest(() -> permissionChanges("repo", "widgets"));
    }

    @Test
    void unknownEnumValues_areA400() {
        assertBadRequest(() -> permissionChanges("action", "MODIFIED"));
        assertBadRequest(() -> permissionChanges("origin", "EMAIL"));
        assertBadRequest(() -> permissionChanges("subjectType", "ROBOT"));
    }

    @Test
    void withoutARepository_theStoreFiltersAndPagesDirectly() {
        grant("a", T0, "/acme/a", MatchType.LITERAL);
        grant("b", T0.plusSeconds(1), "/acme/b", MatchType.LITERAL);
        grant("c", T0.plusSeconds(2), "/acme/c", MatchType.LITERAL);

        var first = permissionChanges("limit", "2");
        assertEquals(List.of("c", "b"), ids(first));
        var second =
                permissionChanges("limit", "2", "after", first.getHeaders().getFirst(ListingParams.NEXT_CURSOR_HEADER));
        assertEquals(List.of("a"), ids(second));
        assertNull(second.getHeaders().getFirst(ListingParams.NEXT_CURSOR_HEADER));
    }

    @Test
    void membershipAndRoleChanges_refuseRepositoryFilters() {
        assertBadRequest(() -> controller.membershipChanges(
                null, null, null, null, null, "github", null, null, null, null, null, null, 50, 0, true));
        assertBadRequest(() -> controller.membershipChanges(
                null, null, null, null, null, null, "acme", null, null, null, null, null, 50, 0, true));
        assertBadRequest(() ->
                controller.roleChanges(null, null, null, null, null, "widgets", null, null, null, null, 50, 0, true));
    }

    // ---- export ----

    /** A response whose body is captured, and a request carrying {@code params}. */
    private record Exchange(HttpServletRequest request, HttpServletResponse response, ByteArrayOutputStream body) {
        static Exchange of(Map<String, String> params) throws IOException {
            var body = new ByteArrayOutputStream();
            HttpServletRequest request = mock(HttpServletRequest.class);
            Map<String, String[]> parameterMap = new HashMap<>();
            params.forEach((k, v) -> parameterMap.put(k, new String[] {v}));
            when(request.getParameterMap()).thenReturn(parameterMap);
            HttpServletResponse response = mock(HttpServletResponse.class);
            when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener listener) {}

                @Override
                public void write(int b) {
                    body.write(b);
                }
            });
            return new Exchange(request, response, body);
        }
    }

    @Test
    void exportGrants_writesTheSnapshotFilteredToTheRepository_withAManifest() throws Exception {
        when(users.findByUsername("alice"))
                .thenReturn(Optional.of(UserEntry.builder().username("alice").build()));
        grants.save(RepoPermission.builder()
                .username("alice")
                .provider("github")
                .value("/acme/widgets")
                .matchType(MatchType.LITERAL)
                .grantedBy("admin")
                .build());
        grants.save(RepoPermission.builder()
                .username("alice")
                .provider("github")
                .value("/other/thing")
                .matchType(MatchType.LITERAL)
                .build());
        var exchange = Exchange.of(Map.of("owner", "acme", "repo", "widgets", "columns", "holder,value,effective"));

        controller.exportGrants(
                null,
                null,
                null,
                null,
                null,
                "acme",
                "widgets",
                null,
                "holder,value,effective",
                exchange.request(),
                exchange.response());

        verify(exchange.response()).setContentType("application/zip");
        ExportZip zip = ExportZip.read(exchange.body().toByteArray());
        assertEquals(
                "holder,value,effective\r\nalice,/acme/widgets,true\r\n",
                zip.entries().get("grants/part-00001.csv"));
        var manifest = zip.manifest();
        assertEquals("acme", manifest.get("filters").get("owner").asString());
        assertNull(manifest.get("filters").get("columns"), "columns shape the file and are not a filter");
        assertTrue(manifest.get("types").get(0).get("snapshot").asBoolean());
    }

    @Test
    void grants_listOnePageOfTheSnapshot_withTheFilteredTotal() {
        for (String holder : List.of("alice", "bob", "carol")) {
            when(users.findByUsername(holder))
                    .thenReturn(Optional.of(UserEntry.builder().username(holder).build()));
            grants.save(RepoPermission.builder()
                    .username(holder)
                    .provider("github")
                    .value("/acme/widgets")
                    .matchType(MatchType.LITERAL)
                    .build());
        }
        grants.save(RepoPermission.builder()
                .username("alice")
                .provider("gitlab")
                .value("/acme/widgets")
                .matchType(MatchType.LITERAL)
                .build());

        var page = controller.grants(null, null, null, null, "github", null, null, 2, 1);

        assertEquals("3", page.getHeaders().getFirst(AuditController.TOTAL_COUNT_HEADER));
        assertEquals(
                List.of("bob", "carol"),
                page.getBody().stream().map(GrantSnapshot.Holding::holder).toList());
        assertBadRequest(() -> controller.grants(null, null, null, null, null, "acme", null, 50, 0));
        assertBadRequest(() -> controller.grants(null, null, "SIDEWAYS", null, null, null, null, 50, 0));
        assertBadRequest(() -> controller.grants(null, null, null, null, null, null, null, 0, 0));
    }

    @Test
    void groupMembers_listOnePageOfTheSnapshot_filteredByMember() {
        PermissionGroup ops = PermissionGroup.builder().name("ops").build();
        PermissionGroup devs = PermissionGroup.builder().name("devs").build();
        groups.saveGroup(ops);
        groups.saveGroup(devs);
        groups.addMember(ops.getId(), "bob");
        groups.addMember(devs.getId(), "bob");
        groups.addMember(devs.getId(), "alice");

        var page = controller.groupMembers(null, "bob", 50, 0);

        assertEquals("2", page.getHeaders().getFirst(AuditController.TOTAL_COUNT_HEADER));
        assertEquals(
                List.of("devs", "ops"),
                page.getBody().stream().map(GrantSnapshot.Membership::group).toList());
    }

    @Test
    void export_refusesABadRequestBeforeWritingAnything() throws Exception {
        var exchange = Exchange.of(Map.of());

        assertBadRequest(() -> {
            try {
                controller.exportRoleChanges(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        true,
                        null,
                        "id,nope",
                        exchange.request(),
                        exchange.response());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        assertBadRequest(() -> {
            try {
                controller.exportEvidence("pushes,nonsense", null, null, null, exchange.request(), exchange.response());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        assertEquals(0, exchange.body().size());
        verify(exchange.response(), never()).getOutputStream();
    }

    @Test
    void exportEvidence_writesEachRequestedTypeInOneZip_inTheTimeWindow() throws Exception {
        grant("before", T0.minusSeconds(60), "/acme/a", MatchType.LITERAL);
        grant("inside", T0.plusSeconds(1), "/acme/b", MatchType.LITERAL);
        roleChanges.record(RoleChangeEvent.builder()
                .occurredAt(T0.plusSeconds(2))
                .origin(ChangeOrigin.IDP)
                .actor("ldap")
                .username("testuser")
                .roles(List.of("USER"))
                .build());
        var exchange = Exchange.of(Map.of("types", "permission-changes,role-changes", "format", "json"));

        controller.exportEvidence(
                "role-changes,permission-changes",
                T0.toString(),
                null,
                "json",
                exchange.request(),
                exchange.response());

        ExportZip zip = ExportZip.read(exchange.body().toByteArray());
        assertEquals(
                List.of("permission-changes/part-00001.jsonl", "role-changes/part-00001.jsonl", "manifest.json"),
                List.copyOf(zip.entries().keySet()),
                "types are written in the fixed record-type order, whatever order they were asked for in");
        assertTrue(zip.entries().get("permission-changes/part-00001.jsonl").contains("\"inside\""));
        assertFalse(zip.entries().get("permission-changes/part-00001.jsonl").contains("\"before\""));
        assertEquals(2, zip.manifest().get("types").size());
    }

    @Test
    void exportEvidence_writesEachTypesChosenColumns_andEveryFieldOfTheRest() throws Exception {
        grant("inside", T0, "/acme/b", MatchType.LITERAL);
        roleChanges.record(RoleChangeEvent.builder()
                .occurredAt(T0)
                .origin(ChangeOrigin.IDP)
                .actor("ldap")
                .username("testuser")
                .roles(List.of("USER"))
                .build());
        var exchange = Exchange.of(
                Map.of("types", "permission-changes,role-changes", "role-changes.columns", "username,roles,origin"));

        controller.exportEvidence(
                "permission-changes,role-changes", null, null, null, exchange.request(), exchange.response());

        ExportZip zip = ExportZip.read(exchange.body().toByteArray());
        assertEquals(
                "username,roles,origin\r\ntestuser,\"[\"\"USER\"\"]\",IDP\r\n",
                zip.entries().get("role-changes/part-00001.csv"));
        assertTrue(zip.entries()
                .get("permission-changes/part-00001.csv")
                .startsWith(String.join(",", ExportFields.PERMISSION_CHANGES) + "\r\n"));
        assertNull(
                zip.manifest().get("filters").get("role-changes.columns"),
                "a type's columns shape the file and are not a filter");
    }

    @Test
    void exportEvidence_refusesColumnsForAnUnknownType_orForATypeNotBeingExported() throws Exception {
        for (Map<String, String> params : List.of(
                Map.of("types", "role-changes", "nonsense.columns", "id"),
                Map.of("types", "role-changes", "pushes.columns", "id"),
                Map.of("types", "role-changes", "role-changes.columns", "id,nope"))) {
            var exchange = Exchange.of(params);
            assertBadRequest(() -> {
                try {
                    controller.exportEvidence(
                            "role-changes", null, null, null, exchange.request(), exchange.response());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            verify(exchange.response(), never()).getOutputStream();
        }
    }

    @Test
    void recordTypes_listEveryTypeInExportOrder_withItsColumns() {
        var types = controller.listRecordTypes();

        assertEquals(
                AuditController.RECORD_TYPES,
                types.stream().map(AuditController.RecordType::name).toList());
        assertEquals(
                ExportFields.ROLE_CHANGES,
                types.stream()
                        .filter(t -> t.name().equals("role-changes"))
                        .findFirst()
                        .orElseThrow()
                        .columns());
    }

    @Test
    void roleChanges_filterBySubjectAndActor() {
        roleChanges.record(RoleChangeEvent.builder()
                .occurredAt(T0)
                .origin(ChangeOrigin.IDP)
                .actor("ldap")
                .username("testuser")
                .previousRoles(List.of("USER"))
                .roles(List.of("USER", "ADMIN"))
                .build());
        roleChanges.record(RoleChangeEvent.builder()
                .occurredAt(T0.plusSeconds(1))
                .origin(ChangeOrigin.API)
                .actor("api-key")
                .username("erin")
                .roles(List.of("USER"))
                .build());

        var bySubject =
                controller.roleChanges(null, "testuser", null, null, null, null, null, null, null, null, 50, 0, true);
        assertEquals(
                List.of("testuser"),
                bySubject.getBody().stream().map(RoleChangeEvent::username).toList());
        var byActor = controller.roleChanges(null, null, "ldap", null, null, null, null, null, null, null, 50, 0, true);
        assertEquals(1, byActor.getBody().size());
        var byOrigin = controller.roleChanges("api", null, null, null, null, null, null, null, null, null, 50, 0, true);
        assertEquals(
                List.of("erin"),
                byOrigin.getBody().stream().map(RoleChangeEvent::username).toList());
    }
}
