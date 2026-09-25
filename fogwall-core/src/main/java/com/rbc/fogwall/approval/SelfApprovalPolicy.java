package com.rbc.fogwall.approval;

import com.rbc.fogwall.db.model.Attestation;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.user.ReadOnlyUserStore;
import com.rbc.fogwall.user.UserEntry;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Decides whether an approved push may be forwarded when the approver is also the pusher. Self-certification takes two
 * grants: the {@code SELF_CERTIFY} role on the pusher's user record, and a {@code SELF_CERTIFY} repository permission
 * covering the pushed repository. Both proxy transports consult this before forwarding an approved push. An approval by
 * anyone other than the pusher is outside its scope.
 *
 * <p>Roles are read from the user store as granted. The dashboard persists an IdP user's roles on login, so a role here
 * reflects that user's last login rather than the IdP's current state. A pusher with no user record fails the check.
 *
 * <p>The dashboard's approve endpoint applies the same two grants before it records a self-approval, reading the role
 * from the reviewer's session authorities, which are derived from the same stored roles. This policy is the check that
 * runs again at forwarding time, whatever path marked the record approved.
 */
@Slf4j
public class SelfApprovalPolicy {

    /** The role, as stored on {@link UserEntry#getRoles()}, that allows a user to approve their own push. */
    public static final String SELF_CERTIFY_ROLE = "SELF_CERTIFY";

    /** The outcome of {@link #evaluate}. Only {@link #isHonored() honored} verdicts may be forwarded. */
    @Getter
    @RequiredArgsConstructor
    public enum Verdict {
        /** No reviewer attestation, no resolved pusher, or an approver other than the pusher. */
        NOT_SELF_APPROVAL(true, null),
        /** The pusher approved their own push and holds both grants. */
        ENTITLED(true, null),
        MISSING_ROLE(false, "Self-approved push rejected: SELF_CERTIFY role not granted"),
        MISSING_PERMISSION(false, "Self-approved push rejected: no SELF_CERTIFY permission for this repository"),
        /** A self-approval whose grants cannot be looked up: no user record, or no provider or repository URL. */
        UNVERIFIABLE(false, "Self-approved push rejected: self-certify entitlement cannot be verified");

        private final boolean honored;

        /** Why the approval was refused, suitable for the client and the push record; {@code null} when honored. */
        private final String reason;
    }

    private final RepoPermissionService repoPermissionService;
    private final ReadOnlyUserStore userStore;

    public SelfApprovalPolicy(RepoPermissionService repoPermissionService, ReadOnlyUserStore userStore) {
        this.repoPermissionService = Objects.requireNonNull(
                repoPermissionService,
                "repoPermissionService is required: without it no self-approval could be verified");
        this.userStore = Objects.requireNonNull(
                userStore, "userStore is required: without it the SELF_CERTIFY role could not be verified");
    }

    /** Evaluates an approved push record. Records that are not self-approvals are always honored. */
    public Verdict evaluate(PushRecord record) {
        Attestation att = record.getAttestation();
        // An auto-approved push has no attestation: there is no human approver to police.
        if (att == null) return Verdict.NOT_SELF_APPROVAL;
        String pusher = record.getResolvedUser();
        String approver = att.getReviewerUsername();
        if (pusher == null || approver == null || !pusher.equals(approver)) return Verdict.NOT_SELF_APPROVAL;

        Verdict verdict = evaluateGrants(pusher, record.getProvider(), record.getUrl());
        if (!verdict.isHonored()) {
            log.warn(
                    "Self-approval of push {} refused: pusher={} provider={} path={} verdict={}",
                    record.getId(),
                    pusher,
                    record.getProvider(),
                    record.getUrl(),
                    verdict);
        }
        return verdict;
    }

    private Verdict evaluateGrants(String pusher, String provider, String path) {
        if (provider == null || path == null) return Verdict.UNVERIFIABLE;
        Optional<UserEntry> user = userStore.findByUsername(pusher);
        if (user.isEmpty()) return Verdict.UNVERIFIABLE;
        if (!user.get().getRoles().contains(SELF_CERTIFY_ROLE)) return Verdict.MISSING_ROLE;
        if (!repoPermissionService.isBypassReviewAllowed(pusher, provider, path)) return Verdict.MISSING_PERMISSION;
        return Verdict.ENTITLED;
    }
}
