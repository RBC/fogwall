package com.rbc.fogwall.db.model;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/**
 * One pull/merge request or issue that exists upstream because a mutation fogwall forwarded created it, or that a
 * forwarded mutation later touched. Mutable current state, keyed on what the upstream calls the thing —
 * {@code (provider, owner, repo, kind, number)} — as opposed to {@link ScmApiActionRecord}, which is the append-only
 * decision log; action records point here through {@link ScmApiActionRecord#getProposalId()}.
 */
@Data
@Builder
public class ScmApiProposalRecord {

    /**
     * What the upstream created. Issues and pull requests share a number space on GitHub and Forgejo, not on GitLab.
     */
    public enum Kind {
        PULL_REQUEST,
        ISSUE
    }

    /**
     * State as last reported by an upstream response fogwall relayed. {@link #MERGED} only ever comes from upstream.
     */
    public enum State {
        OPEN,
        CLOSED,
        MERGED
    }

    @Builder.Default
    private String id = UUID.randomUUID().toString();

    private String provider;
    private String repoOwner;
    private String repoName;
    private Kind kind;
    private int number;
    /** The upstream's own web URL for it, as the create response reported it. */
    private String url;
    /** GitHub's opaque GraphQL node ID; null on the REST dialects, which address by number. */
    private String nodeId;

    private String title;
    private State state;
    /** Fogwall user and SCM login whose forwarded mutation created it. */
    private String createdBy;

    private String createdByScmUsername;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant updatedAt = Instant.now();

    /** The action records of the create and of the most recent mutation that touched it. */
    private String createdActionId;

    private String lastActionId;
}
