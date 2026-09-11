package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.FogwallServlet.APPROVED_PUSH_ID_ATTR;
import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;
import static com.rbc.fogwall.servlet.FogwallServlet.PRE_APPROVED_ATTR;
import static com.rbc.fogwall.servlet.FogwallServlet.SERVICE_URL_ATTR;

import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.model.Attestation;
import com.rbc.fogwall.db.model.Attestation.Type;
import com.rbc.fogwall.db.model.PushQuery;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStatus;
import com.rbc.fogwall.git.GitRequestDetails;
import com.rbc.fogwall.git.HttpOperation;
import com.rbc.fogwall.git.PushStepKind;
import com.rbc.fogwall.permission.RepoPermissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Filter that:
 *
 * <ol>
 *   <li>Sets the {@code fogwall.serviceUrl} request attribute so downstream filters can include the dashboard link in
 *       block error messages.
 *   <li>For PUSH operations, checks whether a prior approved record exists for the same {@code commitTo} + branch +
 *       repo (transparent-proxy re-push flow). If found, sets the {@code fogwall.preApproved} attribute to
 *       short-circuit remaining validation filters.
 *   <li>For PUSH operations, cancels any other still-PENDING record for the same branch + repo: the developer has moved
 *       on to a new push, so the earlier one queued for review is moot.
 * </ol>
 *
 * <p>Runs at order 50 (authorization range) - after {@code ParseGitRequestFilter} (which populates
 * {@link GitRequestDetails}) but before URL rule and content validation filters.
 */
@Slf4j
public class AllowApprovedPushFilter extends AbstractFogwallFilter {

    private static final int ORDER = 50;

    private final PushStore pushStore;
    private final String serviceUrl;
    private final RepoPermissionService repoPermissionService;

    public AllowApprovedPushFilter(
            PushStore pushStore, String serviceUrl, RepoPermissionService repoPermissionService) {
        super(ORDER);
        this.pushStore = pushStore;
        this.serviceUrl = serviceUrl;
        this.repoPermissionService = repoPermissionService;
    }

    @Override
    public Optional<PushStepKind> stepKind() {
        return Optional.of(PushStepKind.ALLOW_APPROVED_PUSH);
    }

    @Override
    public void doHttpFilter(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // Always stamp the service URL so block messages can include the link
        request.setAttribute(SERVICE_URL_ATTR, serviceUrl);

        // Only check for prior approval on PUSH operations
        if (determineOperation(request) != HttpOperation.PUSH) {
            return;
        }

        var details = (GitRequestDetails) request.getAttribute(GIT_REQUEST_ATTR);
        if (details == null) {
            return;
        }

        String commitTo = details.getCommitTo();
        String branch = details.getBranch();
        String repoName = details.getRepoRef() != null ? details.getRepoRef().getName() : null;
        String owner = details.getRepoRef() != null ? details.getRepoRef().getOwner() : null;
        String provider = details.getProvider() != null ? details.getProvider().getProviderId() : null;

        if (commitTo == null || commitTo.isBlank()) {
            return;
        }

        // An approval is granted for one repository on one provider, so the lookup must identify the
        // repository the same way. Repo names like "app" and "common" recur across an estate; matching on
        // the bare name would let an approval for acme/app satisfy a push to another org's app.
        if (provider == null || owner == null || repoName == null) {
            log.warn(
                    "Not checking for prior approval: incomplete repository identity (provider={}, owner={}, repo={})",
                    provider,
                    owner,
                    repoName);
            return;
        }

        // Look up whether this exact commit was already approved
        List<PushRecord> approved = pushStore.find(PushQuery.builder()
                .commitTo(commitTo)
                .branch(branch)
                .provider(provider)
                .project(owner)
                .repoName(repoName)
                .status(PushStatus.APPROVED)
                .limit(1)
                .build());

        if (!approved.isEmpty()) {
            PushRecord approvedRecord = approved.get(0);
            if (selfApprovalEntitled(approvedRecord)) {
                String approvedId = approvedRecord.getId();
                log.info(
                        "Push {} (commitTo={}) was previously approved - allowing re-push through",
                        approvedId,
                        commitTo);
                request.setAttribute(PRE_APPROVED_ATTR, Boolean.TRUE);
                // Store the original push ID so fogwallServlet can update its status to FORWARDED/ERROR
                // via the async response callbacks after the upstream responds.
                request.setAttribute(APPROVED_PUSH_ID_ATTR, approvedId);
            } else {
                // The prior approval was a self-approval that no longer (or never did) carry SELF_CERTIFY. Do not honor
                // it: leaving PRE_APPROVED unset sends the push back through the validation chain, where it is blocked
                // and re-queued for a genuine review rather than forwarded on a bypassed self-approval. Demote the
                // stale record to ERROR so it does not linger as an approval that will never forward; best-effort so a
                // cleanup failure never breaks serving the push.
                log.warn(
                        "Prior approval {} was a self-approval without SELF_CERTIFY permission - not honoring; push will be re-validated",
                        approvedRecord.getId());
                try {
                    pushStore.updateForwardStatus(
                            approvedRecord.getId(),
                            PushStatus.ERROR,
                            "Self-approval without SELF_CERTIFY permission - not forwarded");
                } catch (RuntimeException e) {
                    log.warn(
                            "Could not demote unentitled self-approval {} to ERROR: {}",
                            approvedRecord.getId(),
                            e.getMessage());
                }
            }
        }

        supersedeStalePendingPushes(details, branch, provider, owner, repoName);
    }

