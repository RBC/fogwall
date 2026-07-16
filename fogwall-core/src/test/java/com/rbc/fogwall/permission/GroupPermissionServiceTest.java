package com.rbc.fogwall.permission;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.model.MatchType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link RepoPermissionService} group-union semantics using in-memory stores. */
class GroupPermissionServiceTest {

    RepoPermissionService svc;
    InMemoryGroupPermissionStore groupStore;

    @BeforeEach
    void setUp() {
        groupStore = new InMemoryGroupPermissionStore();
        svc = new RepoPermissionService(new InMemoryRepoPermissionStore(), groupStore);
    }

    private PermissionGroup group(String name) {
        PermissionGroup g = PermissionGroup.builder()
                .name(name)
                .source(PermissionGroup.Source.DB)
                .build();
        groupStore.saveGroup(g);
        return g;
    }

    private GroupPermissionRule rule(String groupId, String provider, String value, RepoPermission.Grant grant) {
        GroupPermissionRule r = GroupPermissionRule.builder()
                .groupId(groupId)
                .provider(provider)
                .value(value)
                .matchType(MatchType.LITERAL)
                .grant(grant)
                .build();
        groupStore.saveRule(r);
        return r;
    }

    private GroupPermissionRule globRule(String groupId, String provider, String value, RepoPermission.Grant grant) {
        GroupPermissionRule r = GroupPermissionRule.builder()
                .groupId(groupId)
                .provider(provider)
                .value(value)
                .matchType(MatchType.GLOB)
                .grant(grant)
                .build();
        groupStore.saveRule(r);
        return r;
    }

    // ---- group member can push via group rule ----

    @Test
    void groupMember_allowedViaPushRule() {
        PermissionGroup g = group("devs");
        rule(g.getId(), "github", "/acme/repo", RepoPermission.Grant.PUSH);
        groupStore.addMember(g.getId(), "alice");

        assertTrue(svc.isAllowedToPush("alice", "github", "/acme/repo"));
    }

    @Test
    void groupMember_allowedViaReviewRule() {
        PermissionGroup g = group("reviewers");
        rule(g.getId(), "github", "/acme/repo", RepoPermission.Grant.REVIEW);
        groupStore.addMember(g.getId(), "alice");

        assertTrue(svc.isAllowedToReview("alice", "github", "/acme/repo"));
    }

    @Test
    void groupMember_pushAndReview_allowsBoth() {
        PermissionGroup g = group("devs");
        rule(g.getId(), "github", "/acme/repo", RepoPermission.Grant.PUSH_AND_REVIEW);
        groupStore.addMember(g.getId(), "alice");

        assertTrue(svc.isAllowedToPush("alice", "github", "/acme/repo"));
        assertTrue(svc.isAllowedToReview("alice", "github", "/acme/repo"));
    }

    // ---- non-member cannot use group rules ----

    @Test
    void nonMember_deniedByGroupRule() {
        PermissionGroup g = group("devs");
        rule(g.getId(), "github", "/acme/repo", RepoPermission.Grant.PUSH);
        groupStore.addMember(g.getId(), "alice");

        assertFalse(svc.isAllowedToPush("bob", "github", "/acme/repo"));
    }

    // ---- fail-closed: path has group rules but user is not a member ----

    @Test
    void pathCoveredByGroupRule_nonMember_denied() {
        PermissionGroup g = group("devs");
        rule(g.getId(), "github", "/acme/repo", RepoPermission.Grant.PUSH);
        // no members added

        assertFalse(svc.isAllowedToPush("alice", "github", "/acme/repo"));
    }

    // ---- union: direct OR group grants access ----

    @Test
    void directGrant_noGroupMembership_stillAllowed() {
        PermissionGroup g = group("devs");
        rule(g.getId(), "github", "/acme/repo", RepoPermission.Grant.PUSH);
        groupStore.addMember(g.getId(), "alice");

        RepoPermission direct = RepoPermission.builder()
                .username("bob")
                .provider("github")
                .value("/acme/repo")
                .matchType(MatchType.LITERAL)
                .grant(RepoPermission.Grant.PUSH)
                .source(RepoPermission.Source.DB)
                .build();
        svc.save(direct);

        assertTrue(svc.isAllowedToPush("bob", "github", "/acme/repo"));
        assertTrue(svc.isAllowedToPush("alice", "github", "/acme/repo"));
    }

    // ---- glob rules in groups ----

    @Test
    void groupGlobRule_matchesAllReposUnderOwner() {
        PermissionGroup g = group("devs");
        globRule(g.getId(), "github", "/acme/*", RepoPermission.Grant.PUSH);
        groupStore.addMember(g.getId(), "alice");

        assertTrue(svc.isAllowedToPush("alice", "github", "/acme/repo-a"));
        assertTrue(svc.isAllowedToPush("alice", "github", "/acme/repo-b"));
        assertFalse(svc.isAllowedToPush("alice", "github", "/other/repo"));
    }

