package com.rbc.fogwall.permission;

import com.rbc.fogwall.db.model.RecordCursor;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

/** Query parameters for role change events. All filters are optional; null means "don't filter on this". */
@Data
@Builder
public class RoleChangeQuery {
    private ChangeOrigin origin;
    private String username;
    private String actor;

    /** Inclusive lower bound on {@code occurredAt}. */
    private Instant from;

    /** Exclusive upper bound on {@code occurredAt}. */
    private Instant to;

    /** Free-text search: matches events whose username contains this value (case-insensitive). */
    private String search;

    /** Return only events that come after this position in the requested order. */
    private RecordCursor after;

    /** Maximum number of results to return. */
    @Builder.Default
    private int limit = 100;

    /** Number of results to skip (for pagination). */
    @Builder.Default
    private int offset = 0;

    /** Order results by {@code occurredAt} descending (newest first). Defaults to true. */
    @Builder.Default
    private boolean newestFirst = true;
}
