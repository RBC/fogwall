package com.rbc.fogwall.permission;

import com.rbc.fogwall.db.model.RecordCursor;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/** In-memory {@link PermissionChangeStore}. Data is lost on restart. */
public class InMemoryPermissionChangeStore implements PermissionChangeStore {

    private final List<PermissionChangeEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void record(PermissionChangeEvent event) {
        events.add(event);
    }

    @Override
    public List<PermissionChangeEvent> find(PermissionChangeQuery query) {
        Comparator<RecordCursor> order = query.isNewestFirst() ? RecordCursor.ORDER.reversed() : RecordCursor.ORDER;
        return events.stream()
                .filter(matches(query))
                .filter(e -> query.getAfter() == null || order.compare(e.cursor(), query.getAfter()) > 0)
                .sorted(Comparator.comparing(PermissionChangeEvent::cursor, order))
                .skip(query.getOffset())
                .limit(query.getLimit())
                .toList();
    }

    private static Predicate<PermissionChangeEvent> matches(PermissionChangeQuery q) {
        return e -> (q.getAction() == null || q.getAction() == e.action())
                && (q.getOrigin() == null || q.getOrigin() == e.origin())
                && (q.getSubjectType() == null || q.getSubjectType() == e.subjectType())
                && (q.getSubject() == null || q.getSubject().equals(e.subject()))
                && (q.getActor() == null || q.getActor().equals(e.actor()))
                && (q.getProvider() == null || q.getProvider().equals(e.provider()))
                && (q.getFrom() == null || !e.occurredAt().isBefore(q.getFrom()))
                && (q.getTo() == null || e.occurredAt().isBefore(q.getTo()))
                && (q.getSearch() == null
                        || q.getSearch().isBlank()
                        || contains(e.subject(), q.getSearch())
                        || contains(e.value(), q.getSearch()));
    }

    private static boolean contains(String value, String search) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT));
    }
}
