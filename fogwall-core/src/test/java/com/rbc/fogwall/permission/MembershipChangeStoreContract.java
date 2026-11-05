package com.rbc.fogwall.permission;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.model.RecordCursor;
import com.rbc.fogwall.permission.MembershipChangeEvent.Action;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What every {@link MembershipChangeStore} must do, run against each implementation by a subclass. */
public abstract class MembershipChangeStoreContract {

    private static final Instant T0 = Instant.parse("2026-11-05T14:00:00.123Z");

    protected abstract MembershipChangeStore store();

    private static MembershipChangeEvent.MembershipChangeEventBuilder event(String member, Instant at) {
        return MembershipChangeEvent.builder()
                .occurredAt(at)
                .action(Action.ADDED)
                .origin(ChangeOrigin.DASHBOARD)
                .actor("admin")
                .groupId("group-1")
                .groupName("platform-team")
                .member(member);
    }

    private void record(MembershipChangeEvent.MembershipChangeEventBuilder... events) {
        for (var e : events) {
            store().record(e.build());
        }
    }

    private List<MembershipChangeEvent> find(MembershipChangeQuery.MembershipChangeQueryBuilder query) {
        return store().find(query.build());
    }

    private static List<String> members(List<MembershipChangeEvent> events) {
        return events.stream().map(MembershipChangeEvent::member).toList();
    }

    @Test
    void record_roundTripsEveryField() {
        MembershipChangeEvent written = MembershipChangeEvent.builder()
                .id("evt-1")
                .occurredAt(T0)
                .action(Action.REMOVED)
                .origin(ChangeOrigin.CONFIG)
                .sourceRevision("4f2c9e1")
                .groupId("group-9")
                .groupName("release-managers")
                .member("alice")
                .build();

        store().record(written);

        assertEquals(List.of(written), find(MembershipChangeQuery.builder()));
    }

    @Test
    void find_ordersNewestFirstByDefault_andOldestFirstOnRequest() {
        record(event("b", T0.plusSeconds(1)), event("a", T0), event("c", T0.plusSeconds(2)));

        assertEquals(List.of("c", "b", "a"), members(find(MembershipChangeQuery.builder())));
        assertEquals(
                List.of("a", "b", "c"),
                members(find(MembershipChangeQuery.builder().newestFirst(false))));
    }

    @Test
    void find_filtersOnEachField() {
        record(
                event("alice", T0),
                event("bob", T0.plusSeconds(1)).action(Action.REMOVED),
                event("carol", T0.plusSeconds(2)).origin(ChangeOrigin.API).actor("ops"),
                event("dave", T0.plusSeconds(3)).groupId("group-2").groupName("auditors"));

        assertEquals(
                List.of("bob"), members(find(MembershipChangeQuery.builder().action(Action.REMOVED))));
        assertEquals(
                List.of("carol"), members(find(MembershipChangeQuery.builder().origin(ChangeOrigin.API))));
        assertEquals(
                List.of("carol"), members(find(MembershipChangeQuery.builder().actor("ops"))));
        assertEquals(
                List.of("dave"), members(find(MembershipChangeQuery.builder().groupName("auditors"))));
        assertEquals(
                List.of("alice"), members(find(MembershipChangeQuery.builder().member("alice"))));
    }

    @Test
    void find_fromIsInclusive_toIsExclusive() {
        record(event("a", T0), event("b", T0.plusSeconds(1)), event("c", T0.plusSeconds(2)));

        assertEquals(
                List.of("b", "a"),
                members(find(MembershipChangeQuery.builder().from(T0).to(T0.plusSeconds(2)))));
    }

    @Test
    void find_searchMatchesGroupOrMember_caseInsensitively() {
        record(event("Alice", T0), event("bob", T0.plusSeconds(1)).groupName("Release-Managers"), event("carol", T0));

        assertEquals(
                List.of("Alice"), members(find(MembershipChangeQuery.builder().search("alic"))));
        assertEquals(
                List.of("bob"), members(find(MembershipChangeQuery.builder().search("release"))));
    }

    @Test
    void find_afterACursor_walksEveryEventOnceInBothDirections() {
        // Two pairs share an instant, so the id must break the tie for the walk to neither skip nor repeat.
        record(
                event("a", T0).id("id-1"),
                event("b", T0).id("id-2"),
                event("c", T0.plusMillis(1)).id("id-3"),
                event("d", T0.plusMillis(1)).id("id-0"),
                event("e", T0.plusMillis(2)).id("id-4"));

        assertEquals(List.of("e", "c", "d", "b", "a"), walk(true));
        assertEquals(List.of("a", "b", "d", "c", "e"), walk(false));
    }

    private List<String> walk(boolean newestFirst) {
        List<String> seen = new ArrayList<>();
        RecordCursor after = null;
        while (true) {
            List<MembershipChangeEvent> page = find(MembershipChangeQuery.builder()
                    .newestFirst(newestFirst)
                    .after(after)
                    .limit(2));
            if (page.isEmpty()) {
                return seen;
            }
            seen.addAll(members(page));
            after = page.getLast().cursor();
        }
    }

    @Test
    void find_appliesLimitAndOffset() {
        record(event("a", T0), event("b", T0.plusSeconds(1)), event("c", T0.plusSeconds(2)));

        assertEquals(
                List.of("b"),
                members(find(MembershipChangeQuery.builder().offset(1).limit(1))));
    }

    @Test
    void record_keepsMillisecondPrecision() {
        record(event("a", Instant.parse("2026-11-05T14:00:00.123456Z")));

        assertEquals(
                Instant.parse("2026-11-05T14:00:00.123Z"),
                find(MembershipChangeQuery.builder()).getFirst().occurredAt());
    }
}
