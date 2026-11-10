package com.rbc.fogwall.db.model;

/** How many pushes in one {@link ActivityKey} group are in {@code status}. */
public record PushActivityCount(ActivityKey key, PushStatus status, long count) {}
