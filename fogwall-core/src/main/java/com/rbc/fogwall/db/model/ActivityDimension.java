package com.rbc.fogwall.db.model;

/** What an activity aggregate is grouped by, besides the outcome every aggregate is always split on. */
public enum ActivityDimension {
    /** The provider alone. Implied by {@link #REPO}. */
    PROVIDER,
    /** Provider, owner and repository name. */
    REPO,
    /** The fogwall user the record resolved to. */
    USER,
    /** The period of {@link ActivityQuery#periodStarts()} the record falls in. */
    PERIOD
}
