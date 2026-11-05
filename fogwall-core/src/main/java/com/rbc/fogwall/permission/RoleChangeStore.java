package com.rbc.fogwall.permission;

import java.util.List;

/**
 * Append-only record of {@link RoleChangeEvent}s. Events are never updated or deleted, and nothing references the user
 * they name, so the record survives the user being removed.
 */
public interface RoleChangeStore {

    /** Called once at startup; implementations may use this to create schema or indexes. */
    default void initialize() {}

    void record(RoleChangeEvent event);

    /** Events matching the query, ordered by {@code (occurredAt, id)} in the query's direction. */
    List<RoleChangeEvent> find(RoleChangeQuery query);
}
