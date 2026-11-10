package com.rbc.fogwall.db;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.model.RecordCursor;
import com.rbc.fogwall.db.model.ScmApiActionQuery;
import com.rbc.fogwall.db.model.ScmApiActionRecord;
import com.rbc.fogwall.db.model.ScmApiActionStatus;
import java.time.Instant;
import java.util.List;

/** Cursor paging and the time window, checked the same way against each {@link ScmApiActionStore}. */
public final class ScmApiActionPagingCheck {

    private ScmApiActionPagingCheck() {}

    private static ScmApiActionRecord at(String id, Instant timestamp) {
        return ScmApiActionRecord.builder()
                .id(id)
                .timestamp(timestamp)
                .provider("github")
                .repoOwner("acme")
                .repoName("widgets")
                .mutationField("createIssue")
                .status(ScmApiActionStatus.FORWARDED)
                .build();
    }

    public static void check(ScmApiActionStore store) {
        Instant t = Instant.parse("2026-11-05T14:00:00.123Z");
        store.save(at("id-3", t));
        store.save(at("id-1", t));
        store.save(at("id-2", t));
        store.save(at("id-0", t.plusSeconds(1)));

        assertEquals(
                List.of("id-0", "id-3", "id-2", "id-1"),
                CursorWalk.walk(
                        after -> store.find(ScmApiActionQuery.builder()
                                .after(after)
                                .limit(3)
                                .build()),
                        r -> new RecordCursor(r.getTimestamp(), r.getId()),
                        ScmApiActionRecord::getId));
        assertEquals(
                List.of("id-1", "id-2", "id-3", "id-0"),
                CursorWalk.walk(
                        after -> store.find(ScmApiActionQuery.builder()
                                .newestFirst(false)
                                .after(after)
                                .limit(1)
                                .build()),
                        r -> new RecordCursor(r.getTimestamp(), r.getId()),
                        ScmApiActionRecord::getId));

        assertEquals(
                List.of("id-3", "id-2", "id-1"),
                store
                        .find(ScmApiActionQuery.builder()
                                .from(t)
                                .to(t.plusSeconds(1))
                                .build())
                        .stream()
                        .map(ScmApiActionRecord::getId)
                        .toList());
    }
}
