package com.rbc.fogwall.permission;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.model.MatchType;
import com.rbc.fogwall.user.StaticUserStore;
import com.rbc.fogwall.user.UserEntry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** A repository grant counts only for a user whose roles permit acting, however the grant reaches them. */
class RoleGrantTest {

    private static final String PROVIDER = "github";
    private static final String REPO = "/acme/repo";

    private InMemoryGroupPermissionStore groupStore;
    private RepoPermissionService svc;

    @BeforeEach
    void setUp() {
        groupStore = new InMemoryGroupPermissionStore();
        var users = new StaticUserStore(List.of(
                user("auditor", "USER", "AUDITOR"),
                user("admin-auditor", "USER", "ADMIN", "AUDITOR"),
                user("dev", "USER"),
                user("admin", "ADMIN"),
                user("reader", "READER"),
                user("self-certifier", "SELF_CERTIFY")));
        svc = new RepoPermissionService(new InMemoryRepoPermissionStore(), groupStore, users);
    }

    @ParameterizedTest
    @EnumSource(RepoPermission.Grant.class)
    void directGrant_ignoredForAuditor(RepoPermission.Grant grant) {
        grantDirect("auditor", grant);
        grantDirect("dev", grant);

        assertInstanceOf(
                RepoPermissionService.GrantResult.NotGranted.class,
                svc.evaluateGrant("auditor", PROVIDER, REPO, grant));
        assertFalse(svc.hasAnyGrant("auditor", PROVIDER, REPO));
        assertFalse(
                svc.evaluateGrant("dev", PROVIDER, REPO, grant) instanceof RepoPermissionService.GrantResult.NotGranted,
                "The same grant still counts for a user who is not an auditor");
    }

    @Test
    void writeChecks_allRefuseAuditor() {
        grantDirect("auditor", RepoPermission.Grant.MAINTAIN);
        grantDirect("auditor", RepoPermission.Grant.PUSH_AND_REVIEW);
        grantDirect("auditor", RepoPermission.Grant.SELF_CERTIFY);

        assertFalse(svc.isAllowedToPush("auditor", PROVIDER, REPO));
        assertFalse(svc.isAllowedToReview("auditor", PROVIDER, REPO));
        assertFalse(svc.isAllowedToPropose("auditor", PROVIDER, REPO));
        assertFalse(svc.isAllowedToFileIssue("auditor", PROVIDER, REPO));
        assertFalse(svc.isAllowedToMerge("auditor", PROVIDER, REPO));
        assertFalse(svc.isBypassReviewAllowed("auditor", PROVIDER, REPO));
    }

    @Test
    void groupGrant_ignoredForAuditor() {
        PermissionGroup g = PermissionGroup.builder()
                .name("devs")
                .source(PermissionGroup.Source.DB)
                .build();
        groupStore.saveGroup(g);
        groupStore.saveRule(GroupPermissionRule.builder()
                .groupId(g.getId())
                .provider(PROVIDER)
                .value(REPO)
                .matchType(MatchType.LITERAL)
                .grant(RepoPermission.Grant.PUSH)
                .build());
        groupStore.addMember(g.getId(), "auditor");
        groupStore.addMember(g.getId(), "dev");

        assertFalse(svc.isAllowedToPush("auditor", PROVIDER, REPO));
        assertFalse(svc.hasAnyGrant("auditor", PROVIDER, REPO));
        assertTrue(svc.isAllowedToPush("dev", PROVIDER, REPO));
    }

    @Test
    void auditorRole_winsOverAdmin() {
        grantDirect("admin-auditor", RepoPermission.Grant.PUSH);
        assertFalse(svc.isAllowedToPush("admin-auditor", PROVIDER, REPO));
    }

    @ParameterizedTest
    @EnumSource(RepoPermission.Grant.class)
    void directGrant_ignoredForReader(RepoPermission.Grant grant) {
        grantDirect("reader", grant);
        assertEquals(
                new RepoPermissionService.GrantResult.NotGranted(
                        RepoPermissionService.GrantResult.Reason.ROLE_CANNOT_ACT),
                svc.evaluateGrant("reader", PROVIDER, REPO, grant));
        assertEquals(
                new RepoPermissionService.GrantResult.NotGranted(
                        RepoPermissionService.GrantResult.Reason.NO_MATCHING_GRANT),
                svc.evaluateGrant("reader", PROVIDER, "/acme/other", grant),
                "With nothing matching, the reason is the missing grant, not the role");
        assertFalse(svc.hasAnyGrant("reader", PROVIDER, REPO));
    }

    @Test
    void readerWithUser_actsAsUser() {
        svc = new RepoPermissionService(
                new InMemoryRepoPermissionStore(),
                groupStore,
                new StaticUserStore(List.of(user("reader-user", "READER", "USER"))));
        grantDirect("reader-user", RepoPermission.Grant.PUSH);
        assertTrue(svc.isAllowedToPush("reader-user", PROVIDER, REPO));
    }

    @Test
    void groupGrant_ignoredForReader() {
        PermissionGroup g = PermissionGroup.builder()
                .name("readers")
                .source(PermissionGroup.Source.DB)
                .build();
        groupStore.saveGroup(g);
        groupStore.saveRule(GroupPermissionRule.builder()
                .groupId(g.getId())
                .provider(PROVIDER)
                .value(REPO)
                .matchType(MatchType.LITERAL)
                .grant(RepoPermission.Grant.PUSH)
                .build());
        groupStore.addMember(g.getId(), "reader");
        assertFalse(svc.isAllowedToPush("reader", PROVIDER, REPO));
    }

    @Test
    void adminAlone_actsAsUser() {
        grantDirect("admin", RepoPermission.Grant.PUSH_AND_REVIEW);
        assertTrue(svc.isAllowedToPush("admin", PROVIDER, REPO));
        assertTrue(svc.isAllowedToReview("admin", PROVIDER, REPO));
    }

    @Test
    void selfCertifyAlone_holdsNoGrant() {
        grantDirect("self-certifier", RepoPermission.Grant.SELF_CERTIFY);
        grantDirect("self-certifier", RepoPermission.Grant.PUSH);
        assertFalse(svc.isAllowedToPush("self-certifier", PROVIDER, REPO));
        assertFalse(svc.isBypassReviewAllowed("self-certifier", PROVIDER, REPO));
    }

    @Test
    void userWithNoRecord_holdsNoGrant() {
        grantDirect("ghost", RepoPermission.Grant.PUSH);
        assertFalse(svc.isAllowedToPush("ghost", PROVIDER, REPO));
        assertFalse(svc.hasAnyGrant("ghost", PROVIDER, REPO));
    }

    @Test
    void isAuditor_readsStoredRoles() {
        assertTrue(svc.isAuditor("auditor"));
        assertTrue(svc.isAuditor("admin-auditor"));
        assertFalse(svc.isAuditor("dev"));
        assertFalse(svc.isAuditor("nobody"), "A user with no record is not an auditor");
        assertFalse(svc.isAuditor(null));
    }

    private void grantDirect(String username, RepoPermission.Grant grant) {
        svc.save(RepoPermission.builder()
                .username(username)
                .provider(PROVIDER)
                .value(REPO)
                .matchType(MatchType.LITERAL)
                .grant(grant)
                .source(RepoPermission.Source.DB)
                .build());
    }

    private static UserEntry user(String username, String... roles) {
        return UserEntry.builder().username(username).roles(List.of(roles)).build();
    }
}