    // ---- provider isolation ----

    @Test
    void groupRule_doesNotApplyToOtherProvider() {
        PermissionGroup g = group("devs");
        rule(g.getId(), "github", "/acme/repo", RepoPermission.Grant.PUSH);
        groupStore.addMember(g.getId(), "alice");

        assertFalse(svc.isAllowedToPush("alice", "gitlab", "/acme/repo"));
    }

    // ---- push-only rule does not grant review ----

    @Test
    void pushOnlyGroupRule_deniesReview() {
        PermissionGroup g = group("devs");
        rule(g.getId(), "github", "/acme/repo", RepoPermission.Grant.PUSH);
        groupStore.addMember(g.getId(), "alice");

        assertTrue(svc.isAllowedToPush("alice", "github", "/acme/repo"));
        assertFalse(svc.isAllowedToReview("alice", "github", "/acme/repo"));
    }

    // ---- multiple groups ----

    @Test
    void userInMultipleGroups_unionOfAllRules() {
        PermissionGroup pushers = group("pushers");
        rule(pushers.getId(), "github", "/acme/repo", RepoPermission.Grant.PUSH);
        groupStore.addMember(pushers.getId(), "alice");

        PermissionGroup reviewers = group("reviewers");
        rule(reviewers.getId(), "github", "/acme/repo", RepoPermission.Grant.REVIEW);
        groupStore.addMember(reviewers.getId(), "alice");

        assertTrue(svc.isAllowedToPush("alice", "github", "/acme/repo"));
        assertTrue(svc.isAllowedToReview("alice", "github", "/acme/repo"));
    }

    // ---- no group store configured ----

    @Test
    void noGroupStore_noDirectPermissions_denied() {
        RepoPermissionService noGroupSvc = new RepoPermissionService(new InMemoryRepoPermissionStore());
        assertFalse(noGroupSvc.isAllowedToPush("alice", "github", "/acme/repo"));
    }

    // ---- evaluateGrant: attributes which entry granted access ----

    @Test
    void evaluateGrant_directPermission_returnsGrantedDirect() {
        RepoPermission direct = RepoPermission.builder()
                .username("alice")
                .provider("github")
                .value("/acme/repo")
                .matchType(MatchType.LITERAL)
                .grant(RepoPermission.Grant.PUSH)
                .source(RepoPermission.Source.DB)
                .build();
        svc.save(direct);

        var result = svc.evaluateGrant("alice", "github", "/acme/repo", RepoPermission.Grant.PUSH);

        assertInstanceOf(RepoPermissionService.GrantResult.GrantedDirect.class, result);
        assertEquals(
                direct.getId(),
                ((RepoPermissionService.GrantResult.GrantedDirect) result)
                        .permission()
                        .getId());
    }

    @Test
    void evaluateGrant_groupRule_returnsGrantedByGroup() {
        PermissionGroup g = group("devs");
        GroupPermissionRule r = rule(g.getId(), "github", "/acme/repo", RepoPermission.Grant.PUSH);
        groupStore.addMember(g.getId(), "alice");

        var result = svc.evaluateGrant("alice", "github", "/acme/repo", RepoPermission.Grant.PUSH);

        assertInstanceOf(RepoPermissionService.GrantResult.GrantedByGroup.class, result);
        assertEquals(
                r.getId(),
                ((RepoPermissionService.GrantResult.GrantedByGroup) result)
                        .rule()
                        .getId());
    }

    @Test
    void evaluateGrant_directPreferredOverGroup() {
        PermissionGroup g = group("devs");
        rule(g.getId(), "github", "/acme/repo", RepoPermission.Grant.PUSH);
        groupStore.addMember(g.getId(), "alice");

        RepoPermission direct = RepoPermission.builder()
                .username("alice")
                .provider("github")
                .value("/acme/repo")
                .matchType(MatchType.LITERAL)
                .grant(RepoPermission.Grant.PUSH)
                .source(RepoPermission.Source.DB)
                .build();
        svc.save(direct);

        var result = svc.evaluateGrant("alice", "github", "/acme/repo", RepoPermission.Grant.PUSH);

        assertInstanceOf(RepoPermissionService.GrantResult.GrantedDirect.class, result);
    }

    @Test
    void evaluateGrant_noMatch_returnsNotGranted() {
        var result = svc.evaluateGrant("alice", "github", "/acme/repo", RepoPermission.Grant.PUSH);
        assertInstanceOf(RepoPermissionService.GrantResult.NotGranted.class, result);
    }

    @Test
    void evaluateGrant_nonMember_returnsNotGranted() {
        PermissionGroup g = group("devs");
        rule(g.getId(), "github", "/acme/repo", RepoPermission.Grant.PUSH);
        groupStore.addMember(g.getId(), "alice");

        var result = svc.evaluateGrant("bob", "github", "/acme/repo", RepoPermission.Grant.PUSH);

        assertInstanceOf(RepoPermissionService.GrantResult.NotGranted.class, result);
    }
}
