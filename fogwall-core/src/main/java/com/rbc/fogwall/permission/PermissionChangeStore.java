package com.rbc.fogwall.permission;

import java.util.List;

/**
 * Append-only record of {@link PermissionChangeEvent}s. Events are never updated or deleted, and nothing references the
 * grant, user or group they describe, so the record survives all three being removed.
 */
public interface PermissionChangeStore {

    /** Called once at startup; implementations may use this to create schema or indexes. */
    default void initialize() {}

    void record(PermissionChangeEvent event);

    /** Events matching the query, ordered by {@code (occurredAt, id)} in the query's direction. */
    List<PermissionChangeEvent> find(PermissionChangeQuery query);
}
