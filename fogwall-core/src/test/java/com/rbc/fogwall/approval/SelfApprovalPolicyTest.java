package com.rbc.fogwall.approval;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.approval.SelfApprovalPolicy.Verdict;
import com.rbc.fogwall.db.model.Attestation;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStatus;
import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.user.StaticUserStore;
import com.rbc.fogwall.user.UserEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

class SelfApprovalPolicyTest {

    private static final String PROVIDER = "github";
    private static final String URL = "github.com/owner/repo.git";

    private final RepoPermissionService perms = mock(RepoPermissionService.class);

    private SelfApprovalPolicy policyWith(UserEntry... users) {
        return new SelfApprovalPolicy(perms, new StaticUserStore(List.of(users)));
    }

    private static UserEntry user(String username, String... roles) {
        return UserEntry.builder().username(username).roles(List.of(roles)).build();
    }

    private static PushRecord approved(String pusher, String approver) {
        return PushRecord.builder()
                .id("push-1")
                .status(PushStatus.APPROVED)
                .resolvedUser(pusher)
                .provider(PROVIDER)
                .url(URL)
                .attestation(
                        approver == null
                                ? null
                                : Attestation.builder()
                                        .pushId("push-1")
                                        .type(Attestation.Type.APPROVAL)
                                        .reviewerUsername(approver)
                                        .build())
                .build();
    }

    @Test
    void selfApproval_withRoleAndPermission_entitled() {
        when(perms.isBypassReviewAllowed("alice", PROVIDER, URL)).thenReturn(true);

        Verdict verdict = policyWith(user("alice", "USER", "SELF_CERTIFY")).evaluate(approved("alice", "alice"));

        assertEquals(Verdict.ENTITLED, verdict);
        assertTrue(verdict.isHonored());
    }

    @Test
    void selfApproval_permissionWithoutRole_refused() {
        when(perms.isBypassReviewAllowed("alice", PROVIDER, URL)).thenReturn(true);

        Verdict verdict = policyWith(user("alice", "USER")).evaluate(approved("alice", "alice"));

        assertEquals(Verdict.MISSING_ROLE, verdict);
        assertFalse(verdict.isHonored());
    }

    @Test
    void selfApproval_roleWithoutPermission_refused() {
        when(perms.isBypassReviewAllowed("alice", PROVIDER, URL)).thenReturn(false);

        Verdict verdict = policyWith(user("alice", "SELF_CERTIFY")).evaluate(approved("alice", "alice"));

        assertEquals(Verdict.MISSING_PERMISSION, verdict);
        assertFalse(verdict.isHonored());
    }

    @Test
    void selfApproval_neitherRoleNorPermission_refused() {
        when(perms.isBypassReviewAllowed("alice", PROVIDER, URL)).thenReturn(false);

        Verdict verdict = policyWith(user("alice", "USER")).evaluate(approved("alice", "alice"));

        assertFalse(verdict.isHonored());
    }

    @Test
    void selfApproval_roleIsNotAuthorityPrefixed() {
        // Roles are stored bare; the ROLE_ prefix belongs to the dashboard's session authorities, not the store.
        when(perms.isBypassReviewAllowed("alice", PROVIDER, URL)).thenReturn(true);

        Verdict verdict = policyWith(user("alice", "ROLE_SELF_CERTIFY")).evaluate(approved("alice", "alice"));

        assertEquals(Verdict.MISSING_ROLE, verdict);
    }

    @Test
    void selfApproval_pusherHasNoUserRecord_refused() {
        // A deprovisioned user has no record to read a role from.
        when(perms.isBypassReviewAllowed("alice", PROVIDER, URL)).thenReturn(true);

        Verdict verdict = policyWith().evaluate(approved("alice", "alice"));

        assertEquals(Verdict.UNVERIFIABLE, verdict);
        assertFalse(verdict.isHonored());
    }

    @Test
    void selfApproval_recordWithoutRepository_refused() {
        PushRecord record = approved("alice", "alice");
        record.setUrl(null);

        Verdict verdict = policyWith(user("alice", "SELF_CERTIFY")).evaluate(record);

        assertEquals(Verdict.UNVERIFIABLE, verdict);
        verifyNoInteractions(perms);
    }

    @Test
    void noAttestation_autoApproved_notInScope() {
        Verdict verdict = policyWith(user("alice", "USER")).evaluate(approved("alice", null));

        assertEquals(Verdict.NOT_SELF_APPROVAL, verdict);
        assertTrue(verdict.isHonored());
        verifyNoInteractions(perms);
    }

    @Test
    void approverIsNotPusher_notInScope() {
        Verdict verdict = policyWith(user("alice", "USER")).evaluate(approved("alice", "bob"));

        assertEquals(Verdict.NOT_SELF_APPROVAL, verdict);
        verifyNoInteractions(perms);
    }

    @Test
    void unresolvedPusher_notInScope() {
        // With no resolved pusher there is no identity to compare the approver against; a reviewer approving an
        // unresolved push is governed by the dashboard's identity gate.
        Verdict verdict = policyWith(user("alice", "SELF_CERTIFY")).evaluate(approved(null, "alice"));

        assertEquals(Verdict.NOT_SELF_APPROVAL, verdict);
        verifyNoInteractions(perms);
    }

    @Test
    void refusedVerdicts_carryAReason() {
        for (Verdict verdict : Verdict.values()) {
            if (verdict.isHonored()) {
                assertNull(verdict.getReason(), verdict.name());
            } else {
                assertNotNull(verdict.getReason(), verdict.name());
            }
        }
    }

    @Test
    void noPermissionService_refusedAtConstruction() {
        assertThrows(NullPointerException.class, () -> new SelfApprovalPolicy(null, new StaticUserStore(List.of())));
    }

    @Test
    void noUserStore_refusedAtConstruction() {
        assertThrows(NullPointerException.class, () -> new SelfApprovalPolicy(perms, null));
    }
}