    /**
     * Defense in depth mirroring {@link com.rbc.fogwall.git.ApprovalPreReceiveHook} in server mode: if a prior approval
     * was the pusher approving their own push, re-verify a {@code SELF_CERTIFY} repo permission still exists before
     * honoring it on the transparent-proxy re-push path. Server mode re-checks this in its pre-receive hook; without
     * the same check here an approved-but-unentitled self-approval would be forwarded on re-push.
     *
     * <p>The early {@code true} returns are the not-a-self-approval cases (no attestation, a different approver, or an
     * incomplete record) — there is nothing to gate. Only the self-approval-that-cannot-be-verified case (no permission
     * service wired) fails closed and returns {@code false}.
     *
     * @return {@code true} if the approval may be honored; {@code false} if it was a self-approval with no
     *     {@code SELF_CERTIFY} permission (or one whose entitlement cannot be verified).
     */
    private boolean selfApprovalEntitled(PushRecord approved) {
        Attestation att = approved.getAttestation();
        // No reviewer attestation means no human approver to police — an auto-approved push maps GitResult.ALLOWED
        // straight to APPROVED with no attestation. Not a self-approval, so nothing for this check to act on.
        if (att == null) return true;
        String pusher = approved.getResolvedUser();
        String approver = att.getReviewerUsername();
        // Only a pusher approving their own push is in scope here; anything else is left to the other controls.
        if (pusher == null || approver == null || !pusher.equals(approver)) return true;
        if (approved.getProvider() == null || approved.getUrl() == null) return true;
        if (repoPermissionService == null) {
            log.error("Self-approval by {} not honored on proxy re-push: no RepoPermissionService wired", pusher);
            return false;
        }
        boolean entitled =
                repoPermissionService.isBypassReviewAllowed(pusher, approved.getProvider(), approved.getUrl());
        if (!entitled) {
            log.warn(
                    "Self-approval rejected at proxy: pusher={} provider={} path={} has no SELF_CERTIFY permission",
                    pusher,
                    approved.getProvider(),
                    approved.getUrl());
        }
        return entitled;
    }

    /**
     * Cancels any other PENDING record for this branch: this push means the developer has moved past whatever earlier
     * commit was queued for review on it. Not scoped to {@code commitTo} — the point is to catch a <em>different</em>,
     * older commit on the same branch, not a re-push of the same one.
     */
    private void supersedeStalePendingPushes(
            GitRequestDetails details, String branch, String provider, String owner, String repoName) {
        List<PushRecord> stalePending = pushStore.find(PushQuery.builder()
                .branch(branch)
                .provider(provider)
                .project(owner)
                .repoName(repoName)
                .status(PushStatus.PENDING)
                .build());

        String newPushId = details.getId().toString();
        for (PushRecord stale : stalePending) {
            log.info("Push {} superseded by new push {} to the same branch", stale.getId(), newPushId);
            pushStore.cancel(
                    stale.getId(),
                    Attestation.builder()
                            .pushId(stale.getId())
                            .type(Type.CANCELLATION)
                            .automated(true)
                            .reason("Superseded by push " + newPushId)
                            .build());
        }
    }
}
