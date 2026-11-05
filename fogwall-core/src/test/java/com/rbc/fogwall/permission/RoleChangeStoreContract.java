package com.rbc.fogwall.permission;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.model.RecordCursor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What every {@link RoleChangeStore} must do, run against each implementation by a subclass. */
public abstract class RoleChangeStoreContract {

    private static final Instant T0 = Instant.parse("2026-11-05T14:00:00.123Z");

    protected abstract RoleChangeStore store();

    private static RoleChangeEvent.RoleChangeEventBuilder event(String username, Instant at) {
        return RoleChangeEvent.builder()
                .occurredAt(at)
                .origin(ChangeOrigin.IDP)
                .actor("oidc")
                .username(username)
                .previousRoles(List.of("USER"))
                .roles(List.of("USER", "ADMIN"));
    }

    private void record(RoleChangeEvent.RoleChangeEventBuilder... events) {
        for (var e : events) {
            store().record(e.build());
        }
    }

    private List<RoleChangeEvent> find(RoleChangeQuery.RoleChangeQueryBuilder query) {
        return store().find(query.build());
    }

    private static List<String> usernames(List<RoleChangeEvent> events) {
        return events.stream().map(RoleChangeEvent::username).toList();
    }

    @Test
    void record_roundTripsEveryField_withRolesSorted() {
        RoleChangeEvent written = RoleChangeEvent.builder()
                .id("evt-1")
                .occurredAt(T0)
                .origin(ChangeOrigin.DASHBOARD)
                .actor("admin")
                .username("alice")
                .previousRoles(List.of())
                .roles(List.of("USER", "ADMIN"))
                .build();

        store().record(written);

        List<RoleChangeEvent> found = find(RoleChangeQuery.builder());
        assertEquals(List.of(written), found);
        assertEquals(List.of("ADMIN", "USER"), found.getFirst().roles());
    }

    @Test
    void record_aDeletedUser_roundTripsNoRoles() {
        record(event("alice", T0).previousRoles(List.of("ADMIN")).roles(List.of()));

        assertEquals(List.of(), find(RoleChangeQuery.builder()).getFirst().roles());
    }

    @Test
    void find_filtersOnEachField_andSearchesTheUsername() {
        record(
                event("Alice", T0),
                event("bob", T0.plusSeconds(1)).origin(ChangeOrigin.API).actor("api-key"),
                event("carol", T0.plusSeconds(2)));

        assertEquals(List.of("bob"), usernames(find(RoleChangeQuery.builder().origin(ChangeOrigin.API))));
        assertEquals(List.of("bob"), usernames(find(RoleChangeQuery.builder().actor("api-key"))));
        assertEquals(List.of("carol"), usernames(find(RoleChangeQuery.builder().username("carol"))));
        assertEquals(List.of("Alice"), usernames(find(RoleChangeQuery.builder().search("alic"))));
        assertEquals(
                List.of("bob", "Alice"),
                usernames(find(RoleChangeQuery.builder().from(T0).to(T0.plusSeconds(2)))));
    }

    @Test
    void find_afterACursor_walksEveryEventOnceInBothDirections() {
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
            List<RoleChangeEvent> page = find(RoleChangeQuery.builder()
                    .newestFirst(newestFirst)
                    .after(after)
                    .limit(2));
            if (page.isEmpty()) {
                return seen;
            }
            seen.addAll(usernames(page));
            after = page.getLast().cursor();
        }
    }

    @Test
    void find_appliesLimitAndOffset() {
        record(event("a", T0), event("b", T0.plusSeconds(1)), event("c", T0.plusSeconds(2)));

        assertEquals(
                List.of("b"), usernames(find(RoleChangeQuery.builder().offset(1).limit(1))));
    }
}
