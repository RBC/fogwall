package com.rbc.fogwall.db.model;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.scmapi.ForgejoRestAllowlist;
import com.rbc.fogwall.scmapi.GitHubMutationAllowlist;
import com.rbc.fogwall.scmapi.GitLabRestAllowlist;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ActivityModelTest {

    private static final Instant FROM = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant TO = FROM.plusSeconds(3 * 86_400);

    private static ActivityQuery.ActivityQueryBuilder byPeriod(List<Instant> starts) {
        return ActivityQuery.builder()
                .from(FROM)
                .to(TO)
                .groupBy(Set.of(ActivityDimension.PERIOD))
                .periodStarts(starts);
    }

    @Test
    void query_withoutABoundedWindow_isRefused() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ActivityQuery.builder().to(TO).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> ActivityQuery.builder().from(FROM).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> ActivityQuery.builder().from(TO).to(FROM).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> ActivityQuery.builder().from(FROM).to(FROM).build());
    }

    @Test
    void query_byPeriod_needsPeriodsThatTileTheWindow() {
        assertThrows(IllegalArgumentException.class, () -> byPeriod(List.of()).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> byPeriod(List.of(FROM.plusSeconds(1))).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> byPeriod(List.of(FROM, FROM.plusSeconds(10), FROM.plusSeconds(10)))
                        .build());
        assertThrows(
                IllegalArgumentException.class,
                () -> byPeriod(List.of(FROM, TO)).build());

        List<Instant> tooMany = new ArrayList<>();
        for (int i = 0; i <= ActivityQuery.MAX_PERIODS; i++) {
            tooMany.add(FROM.plusSeconds(i));
        }
        assertThrows(IllegalArgumentException.class, () -> byPeriod(tooMany).build());
    }

    @Test
    void query_byPeriod_exposesTheBoundariesAfterTheFirstStart() {
        Instant second = FROM.plusSeconds(86_400);
        ActivityQuery query = byPeriod(List.of(FROM, second)).build();
        assertEquals(List.of(second), query.periodBoundaries());
        assertTrue(query.groups(ActivityDimension.PERIOD));
        assertFalse(query.groupsProvider());
    }

    @Test
    void query_groupedByRepo_groupsTheProviderToo() {
        ActivityQuery query = ActivityQuery.builder()
                .from(FROM)
                .to(TO)
                .groupBy(Set.of(ActivityDimension.REPO))
                .build();
        assertTrue(query.groupsProvider());
    }

    @Test
    void activity_classifiesEveryAllowlistedOperation() {
        Stream.of(
                        GitHubMutationAllowlist.operations(),
                        GitLabRestAllowlist.operations(),
                        ForgejoRestAllowlist.operations())
                .flatMap(Set::stream)
                .forEach(op -> assertNotEquals(ScmApiActivity.OTHER, ScmApiActivity.of(op), op));
    }

    @Test
    void activity_namesEachFieldOnce_andAnUnknownOneIsOther() {
        Set<String> seen = new HashSet<>();
        for (ScmApiActivity activity : ScmApiActivity.classified()) {
            for (String field : activity.mutationFields()) {
                assertTrue(seen.add(field), field + " is classified twice");
            }
        }
        assertEquals(ScmApiActivity.OTHER, ScmApiActivity.of("deleteRepository"));
        assertEquals(ScmApiActivity.OTHER, ScmApiActivity.of(null));
    }
}
