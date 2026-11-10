package com.rbc.fogwall.permission;

import com.rbc.fogwall.db.model.RecordCursor;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/** In-memory {@link MembershipChangeStore}. Data is lost on restart. */
public class InMemoryMembershipChangeStore implements MembershipChangeStore {

    private final List<MembershipChangeEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void record(MembershipChangeEvent event) {
        events.add(event);
    }

    @Override
    public List<MembershipChangeEvent> find(MembershipChangeQuery query) {
        Comparator<RecordCursor> order = query.isNewestFirst() ? RecordCursor.ORDER.reversed() : RecordCursor.ORDER;
        return events.stream()
                .filter(matches(query))
                .filter(e -> query.getAfter() == null || order.compare(e.cursor(), query.getAfter()) > 0)
                .sorted(Comparator.comparing(MembershipChangeEvent::cursor, order))
                .skip(query.getOffset())
                .limit(query.getLimit())
                .toList();
    }

    private static Predicate<MembershipChangeEvent> matches(MembershipChangeQuery q) {
        return e -> (q.getAction() == null || q.getAction() == e.action())
                && (q.getOrigin() == null || q.getOrigin() == e.origin())
                && (q.getGroupName() == null || q.getGroupName().equals(e.groupName()))
                && (q.getMember() == null || q.getMember().equals(e.member()))
                && (q.getActor() == null || q.getActor().equals(e.actor()))
                && (q.getFrom() == null || !e.occurredAt().isBefore(q.getFrom()))
                && (q.getTo() == null || e.occurredAt().isBefore(q.getTo()))
                && (q.getSearch() == null
                        || q.getSearch().isBlank()
                        || contains(e.groupName(), q.getSearch())
                        || contains(e.member(), q.getSearch()));
    }

    private static boolean contains(String value, String search) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT));
    }
}
