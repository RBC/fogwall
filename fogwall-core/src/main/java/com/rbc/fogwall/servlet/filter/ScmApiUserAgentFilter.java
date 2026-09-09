package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.ScmApiRequestContext.SCM_API_REQUEST_ATTR;

import com.rbc.fogwall.scmapi.ScmApiClientType;
import com.rbc.fogwall.servlet.ScmApiRequestContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.apache.hc.core5.http.HttpHeaders;

/**
 * Classifies the calling client from its {@code User-Agent} and records it on the request context for the audit trail.
 * Every CLI advertises its version ({@code GitHub CLI 2.98.0}, {@code glab/v1.116.0}, {@code tea/0.15.1},
 * {@code forgejo-cli/0.6.0}), which is the anchor for noticing a CLI upgrade has changed its wire format — a mutation
 * that stops matching the allowlist otherwise surfaces only as an unexplained denial.
 *
 * <p>Records only, never gates: {@code User-Agent} is caller-controlled, so nothing downstream may branch on the
 * classification to grant or refuse access. Runs after {@link ScmApiAuthenticateFilter} so the recorded client is
 * attributed to a resolved user.
 */
public class ScmApiUserAgentFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String userAgent = httpRequest.getHeader(HttpHeaders.USER_AGENT);

        var context = (ScmApiRequestContext) httpRequest.getAttribute(SCM_API_REQUEST_ATTR);
        if (context != null) {
            context.setUserAgent(userAgent);
            context.setClientType(ScmApiClientType.classify(userAgent));
            context.setClientVersion(ScmApiClientType.version(userAgent));
        }

        chain.doFilter(request, response);
    }
}
