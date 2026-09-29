package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.git.GitClientUtils.AnsiColor.*;
import static com.rbc.fogwall.git.GitClientUtils.SymbolCodes.*;
import static com.rbc.fogwall.git.GitClientUtils.sym;
import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;

import com.rbc.fogwall.git.GitClientUtils;
import com.rbc.fogwall.git.GitRequestDetails;
import com.rbc.fogwall.git.HttpOperation;
import com.rbc.fogwall.git.LifecycleStage;
import com.rbc.fogwall.git.PushStepKind;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Records whether a push introduces new commits, and blocks one whose commits were never inspected.
 *
 * <ul>
 *   <li><b>No new commits</b> - {@code EnrichPushCommitsFilter} walked the range and found nothing new: the pushed tip
 *       is already reachable from an existing ref, as when a branch is created at a commit already upstream. There is
 *       no new content to inspect, so the push continues.
 *   <li><b>Commit data not found</b> - no commits and no completed walk. The content stages would have nothing to
 *       inspect for a reason other than there being nothing new, so the push is blocked.
 * </ul>
 *
 * <p>Terminating: a push whose commits could not be inspected leaves nothing for the content stages, so recording the
 * issue ends the chain.
 */
@Slf4j
public final class CheckEmptyBranchFilter extends AbstractFogwallFilter {

    public CheckEmptyBranchFilter() {
        super(LifecycleStage.MANDATORY_PROCESSING, Set.of(HttpOperation.PUSH));
    }

    @Override
    public Optional<PushStepKind> stepKind() {
        return Optional.of(PushStepKind.EMPTY_BRANCH);
    }

    @Override
    public boolean terminatesChainOnFailure() {
        return true;
    }

    @Override
    public void doHttpFilter(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var requestDetails = (GitRequestDetails) request.getAttribute(GIT_REQUEST_ATTR);
        if (requestDetails == null) {
            log.warn("GitRequestDetails not found in request attributes");
            return;
        }

        // Tags legitimately point to existing commits — pushedCommits will be empty but that is expected.
        if (requestDetails.isTagPush()) {
            log.debug("Tag push detected (ref={}), skipping empty branch check", requestDetails.getBranch());
            return;
        }

        // An earlier filter (e.g. EnrichPushCommitsFilter) already failed and set a specific
        // reason for why pushedCommits is empty — don't clobber it with a generic message here.
        if (requestDetails.getResult() == GitRequestDetails.GitResult.ERROR) {
            log.debug("Push already in ERROR state — skipping empty branch check");
            return;
        }

        var commits = requestDetails.getPushedCommits();
        if (commits != null && !commits.isEmpty()) {
            return;
        }
        if (requestDetails.isCommitRangeInspected()) {
            log.debug("Push to {} introduces no new commits", requestDetails.getBranch());
            return;
        }

        String title = sym(NO_ENTRY) + "  Push Blocked - Commit Data Not Found";
        String message = "Commit data not found. Please contact an administrator for support.";
        log.warn("checkEmptyBranch: rejecting push - {}", message);
        recordIssue(request, title, GitClientUtils.format(title, message, RED, null));
    }
}
