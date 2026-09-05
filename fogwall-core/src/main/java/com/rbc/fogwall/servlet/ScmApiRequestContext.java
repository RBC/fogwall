package com.rbc.fogwall.servlet;

import com.rbc.fogwall.db.model.ScmApiActionStatus;
import com.rbc.fogwall.scmapi.ScmApiClientType;
import lombok.Data;

/**
 * Per-request state shared across the SCM API proxy filter chain, attached under {@link #SCM_API_REQUEST_ATTR}. Mirrors
 * {@code GitRequestDetails}'s role for the git-push pipeline, but for SCM API traffic — shared by both dialects
 * (GitHub's GraphQL and GitLab's REST).
 *
 * <p>{@link #mutationField} is {@code null} for a pure read (a GraphQL {@code query} document, or a REST {@code GET}) —
 * the audit filter only writes a {@link com.rbc.fogwall.db.model.ScmApiActionRecord} when it is set, since reads are
 * not audited individually.
 */
@Data
public class ScmApiRequestContext {

    /** Request attribute holding the {@link ScmApiRequestContext} for the current request. */
    public static final String SCM_API_REQUEST_ATTR = "com.rbc.fogwall.scmapi.context";

    private String provider;

    /** SCM login the caller's token resolved to, before proxy-user resolution. Set by the authenticate filter. */
    private String scmLogin;

    /** Fogwall proxy username. Set by the authenticate filter once identity resolution succeeds. */
    private String resolvedUser;

    /** Schema mutation field, e.g. "createIssue". {@code null} for a pure read. */
    private String mutationField;

    private String nodeId;
    private String nodeType;
    private String repoOwner;
    private String repoName;
    private String variablesJson;

    /**
     * Raw {@code User-Agent} as sent. Audited because every SCM CLI advertises its version, which is the anchor for
     * spotting a wire-format change after a CLI upgrade. Caller-controlled, so never an input to an access decision.
     */
    private String userAgent;

    /** {@link #userAgent} classified. Recorded for audit; only ever used to refuse a client type, never to permit. */
    private ScmApiClientType clientType;

    /**
     * Final outcome, set once known: {@code DENIED}/{@code ERROR} by the gate filter before it short-circuits the
     * chain, or {@code FORWARDED}/{@code ERROR} by the forwarding servlet once the upstream response is known. The
     * audit filter reads this in its {@code finally} block, after the chain has fully unwound either way.
     */
    private ScmApiActionStatus status;

    private String reason;
}
