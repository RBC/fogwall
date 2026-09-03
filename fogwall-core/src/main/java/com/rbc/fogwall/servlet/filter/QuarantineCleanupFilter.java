package com.rbc.fogwall.servlet.filter;

import com.rbc.fogwall.git.QuarantineObjectStore;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;

/**
 * Discards the server mode push quarantine once the request is over.
 *
 * <p>The quarantine is opened by {@code ServerReceivePackFactory} while JGit's {@code GitServlet} is running, which is
 * inside this filter's {@code chain.doFilter} call — so parking it on the request and clearing it here is enough to
 * bound its lifetime to the request. Register this on the {@code /push} mapping; the transparent proxy handles its own
 * teardown inside {@code EnrichPushCommitsFilter}, which already wraps the rest of that chain.
 */
public class QuarantineCleanupFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        } finally {
            if (request.getAttribute(QuarantineObjectStore.REQUEST_ATTRIBUTE) instanceof QuarantineObjectStore q) {
                request.removeAttribute(QuarantineObjectStore.REQUEST_ATTRIBUTE);
                q.close();
            }
        }
    }
}
