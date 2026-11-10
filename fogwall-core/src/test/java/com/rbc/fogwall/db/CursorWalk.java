package com.rbc.fogwall.db;

import com.rbc.fogwall.db.model.RecordCursor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Reads a store page by page through its cursor, the way an external puller walks the whole record. */
public final class CursorWalk {

    private CursorWalk() {}

    /**
     * @param page reads the page after the given cursor (null for the first page)
     * @param cursorOf the cursor of a row
     * @param key what to collect from each row
     */
    public static <T, K> List<K> walk(
            Function<RecordCursor, List<T>> page, Function<T, RecordCursor> cursorOf, Function<T, K> key) {
        List<K> seen = new ArrayList<>();
        RecordCursor after = null;
        while (true) {
            List<T> rows = page.apply(after);
            if (rows.isEmpty()) {
                return seen;
            }
            rows.forEach(r -> seen.add(key.apply(r)));
            after = cursorOf.apply(rows.getLast());
        }
    }
}
