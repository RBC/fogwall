package com.rbc.fogwall.dashboard.controller;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.rbc.fogwall.db.model.ScmApiActionRecord;
import com.rbc.fogwall.db.model.ScmApiEntityRecord;

/** An audit record with the pull/merge request or issue it created or touched, when the upstream response named one. */
public final class ScmApiActionView {

    private final ScmApiActionRecord action;
    private final ScmApiEntityRecord entity;

    public ScmApiActionView(ScmApiActionRecord action, ScmApiEntityRecord entity) {
        this.action = action;
        this.entity = entity;
    }

    @JsonUnwrapped
    public ScmApiActionRecord getAction() {
        return action;
    }

    public ScmApiEntityRecord getEntity() {
        return entity;
    }
}
