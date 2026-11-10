package com.rbc.fogwall.db.model;

/**
 * Pull/merge requests or issues opened through fogwall in one {@link ActivityKey} group, by the state the entity
 * registry last saw them in.
 *
 * @param opened {@link ScmApiActivity#OPEN_PULL_REQUEST} or {@link ScmApiActivity#OPEN_ISSUE}
 * @param state null when the create's response named no entity fogwall could register
 */
public record ProposalOutcomeCount(
        ActivityKey key, ScmApiActivity opened, ScmApiEntityRecord.State state, long count) {}
