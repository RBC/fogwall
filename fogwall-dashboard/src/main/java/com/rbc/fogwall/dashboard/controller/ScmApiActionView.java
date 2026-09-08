package com.rbc.fogwall.dashboard.controller;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.rbc.fogwall.db.model.ScmApiActionRecord;
import com.rbc.fogwall.db.model.ScmApiProposalRecord;

/** An audit record with the proposal it created or touched, when the upstream response named one. */
public final class ScmApiActionView {

    private final ScmApiActionRecord action;
    private final ScmApiProposalRecord proposal;

    public ScmApiActionView(ScmApiActionRecord action, ScmApiProposalRecord proposal) {
        this.action = action;
        this.proposal = proposal;
    }

    @JsonUnwrapped
    public ScmApiActionRecord getAction() {
        return action;
    }

    public ScmApiProposalRecord getProposal() {
        return proposal;
    }
}
