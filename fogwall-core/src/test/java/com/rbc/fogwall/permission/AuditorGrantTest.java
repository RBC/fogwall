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

/** An auditor holds no repository grant, however the grant reaches them. */
class AuditorGrantTest {

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
                user("dev", "USER")));
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
