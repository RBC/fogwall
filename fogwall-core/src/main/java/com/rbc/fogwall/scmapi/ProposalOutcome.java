package com.rbc.fogwall.scmapi;

import com.rbc.fogwall.db.model.ScmApiProposalRecord;

/**
 * What one forwarded mutation's upstream response says about the proposal it created or touched. Every field but
 * {@code kind} may be null: a comment response names its issue by number and nothing else, and GitHub's close and
 * update mutations return no object at all, in which case the target is known only by {@code nodeId}.
 */
public record ProposalOutcome(
        ScmApiProposalRecord.Kind kind,
        Integer number,
        String url,
        String nodeId,
        String title,
        ScmApiProposalRecord.State state) {}
