package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.ScmApiGateResponse.deny;
import static com.rbc.fogwall.servlet.ScmApiGateResponse.denyWithoutNamingTarget;
import static com.rbc.fogwall.servlet.ScmApiGateResponse.tooLarge;
import static com.rbc.fogwall.servlet.ScmApiRequestContext.SCM_API_REQUEST_ATTR;

import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.scmapi.OwnerRepo;
import com.rbc.fogwall.servlet.PushTooLargeException;
import com.rbc.fogwall.servlet.RequestBodyWrapper;
import com.rbc.fogwall.servlet.ScmApiRequestContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * The SCM API proxy decision pipeline, shared across the three dialects. The frame is identical everywhere — bound the
 * body, then either forward a read, forward an authorized mutation, or let a dialect's own refusal stand — so it lives
 * here; only {@link #evaluate} differs per dialect. Denies are terminal: the response is written and the chain is not
 * called, so the forward servlet never sees a refused request. The refusal shapes live in
 * {@link com.rbc.fogwall.servlet.ScmApiGateResponse}.
 */
public interface ScmApiGateFilter extends Filter {

    RepoPermissionService repoPermissionService();

    String providerId();

    /**
     * This dialect's one allowlisted merge operation — authorized against {@code MERGE} rather than {@code PROPOSE}.
     */
    String mergeOperation();

    /**
     * Whether merging is enabled for this provider ({@code proposals.merge-enabled}); the merge op is refused if not.
     */
    boolean mergeEnabled();

    /**
     * Parses and allowlists the request, resolving the {@code owner/repo} a mutation targets. Sets the audit fields it
     * learns ({@code mutationField}, node ID, variables) on {@code context} as it goes, and writes its own terminal
     * refusal — returning {@link GateOutcome#REFUSED} — for a malformed, non-allowlisted, or unresolvable request.
     */
    GateOutcome evaluate(
            HttpServletRequest request,
            HttpServletResponse response,
            ScmApiRequestContext context,
            RequestBodyWrapper wrapper)
            throws IOException;

    /** What {@link #evaluate} decided: forward as-is, authorize a mutation first, or a refusal already written. */
    sealed interface GateOutcome permits GateOutcome.Forward, GateOutcome.Mutation, GateOutcome.Refused {
        record Forward() implements GateOutcome {}

        record Mutation(String operation, OwnerRepo target) implements GateOutcome {}

        record Refused() implements GateOutcome {}

        GateOutcome FORWARD = new Forward();
        GateOutcome REFUSED = new Refused();
    }

    @Override
    default void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        var context = (ScmApiRequestContext) httpRequest.getAttribute(SCM_API_REQUEST_ATTR);

        // Cheap pre-check, then a counting read: a chunked request declares no length, so the wrapper is the bound.
        long declared = httpRequest.getContentLengthLong();
        if (declared > ScmApiRequestContext.MAX_BODY_BYTES) {
            tooLarge(context, httpResponse, declared);
            return;
        }
        RequestBodyWrapper wrapper;
        try {
            wrapper = new RequestBodyWrapper(httpRequest, ScmApiRequestContext.MAX_BODY_BYTES);
        } catch (PushTooLargeException e) {
            tooLarge(context, httpResponse, e.getBytesRead());
            return;
        }

        switch (evaluate(httpRequest, httpResponse, context, wrapper)) {
            case GateOutcome.Refused ignored -> {
                // The dialect already wrote its terminal response.
            }
            case GateOutcome.Forward ignored -> chain.doFilter(wrapper, response);
            case GateOutcome.Mutation(String operation, OwnerRepo target) -> {
                context.setRepoOwner(target.owner());
                context.setRepoName(target.name());
                String repoPath = "/" + target.owner() + "/" + target.name();
                boolean isMerge = operation.equals(mergeOperation());
                if (isMerge && !mergeEnabled()) {
                    deny(
                            context,
                            httpResponse,
                            HttpServletResponse.SC_FORBIDDEN,
                            "Merging through fogwall is not enabled for this provider");
                    return;
                }
                boolean allowed = isMerge
                        ? repoPermissionService().isAllowedToMerge(context.getResolvedUser(), providerId(), repoPath)
                        : repoPermissionService().isAllowedToPropose(context.getResolvedUser(), providerId(), repoPath);
                if (!allowed) {
                    denyWithoutNamingTarget(
                            context,
                            httpResponse,
                            HttpServletResponse.SC_FORBIDDEN,
                            "User '" + context.getResolvedUser() + "' is not permitted to perform API mutations on "
                                    + repoPath);
                    return;
                }
                chain.doFilter(wrapper, response);
            }
        }
    }
}
