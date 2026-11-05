package com.rbc.fogwall.permission;

import com.rbc.fogwall.db.model.RecordCursor;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/**
 * One change to a user's roles: who changed them, whose, from what to what, when, and where the change came from. A
 * user's roles decide whether their repository grants count at all, so a role change is an access change even when no
 * grant moves. Events are append-only and outlive the user they name.
 *
 * <p>Both role lists are held sorted and de-duplicated, so two events for the same roles compare equal. A new user has
 * no previous roles; a deleted one has none after.
 *
 * <p>{@code occurredAt} is held to millisecond precision, as on {@link PermissionChangeEvent}.
 *
 * @param actor who made the change; for {@link ChangeOrigin#IDP}, the identity provider that asserted the roles
 */
@Builder(toBuilder = true)
public record RoleChangeEvent(
        String id,
        Instant occurredAt,
        ChangeOrigin origin,
        String actor,
        String username,
        List<String> previousRoles,
        List<String> roles) {

    public RoleChangeEvent {
        id = id == null ? UUID.randomUUID().toString() : id;
        occurredAt = (occurredAt == null ? Instant.now() : occurredAt).truncatedTo(ChronoUnit.MILLIS);
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(username, "username");
        previousRoles = normalize(previousRoles);
        roles = normalize(roles);
    }

    /** Sorted and de-duplicated; null is no roles. */
    public static List<String> normalize(Collection<String> roles) {
        return roles == null ? List.of() : roles.stream().distinct().sorted().toList();
    }

    /** The position of this event in the store's order, for reading the page after it. */
    public RecordCursor cursor() {
        return new RecordCursor(occurredAt, id);
    }
}
