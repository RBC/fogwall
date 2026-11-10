package com.rbc.fogwall.dashboard.export;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.model.FetchActivity;
import com.rbc.fogwall.db.model.MatchTarget;
import com.rbc.fogwall.db.model.MatchType;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.ScmApiActionRecord;
import com.rbc.fogwall.permission.ChangeOrigin;
import com.rbc.fogwall.permission.MembershipChangeEvent;
import com.rbc.fogwall.permission.PermissionChangeEvent;
import com.rbc.fogwall.permission.RepoPermission;
import com.rbc.fogwall.permission.RoleChangeEvent;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Each type's export fields against the properties its record actually serialises, so a property added to a record is
 * either exported or deliberately left out here — never silently dropped.
 */
class ExportFieldsTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static void assertFieldsCover(Object sample, List<String> fields, Set<String> notExported) {
        Set<String> properties = new HashSet<>(MAPPER.valueToTree(sample).propertyNames());
        Set<String> expected = new HashSet<>(fields);
        expected.addAll(notExported);
        assertEquals(expected, properties);
        assertEquals(fields.size(), new HashSet<>(fields).size(), "a field is listed twice");
    }

    @Test
    void pushes_exportEverythingButThePerViewerFlagsAndTheDisplayUrls() {
        assertFieldsCover(
                PushRecord.builder().build(),
                ExportFields.PUSHES,
                Set.of(
                        "repoUrl",
                        "commitUrl",
                        "canCurrentUserSelfCertify",
                        "canCurrentUserCancel",
                        "canCurrentUserForward"));
    }

    @Test
    void scmApiActions_and_fetchActivity_exportEveryProperty() {
        assertFieldsCover(ScmApiActionRecord.builder().build(), ExportFields.SCM_API_ACTIONS, Set.of());
        assertFieldsCover(FetchActivity.builder().build(), ExportFields.FETCH_ACTIVITY, Set.of());
    }

    @Test
    void accessChangeEvents_exportEveryProperty() {
        assertFieldsCover(
                PermissionChangeEvent.builder()
                        .action(PermissionChangeEvent.Action.GRANTED)
                        .origin(ChangeOrigin.API)
                        .actor("a")
                        .subjectType(PermissionChangeEvent.SubjectType.USER)
                        .subject("s")
                        .permissionId("p")
                        .provider("github")
                        .target(MatchTarget.SLUG)
                        .value("/a/b")
                        .matchType(MatchType.LITERAL)
                        .grant(RepoPermission.Grant.PUSH)
                        .build(),
                ExportFields.PERMISSION_CHANGES,
                Set.of());
        assertFieldsCover(
                MembershipChangeEvent.builder()
                        .action(MembershipChangeEvent.Action.ADDED)
                        .origin(ChangeOrigin.API)
                        .actor("a")
                        .groupId("g")
                        .groupName("devs")
                        .member("m")
                        .build(),
                ExportFields.MEMBERSHIP_CHANGES,
                Set.of());
        assertFieldsCover(
                RoleChangeEvent.builder()
                        .origin(ChangeOrigin.IDP)
                        .actor("ldap")
                        .username("u")
                        .build(),
                ExportFields.ROLE_CHANGES,
                Set.of());
    }

    @Test
    void snapshots_exportEveryProperty() {
        assertFieldsCover(
                new GrantSnapshot.Holding(
                        "h",
                        GrantSnapshot.Via.DIRECT,
                        null,
                        "github",
                        MatchTarget.SLUG,
                        "/a/b",
                        MatchType.LITERAL,
                        RepoPermission.Grant.PUSH,
                        "DB",
                        "admin",
                        Instant.EPOCH,
                        true,
                        "p"),
                ExportFields.GRANTS,
                Set.of());
        assertFieldsCover(new GrantSnapshot.Membership("g", "devs", "DB", "m"), ExportFields.GROUP_MEMBERS, Set.of());
    }

    @Test
    void columns_blankMeansEveryField_andAChoiceKeepsTheCallersOrder() {
        assertEquals(ExportFields.ROLE_CHANGES, ExportFields.columns(null, ExportFields.ROLE_CHANGES, "role change"));
        assertEquals(
                List.of("username", "id"),
                ExportFields.columns(" username , id ", ExportFields.ROLE_CHANGES, "role change"));
    }

    @Test
    void columns_unknownOrRepeated_isA400() {
        for (String bad : List.of("id,nope", "id,id", ",")) {
            var ex = assertThrows(
                    ResponseStatusException.class,
                    () -> ExportFields.columns(bad, ExportFields.ROLE_CHANGES, "role change"));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode(), bad);
        }
    }
}
