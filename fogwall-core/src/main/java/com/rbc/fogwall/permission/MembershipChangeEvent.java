package com.rbc.fogwall.permission;

import com.rbc.fogwall.db.model.RecordCursor;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/**
 * One change to a {@link PermissionGroup}'s membership: who added or removed whom, from which group, when, and where
 * the change came from. Together with the group's {@link PermissionChangeEvent}s this answers who held a group-granted
 * capability at a given time. Events are append-only and outlive the group and user they name.
 *
 * <p>{@code occurredAt} is held to millisecond precision, as on {@link PermissionChangeEvent}.
 *
 * @param actor who made the change; null for a change loaded from configuration
 * @param sourceRevision the configuration revision a {@link ChangeOrigin#CONFIG} change was reloaded from
 * @param groupName the group's name when the change was made
 */
@Builder(toBuilder = true)
public record MembershipChangeEvent(
        String id,
        Instant occurredAt,
        Action action,
        ChangeOrigin origin,
        String actor,
        String sourceRevision,
        String groupId,
        String groupName,
        String member) {

    public MembershipChangeEvent {
        id = id == null ? UUID.randomUUID().toString() : id;
        occurredAt = (occurredAt == null ? Instant.now() : occurredAt).truncatedTo(ChronoUnit.MILLIS);
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(groupName, "groupName");
        Objects.requireNonNull(member, "member");
    }

    /** The position of this event in the store's order, for reading the page after it. */
    public RecordCursor cursor() {
        return new RecordCursor(occurredAt, id);
    }

    public enum Action {
        ADDED,
        REMOVED
    }
}
