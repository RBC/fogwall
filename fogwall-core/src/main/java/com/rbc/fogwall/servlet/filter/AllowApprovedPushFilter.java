package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.FogwallServlet.APPROVED_PUSH_ID_ATTR;
import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;
import static com.rbc.fogwall.servlet.FogwallServlet.PRE_APPROVED_ATTR;
import static com.rbc.fogwall.servlet.FogwallServlet.SERVICE_URL_ATTR;

import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.model.PushQuery;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStatus;
import com.rbc.fogwall.git.GitRequestDetails;
import com.rbc.fogwall.git.HttpOperation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
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

    public AllowApprovedPushFilter(PushStore pushStore, String serviceUrl) {
        super(ORDER);
        this.pushStore = pushStore;
        this.serviceUrl = serviceUrl;
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

        if (commitTo == null || commitTo.isBlank()) {
            return;
        }

        // Look up whether this exact commit was already approved
        List<PushRecord> approved = pushStore.find(PushQuery.builder()
                .commitTo(commitTo)
                .branch(branch)
                .repoName(repoName)
                .status(PushStatus.APPROVED)
                .limit(1)
                .build());

        if (!approved.isEmpty()) {
            String approvedId = approved.get(0).getId();
            log.info("Push {} (commitTo={}) was previously approved - allowing re-push through", approvedId, commitTo);
            request.setAttribute(PRE_APPROVED_ATTR, Boolean.TRUE);
            // Store the original push ID so fogwallServlet can update its status to FORWARDED/ERROR
            // via the async response callbacks after the upstream responds.
            request.setAttribute(APPROVED_PUSH_ID_ATTR, approvedId);
        }
    }
}
