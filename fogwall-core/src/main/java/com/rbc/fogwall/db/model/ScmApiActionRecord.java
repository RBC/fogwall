package com.rbc.fogwall.db.model;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/**
 * Audit record for a single SCM API proxy mutation — one per proxied mutation, never per read. Same auditability bar as
 * {@link PushRecord}: who, which rule, what target, what evidence.
 */
@Data
@Builder
public class ScmApiActionRecord {

    /** Unique identifier for this action. */
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    /** When the action was received. */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /** Provider name (e.g. "github"). */
    private String provider;

    /** SCM login the caller's token resolved to (e.g. the GitHub username), before proxy-user resolution. */
    private String user;

    /** Fogwall proxy username the caller resolved to. */
    private String resolvedUser;

    /** Repository owner the mutation's node ID resolved to. Null if resolution failed. */
    private String repoOwner;

    /** Repository name the mutation's node ID resolved to. Null if resolution failed. */
    private String repoName;

    /** Schema mutation field, e.g. "createIssue" — parsed from the GraphQL AST, never a client alias. */
    private String mutationField;

    /** The opaque node ID the mutation targeted. */
    private String nodeId;

    /** Which node type {@link #nodeId} refers to (Repository, Issue, PullRequest, ...). */
    private String nodeType;

    private ScmApiActionStatus status;

    /** Human-readable reason for a {@link ScmApiActionStatus#DENIED} or {@link ScmApiActionStatus#ERROR} outcome. */
    private String reason;

    /** Raw {@code variables} JSON from the mutation request, for post-hoc audit. */
    private String variablesJson;

    /**
     * Raw {@code User-Agent} the caller sent. Each SCM CLI advertises its version here, making this the anchor for
     * spotting a wire-format change after a CLI upgrade. Caller-controlled: evidence, never an access-control input.
     */
    private String userAgent;

    /** {@link #userAgent} classified — e.g. {@code GH_CLI}, {@code BROWSER}. */
    private String clientType;
}
