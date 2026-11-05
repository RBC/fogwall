package com.rbc.fogwall.permission;

import java.util.Objects;

/**
 * Where an access change came from, recorded on every {@link PermissionChangeEvent} and {@link MembershipChangeEvent}.
 *
 * @param actor who made the change; null for {@link ChangeOrigin#CONFIG}
 * @param sourceRevision the configuration revision a {@link ChangeOrigin#CONFIG} change was reloaded from; null
 *     otherwise
 */
public record ChangeContext(ChangeOrigin origin, String actor, String sourceRevision) {

    public ChangeContext {
        Objects.requireNonNull(origin, "origin");
        if (origin == ChangeOrigin.CONFIG) {
            if (actor != null) {
                throw new IllegalArgumentException("a configuration change has no actor");
            }
        } else {
            Objects.requireNonNull(actor, "a dashboard or API change must name its actor");
            if (sourceRevision != null) {
                throw new IllegalArgumentException("only a configuration change has a source revision");
            }
        }
    }

    /** A change made by {@code actor} through the dashboard or the REST API. */
    public static ChangeContext by(ChangeOrigin origin, String actor) {
        return new ChangeContext(origin, actor, null);
    }

    /** A change applied by a live configuration reload from {@code sourceRevision}. */
    public static ChangeContext config(String sourceRevision) {
        return new ChangeContext(ChangeOrigin.CONFIG, null, sourceRevision);
    }
}
