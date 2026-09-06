package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.ScmApiRequestContext.SCM_API_REQUEST_ATTR;

import com.rbc.fogwall.db.ScmApiActionStore;
import com.rbc.fogwall.db.model.ScmApiActionRecord;
import com.rbc.fogwall.servlet.ScmApiRequestContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes one {@link ScmApiActionRecord} per proxied SCM API mutation — never per read, matching the "same bar as the
 * push path" auditability requirement.
 *
 * <p>Wraps the entire SCM API filter chain via try-finally, mirroring {@code PushStoreAuditFilter}, so it runs
 * whichever way the request resolved: a denial from {@link ScmApiGitHubGateFilter} (which never calls the chain
 * further), or a forward outcome recorded by {@link com.rbc.fogwall.servlet.ScmApiGraphQlForwardServlet} once the
 * upstream response is known. Must be registered FIRST so its {@code finally} block executes last.
 *
 * <p>A pure read has {@link ScmApiRequestContext#getMutationField()} {@code null} and is never audited individually.
 */
@Slf4j
@RequiredArgsConstructor
public class ScmApiAuditFilter implements Filter {

    private final ScmApiActionStore scmApiActionStore;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        } finally {
            var context = (ScmApiRequestContext) ((HttpServletRequest) request).getAttribute(SCM_API_REQUEST_ATTR);
            if (context != null && context.getMutationField() != null) {
                try {
                    scmApiActionStore.save(ScmApiActionRecord.builder()
                            .provider(context.getProvider())
                            .user(context.getScmLogin())
                            .resolvedUser(context.getResolvedUser())
                            .repoOwner(context.getRepoOwner())
                            .repoName(context.getRepoName())
                            .mutationField(context.getMutationField())
                            .nodeId(context.getNodeId())
                            .nodeType(context.getNodeType())
                            .status(context.getStatus())
                            .reason(context.getReason())
                            .variablesJson(context.getVariablesJson())
                            .userAgent(context.getUserAgent())
                            .clientType(
                                    context.getClientType() == null
                                            ? null
                                            : context.getClientType().name())
                            .build());
                } catch (RuntimeException e) {
                    log.error("Failed to write SCM API action audit record", e);
                }
            }
        }
    }
}
