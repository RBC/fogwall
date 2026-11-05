package com.rbc.fogwall.db.model;

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;

/**
 * A position in a record store's {@code (time, id)} order, for reading the page after it. The id breaks ties between
 * records sharing an instant, so a walk neither skips nor repeats a record.
 */
public record RecordCursor(Instant at, String id) {

    /** Oldest first: by time, then id. */
    public static final Comparator<RecordCursor> ORDER =
            Comparator.comparing(RecordCursor::at).thenComparing(RecordCursor::id);

    public RecordCursor {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(id, "id");
    }
}
