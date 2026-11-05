package com.rbc.fogwall.permission;

import com.rbc.fogwall.db.model.MatchTarget;
import com.rbc.fogwall.db.model.MatchType;
import com.rbc.fogwall.db.model.RecordCursor;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/**
 * One change to a user or group repository permission: who made it, whose access it changed, on which repositories,
 * which capability, when, and where the change came from. Events are append-only and outlive the grant they describe —
 * a revoked grant's row is deleted, its revocation stays here.
 *
 * <p>{@code occurredAt} is held to millisecond precision so every store returns the instant it was given and the
 * {@link RecordCursor} compares equally on each.
 *
 * @param permissionId id of the {@link RepoPermission} or {@link GroupPermissionRule} the event is about
 * @param actor who made the change; null for a change loaded from configuration
 * @param sourceRevision the configuration revision a {@link ChangeOrigin#CONFIG} change was reloaded from
 * @param subject the username for a {@link SubjectType#USER} grant, the group name for a {@link SubjectType#GROUP} one
 * @param value the repository pattern, interpreted per {@code target} and {@code matchType}
 */
@Builder(toBuilder = true)
public record PermissionChangeEvent(
        String id,
        Instant occurredAt,
        Action action,
        ChangeOrigin origin,
        String actor,
        String sourceRevision,
        SubjectType subjectType,
        String subject,
        String permissionId,
        String provider,
        MatchTarget target,
        String value,
        MatchType matchType,
        RepoPermission.Grant grant) {

    public PermissionChangeEvent {
        id = id == null ? UUID.randomUUID().toString() : id;
        occurredAt = (occurredAt == null ? Instant.now() : occurredAt).truncatedTo(ChronoUnit.MILLIS);
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(subjectType, "subjectType");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(permissionId, "permissionId");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(matchType, "matchType");
        Objects.requireNonNull(grant, "grant");
    }

    /** The position of this event in the store's order, for reading the page after it. */
    public RecordCursor cursor() {
        return new RecordCursor(occurredAt, id);
    }

    public enum Action {
        GRANTED,
        REVOKED
    }

    public enum SubjectType {
        USER,
        GROUP
    }
}
