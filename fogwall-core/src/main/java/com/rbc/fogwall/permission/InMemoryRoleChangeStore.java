package com.rbc.fogwall.permission;

import com.rbc.fogwall.db.model.RecordCursor;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/** In-memory {@link RoleChangeStore}. Data is lost on restart. */
public class InMemoryRoleChangeStore implements RoleChangeStore {

    private final List<RoleChangeEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void record(RoleChangeEvent event) {
        events.add(event);
    }

    @Override
    public List<RoleChangeEvent> find(RoleChangeQuery query) {
        Comparator<RecordCursor> order = query.isNewestFirst() ? RecordCursor.ORDER.reversed() : RecordCursor.ORDER;
        return events.stream()
                .filter(matches(query))
                .filter(e -> query.getAfter() == null || order.compare(e.cursor(), query.getAfter()) > 0)
                .sorted(Comparator.comparing(RoleChangeEvent::cursor, order))
                .skip(query.getOffset())
                .limit(query.getLimit())
                .toList();
    }

    private static Predicate<RoleChangeEvent> matches(RoleChangeQuery q) {
        return e -> (q.getOrigin() == null || q.getOrigin() == e.origin())
                && (q.getUsername() == null || q.getUsername().equals(e.username()))
                && (q.getActor() == null || q.getActor().equals(e.actor()))
                && (q.getFrom() == null || !e.occurredAt().isBefore(q.getFrom()))
                && (q.getTo() == null || e.occurredAt().isBefore(q.getTo()))
                && (q.getSearch() == null
                        || q.getSearch().isBlank()
                        || e.username()
                                .toLowerCase(Locale.ROOT)
                                .contains(q.getSearch().toLowerCase(Locale.ROOT)));
    }
}
