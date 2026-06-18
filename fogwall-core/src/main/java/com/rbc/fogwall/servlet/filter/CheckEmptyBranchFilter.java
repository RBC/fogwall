package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.git.GitClientUtils.AnsiColor.*;
import static com.rbc.fogwall.git.GitClientUtils.SymbolCodes.*;
import static com.rbc.fogwall.git.GitClientUtils.sym;
import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;

import com.rbc.fogwall.git.GitClientUtils;
import com.rbc.fogwall.git.GitRequestDetails;
import com.rbc.fogwall.git.HttpOperation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Filter that rejects pushes where no commits could be found in the pushed range. Two cases are distinguished:
 *
 * <ul>
 *   <li><b>Empty branch</b> - a new branch is being created but its tip is already reachable from an existing ref; no
 *       new commits were introduced.
 *   <li><b>Commit data not found</b> - a non-new-branch push produced no commit data; indicates a proxy or repository
 *       state problem.
 * </ul>
 *
 * <p>This filter short-circuits immediately via {@link #rejectAndSendError} without recording to
 * {@link ValidationSummaryFilter}.
 *
 * <p>Runs at order 210, at the start of the content validation range (200-399).
 */
@Slf4j
public class CheckEmptyBranchFilter extends AbstractFogwallFilter {

    private static final int ORDER = 210;

    public CheckEmptyBranchFilter() {
        super(ORDER, Set.of(HttpOperation.PUSH));
    }

    @Override
    public String getStepName() {
        return "checkEmptyBranch";
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

        var commits = requestDetails.getPushedCommits();
        if (commits != null && !commits.isEmpty()) {
            return;
        }

        String commitFrom = requestDetails.getCommitFrom();
        boolean isNewBranch = commitFrom == null || commitFrom.matches("^0+$");

        String title;
        String message;
        if (isNewBranch) {
            title = sym(NO_ENTRY) + "  Push Blocked - Empty Branch";
            message = "Please make a commit before pushing a new branch.";
        } else {
            title = sym(NO_ENTRY) + "  Push Blocked - Commit Data Not Found";
            message = "Commit data not found. Please contact an administrator for support.";
        }

        log.warn("checkEmptyBranch: rejecting push - {}", message);
        rejectAndSendError(request, response, title, GitClientUtils.format(title, message, RED, null));
    }
}
