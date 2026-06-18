package com.rbc.fogwall.db.model;

/** Lifecycle status of a push through the proxy. */
public enum PushStatus {
    /** Push received but not yet processed. */
    RECEIVED,
    /** Push is being validated by hooks/filters. */
    PROCESSING,
    /** Push passed validation and is awaiting manual review. */
    PENDING,
    /** Push approved (manually or automatically). */
    APPROVED,
    /** Push rejected (manually or automatically). */
    REJECTED,
    /** Push successfully forwarded to upstream. */
    FORWARDED,
    /** Push canceled by a reviewer. */
    CANCELED,
    /** An error occurred during processing. */
    ERROR
}
