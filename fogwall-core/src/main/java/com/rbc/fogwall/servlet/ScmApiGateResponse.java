package com.rbc.fogwall.servlet;

import com.rbc.fogwall.db.model.ScmApiActionStatus;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

/**
 * The refusal shapes a dialect's gate filter ({@code ScmApiGitHubGateFilter}, {@code ScmApiGitLabGateFilter},
 * {@code ScmApiForgejoGateFilter}) responds with — identical across all three, since the decision points (not
 * allowlisted, not authorized, oversize body, unparseable input) are dialect-agnostic even though what parses the
 * request into those points is not.
 */
@Slf4j
public final class ScmApiGateResponse {

    private ScmApiGateResponse() {}

    /**
     * Refuses the request because a policy said no — not allowlisted, not enabled, or the caller lacks the grant.
     * Recorded as {@link ScmApiActionStatus#DENIED}: fogwall reached a decision, and the decision was no.
     */
    public static void deny(ScmApiRequestContext context, HttpServletResponse response, int status, String reason)
            throws IOException {
        respond(context, response, status, ScmApiActionStatus.DENIED, reason);
    }

    /** A denial whose reason names a repository, told to the caller without it. */
    public static void denyWithoutNamingTarget(
            ScmApiRequestContext context, HttpServletResponse response, int status, String reason) throws IOException {
        respond(
                context,
                response,
                status,
                ScmApiActionStatus.DENIED,
                reason,
                "Not permitted to perform this operation on its target");
    }

    /**
     * Refuses an over-size body. Recorded as {@link ScmApiActionStatus#ERROR}: fogwall reached no decision, having
     * declined to read enough of the request to have one.
     */
    public static void tooLarge(ScmApiRequestContext context, HttpServletResponse response, long actualBytes)
            throws IOException {
        respond(
                context,
                response,
                HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                ScmApiActionStatus.ERROR,
                "Request body exceeds " + ScmApiRequestContext.MAX_BODY_BYTES + " bytes (" + actualBytes + ")");
    }

    /**
     * Refuses the request because no decision was reachable — unparseable input, or a target that resolved to no
     * repository. Recorded as {@link ScmApiActionStatus#ERROR} rather than DENIED so filtering the trail for denials
     * shows policy violations rather than malformed requests. Fails closed either way.
     */
    public static void fail(ScmApiRequestContext context, HttpServletResponse response, int status, String reason)
            throws IOException {
        respond(context, response, status, ScmApiActionStatus.ERROR, reason);
    }

    private static void respond(
            ScmApiRequestContext context,
            HttpServletResponse response,
            int status,
            ScmApiActionStatus actionStatus,
            String reason)
            throws IOException {
        respond(context, response, status, actionStatus, reason, reason);
    }

    /**
     * Refuses the request, recording {@code reason} and telling the caller {@code clientMessage}.
     *
     * <p>The two differ where the reason names a repository the caller did not: the node cache is shared between users,
     * so naming what an ID resolved to would answer a question the caller's own token could not. The audit record keeps
     * the full reason.
     */
    private static void respond(
            ScmApiRequestContext context,
            HttpServletResponse response,
            int status,
            ScmApiActionStatus actionStatus,
            String reason,
            String clientMessage)
            throws IOException {
        log.debug("SCM API proxy request refused ({}): {}", actionStatus, reason);
        if (context != null) {
            context.setStatus(actionStatus);
            context.setReason(reason);
        }
        ScmApiErrorResponse.write(response, status, clientMessage);
    }
}
