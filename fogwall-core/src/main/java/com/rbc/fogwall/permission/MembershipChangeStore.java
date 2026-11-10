package com.rbc.fogwall.permission;

import java.util.List;

/**
 * Append-only record of {@link MembershipChangeEvent}s. Events are never updated or deleted, and nothing references the
 * group or user they name, so the record survives both being removed.
 */
public interface MembershipChangeStore {

    /** Called once at startup; implementations may use this to create schema or indexes. */
    default void initialize() {}

    void record(MembershipChangeEvent event);

    /** Events matching the query, ordered by {@code (occurredAt, id)} in the query's direction. */
    List<MembershipChangeEvent> find(MembershipChangeQuery query);
}
