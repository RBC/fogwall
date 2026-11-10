package com.rbc.fogwall.dashboard.export;

import com.rbc.fogwall.db.model.RecordCursor;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The rows of one record type in an export, read lazily so an export of any size holds one batch in memory.
 *
 * @param type the record type's name, which is its folder in the zip
 * @param fields the record's exported properties, in order: every JSON row has these, a CSV row the chosen ones
 * @param columns the CSV columns chosen, a subset of {@code fields} in the caller's order
 * @param snapshot true for current state rather than a record of events; noted in the manifest
 */
public record ExportSource<T>(
        String type, List<String> fields, List<String> columns, boolean snapshot, Iterator<T> rows) {

    /** Rows read from the store this many at a time. */
    static final int BATCH = 1000;

    /**
     * A record type read from its store in cursor order, batch by batch, keeping the rows {@code keep} accepts.
     *
     * @param page reads up to the given number of rows after the cursor (null for the first batch)
     */
    public static <T> ExportSource<T> paged(
            String type,
            List<String> fields,
            List<String> columns,
            BiFunction<RecordCursor, Integer, List<T>> page,
            Function<T, RecordCursor> cursorOf,
            Predicate<T> keep) {
        return new ExportSource<>(type, fields, columns, false, new PagedIterator<>(page, cursorOf, keep));
    }

    /** Current state, already in hand. */
    public static <T> ExportSource<T> snapshot(String type, List<String> fields, List<String> columns, List<T> rows) {
        return new ExportSource<>(type, fields, columns, true, rows.iterator());
    }

    private static final class PagedIterator<T> implements Iterator<T> {
        private final BiFunction<RecordCursor, Integer, List<T>> page;
        private final Function<T, RecordCursor> cursorOf;
        private final Predicate<T> keep;
        private final Deque<T> buffered = new ArrayDeque<>();
        private RecordCursor position;
        private boolean exhausted;

        PagedIterator(
                BiFunction<RecordCursor, Integer, List<T>> page,
                Function<T, RecordCursor> cursorOf,
                Predicate<T> keep) {
            this.page = page;
            this.cursorOf = cursorOf;
            this.keep = keep;
        }

        @Override
        public boolean hasNext() {
            while (buffered.isEmpty() && !exhausted) {
                List<T> batch = page.apply(position, BATCH);
                if (batch.size() < BATCH) {
                    exhausted = true;
                }
                if (!batch.isEmpty()) {
                    position = cursorOf.apply(batch.getLast());
                }
                batch.stream().filter(keep).forEach(buffered::add);
            }
            return !buffered.isEmpty();
        }

        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return buffered.poll();
        }
    }
}
