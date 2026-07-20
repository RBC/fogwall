package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;
import static com.rbc.fogwall.servlet.FogwallServlet.PRE_APPROVED_ATTR;

import com.rbc.fogwall.db.PushRecordMapper;
import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.git.GitRequestDetails;
import com.rbc.fogwall.git.HttpOperation;
import com.rbc.fogwall.git.SecretRedactor;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A plain servlet {@link Filter} that persists push records to the {@link PushStore}. Unlike other fogwallFilters, this
 * wraps the entire filter chain using try-finally so it always runs - even when a validation filter commits the
 * response early (e.g., via {@code sendGitError}).
 *
 * <p>This filter should be registered BEFORE all other filters so its {@code finally} block executes after them.
 */
@Slf4j
@RequiredArgsConstructor
public class PushStoreAuditFilter implements Filter {

    private final PushStore pushStore;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        } finally {
            persistIfPush(request, response);
        }
    }

    private void persistIfPush(ServletRequest request, ServletResponse response) {
        if (!(request instanceof HttpServletRequest httpRequest)) return;

        var requestDetails = (GitRequestDetails) httpRequest.getAttribute(GIT_REQUEST_ATTR);

        // Only persist push operations
        if (requestDetails == null || requestDetails.getOperation() != HttpOperation.PUSH) return;

        // For transparent-proxy re-pushes (PRE_APPROVED_ATTR is set), the upstream response is handled
        // asynchronously by fogwallServlet.onProxyResponseSuccess/Failure, which updates the original
        // approved record directly. Skip creating a duplicate record here.
        if (Boolean.TRUE.equals(httpRequest.getAttribute(PRE_APPROVED_ATTR))) return;

        try {
            PushRecord record = PushRecordMapper.fromRequestDetails(requestDetails);
            SecretRedactor.redact(record, requestDetails.getSecretsToRedact());
            pushStore.save(record);

            log.info(
                    "Persisted push record: id={}, repo={}, status={}",
                    record.getId(),
                    record.getUrl(),
                    record.getStatus());
        } catch (Exception e) {
            log.error("Failed to persist push record for request {}", requestDetails.getId(), e);
        }
    }
}
