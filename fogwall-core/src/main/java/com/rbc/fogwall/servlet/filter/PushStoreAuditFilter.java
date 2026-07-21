package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;
import static com.rbc.fogwall.servlet.FogwallServlet.PERSIST_CALLBACK_ATTR;
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
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A plain servlet {@link Filter} that persists push records to the {@link PushStore}. Unlike other fogwallFilters, this
 * wraps the entire filter chain using try-finally so it always runs - even when a validation filter commits the
 * response early (e.g., via {@code sendGitError}).
 *
 * <p>This filter should be registered BEFORE all other filters so its {@code finally} block executes after them.
 *
 * <p>Persistence is exposed to the rest of the chain as a callback under
 * {@link com.rbc.fogwall.servlet.FogwallServlet#PERSIST_CALLBACK_ATTR}: {@code sendGitError} (see
 * {@link FogwallFilter}) runs it immediately before writing the real response, so the store write always happens before
 * the response bytes reach the git client. Relying solely on the outer {@code finally} below would leave a window where
 * the client has already received (and can act on) a push ID that isn't queryable in the store yet - a client polling
 * right after the response, or a server crash in that window, would see the push as missing even though it was in fact
 * handled. The callback is idempotent ({@link AtomicBoolean}-guarded), so this filter's own {@code finally} can still
 * call it unconditionally as the fallback for paths that never call {@code sendGitError} at all (e.g. an ALLOWED push
 * forwarded upstream, whose record is instead updated later by
 * {@code FogwallServlet#onProxyResponseSuccess}/{@code onProxyResponseFailure}).
 */
@Slf4j
@RequiredArgsConstructor
public class PushStoreAuditFilter implements Filter {

    private final PushStore pushStore;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        AtomicBoolean persisted = new AtomicBoolean(false);
        Runnable persistOnce = () -> {
            if (persisted.compareAndSet(false, true)) {
                persistIfPush(request);
            }
        };
        request.setAttribute(PERSIST_CALLBACK_ATTR, persistOnce);
        try {
            chain.doFilter(request, response);
        } finally {
            persistOnce.run();
        }
    }

    private void persistIfPush(ServletRequest request) {
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
