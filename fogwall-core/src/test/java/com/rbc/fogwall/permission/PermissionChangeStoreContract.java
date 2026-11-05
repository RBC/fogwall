package com.rbc.fogwall.permission;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.model.MatchTarget;
import com.rbc.fogwall.db.model.MatchType;
import com.rbc.fogwall.db.model.RecordCursor;
import com.rbc.fogwall.permission.PermissionChangeEvent.Action;
import com.rbc.fogwall.permission.PermissionChangeEvent.SubjectType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What every {@link PermissionChangeStore} must do, run against each implementation by a subclass. */
public abstract class PermissionChangeStoreContract {

    private static final Instant T0 = Instant.parse("2026-11-05T14:00:00.123Z");

    protected abstract PermissionChangeStore store();

    private static PermissionChangeEvent.PermissionChangeEventBuilder event(String subject, Instant at) {
        return PermissionChangeEvent.builder()
                .occurredAt(at)
                .action(Action.GRANTED)
                .origin(ChangeOrigin.DASHBOARD)
                .actor("admin")
                .subjectType(SubjectType.USER)
                .subject(subject)
                .permissionId("perm-" + subject)
                .provider("github")
                .target(MatchTarget.SLUG)
                .value("/acme/widgets")
                .matchType(MatchType.LITERAL)
                .grant(RepoPermission.Grant.PUSH);
    }

    private void record(PermissionChangeEvent.PermissionChangeEventBuilder... events) {
        for (var e : events) {
            store().record(e.build());
        }
    }

    private List<PermissionChangeEvent> find(PermissionChangeQuery.PermissionChangeQueryBuilder query) {
        return store().find(query.build());
    }

    private static List<String> subjects(List<PermissionChangeEvent> events) {
        return events.stream().map(PermissionChangeEvent::subject).toList();
    }

    @Test
    void record_roundTripsEveryField() {
        PermissionChangeEvent written = PermissionChangeEvent.builder()
                .id("evt-1")
                .occurredAt(T0)
                .action(Action.REVOKED)
                .origin(ChangeOrigin.CONFIG)
                .sourceRevision("4f2c9e1")
                .subjectType(SubjectType.GROUP)
                .subject("platform-team")
                .permissionId("rule-1")
                .provider("gitlab")
                .target(MatchTarget.OWNER)
                .value("acme-*")
                .matchType(MatchType.GLOB)
                .grant(RepoPermission.Grant.MAINTAIN)
                .build();

        store().record(written);

        assertEquals(List.of(written), find(PermissionChangeQuery.builder()));
    }

    @Test
    void find_ordersNewestFirstByDefault_andOldestFirstOnRequest() {
        record(event("b", T0.plusSeconds(1)), event("a", T0), event("c", T0.plusSeconds(2)));

        assertEquals(List.of("c", "b", "a"), subjects(find(PermissionChangeQuery.builder())));
        assertEquals(
                List.of("a", "b", "c"),
                subjects(find(PermissionChangeQuery.builder().newestFirst(false))));
    }

    @Test
    void find_filtersOnEachField() {
        record(
                event("alice", T0),
                event("bob", T0.plusSeconds(1)).action(Action.REVOKED),
                event("carol", T0.plusSeconds(2)).origin(ChangeOrigin.API).actor("ops"),
                event("devs", T0.plusSeconds(3)).subjectType(SubjectType.GROUP),
                event("erin", T0.plusSeconds(4)).provider("gitlab"));

        assertEquals(
                List.of("bob"), subjects(find(PermissionChangeQuery.builder().action(Action.REVOKED))));
        assertEquals(
                List.of("carol"), subjects(find(PermissionChangeQuery.builder().origin(ChangeOrigin.API))));
        assertEquals(
                List.of("carol"), subjects(find(PermissionChangeQuery.builder().actor("ops"))));
        assertEquals(
                List.of("devs"), subjects(find(PermissionChangeQuery.builder().subjectType(SubjectType.GROUP))));
        assertEquals(
                List.of("alice"), subjects(find(PermissionChangeQuery.builder().subject("alice"))));
        assertEquals(
                List.of("erin"), subjects(find(PermissionChangeQuery.builder().provider("gitlab"))));
    }

    @Test
    void find_fromIsInclusive_toIsExclusive() {
        record(event("a", T0), event("b", T0.plusSeconds(1)), event("c", T0.plusSeconds(2)));

        assertEquals(
                List.of("b", "a"),
                subjects(find(PermissionChangeQuery.builder().from(T0).to(T0.plusSeconds(2)))));
    }

    @Test
    void find_searchMatchesSubjectOrPattern_caseInsensitively() {
        record(event("Alice", T0), event("bob", T0.plusSeconds(1)).value("/ACME/gadgets"), event("carol", T0));

        assertEquals(
                List.of("Alice"), subjects(find(PermissionChangeQuery.builder().search("alic"))));
        assertEquals(
                List.of("bob"), subjects(find(PermissionChangeQuery.builder().search("gadget"))));
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
            List<PermissionChangeEvent> page = find(PermissionChangeQuery.builder()
                    .newestFirst(newestFirst)
                    .after(after)
                    .limit(2));
            if (page.isEmpty()) {
                return seen;
            }
            seen.addAll(subjects(page));
            after = page.getLast().cursor();
        }
    }

    @Test
    void find_appliesLimitAndOffset() {
        record(event("a", T0), event("b", T0.plusSeconds(1)), event("c", T0.plusSeconds(2)));

        assertEquals(
                List.of("b"),
                subjects(find(PermissionChangeQuery.builder().offset(1).limit(1))));
    }

    @Test
    void record_keepsMillisecondPrecision() {
        Instant micros = Instant.parse("2026-11-05T14:00:00.123456Z");
        record(event("a", micros));

        assertEquals(
                Instant.parse("2026-11-05T14:00:00.123Z"),
                find(PermissionChangeQuery.builder()).getFirst().occurredAt());
    }
}
