package com.rbc.fogwall.db;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.model.ActivityDimension;
import com.rbc.fogwall.db.model.ActivityKey;
import com.rbc.fogwall.db.model.ActivityQuery;
import com.rbc.fogwall.db.model.ProposalOutcomeCount;
import com.rbc.fogwall.db.model.PushActivityCount;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStatus;
import com.rbc.fogwall.db.model.ScmApiActionRecord;
import com.rbc.fogwall.db.model.ScmApiActionStatus;
import com.rbc.fogwall.db.model.ScmApiActivity;
import com.rbc.fogwall.db.model.ScmApiActivityCount;
import com.rbc.fogwall.db.model.ScmApiEntityRecord;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * What every push store and SCM API action store must answer for an activity aggregate, run against each store family
 * by a subclass. Both families must put a record in the same group and the same period.
 */
public abstract class ActivityAggregateContract {

    private static final Instant DAY0 = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant DAY1 = DAY0.plusSeconds(86_400);
    private static final Instant DAY2 = DAY1.plusSeconds(86_400);
    private static final Instant DAY3 = DAY2.plusSeconds(86_400);

    protected abstract PushStore pushStore();

    protected abstract ScmApiActionStore actionStore();

    protected abstract ScmApiEntityStore entityStore();

    private static ActivityQuery.ActivityQueryBuilder window() {
        return ActivityQuery.builder().from(DAY0).to(DAY3);
    }

    private void push(String user, String repo, PushStatus status, Instant at) {
        pushStore()
                .save(PushRecord.builder()
                        .provider("github")
                        .project("acme")
                        .repoName(repo)
                        .branch("refs/heads/main")
                        .commitTo("abc")
                        .user(user)
                        .resolvedUser(user)
                        .status(status)
                        .timestamp(at)
                        .build());
    }

    private ScmApiActionRecord action(
            String user, String repo, String field, ScmApiActionStatus status, Instant at, String entityId) {
        ScmApiActionRecord record = ScmApiActionRecord.builder()
                .provider("github")
                .resolvedUser(user)
                .repoOwner("acme")
                .repoName(repo)
                .mutationField(field)
                .status(status)
                .timestamp(at)
                .entityId(entityId)
                .build();
        actionStore().save(record);
        return record;
    }

    private String entity(String repo, int number, ScmApiEntityRecord.Kind kind, ScmApiEntityRecord.State state) {
        ScmApiEntityRecord entity = ScmApiEntityRecord.builder()
                .provider("github")
                .repoOwner("acme")
                .repoName(repo)
                .kind(kind)
                .number(number)
                .state(state)
                .createdBy("alice")
                .build();
        entityStore().save(entity);
        return entity.getId();
    }

    @Test
    void pushes_groupedByUserAndStatus_countEachPushInTheWindow() {
        push("alice", "widgets", PushStatus.FORWARDED, DAY0.plusSeconds(60));
        push("alice", "widgets", PushStatus.FORWARDED, DAY1.plusSeconds(60));
        push("alice", "widgets", PushStatus.REJECTED, DAY2.plusSeconds(60));
        push("bob", "widgets", PushStatus.FORWARDED, DAY1.plusSeconds(60));
        push("alice", "widgets", PushStatus.FORWARDED, DAY3.plusSeconds(60)); // outside the window

        Map<String, Long> counts =
                pushStore()
                        .summarizeActivity(
                                window().groupBy(Set.of(ActivityDimension.USER)).build())
                        .stream()
                        .collect(Collectors.toMap(c -> c.key().user() + ":" + c.status(), PushActivityCount::count));

        assertEquals(Map.of("alice:FORWARDED", 2L, "alice:REJECTED", 1L, "bob:FORWARDED", 1L), counts);
    }

    @Test
    void pushes_filteredToOneUserAndRepo_leaveTheKeyUngrouped() {
        push("alice", "widgets", PushStatus.FORWARDED, DAY0.plusSeconds(60));
        push("alice", "gadgets", PushStatus.FORWARDED, DAY0.plusSeconds(60));
        push("bob", "widgets", PushStatus.FORWARDED, DAY0.plusSeconds(60));

        List<PushActivityCount> counts = pushStore()
                .summarizeActivity(window().user("alice").repo("widgets").build());

        assertEquals(
                List.of(new PushActivityCount(new ActivityKey(null, null, null, null, null), PushStatus.FORWARDED, 1)),
                counts);
    }

    @Test
    void pushes_groupedByPeriod_landInTheirPeriod() {
        // Whole seconds: MySQL's TIMESTAMP rounds a fractional second, which would move a push across the boundary.
        push("alice", "widgets", PushStatus.FORWARDED, DAY0);
        push("alice", "widgets", PushStatus.FORWARDED, DAY1.minusSeconds(1));
        push("alice", "widgets", PushStatus.FORWARDED, DAY1);
        push("alice", "widgets", PushStatus.FORWARDED, DAY3.minusSeconds(1));

        Map<Integer, Long> byPeriod = pushStore()
                .summarizeActivity(window().groupBy(Set.of(ActivityDimension.PERIOD))
                        .periodStarts(List.of(DAY0, DAY1, DAY2))
                        .build())
                .stream()
                .collect(Collectors.toMap(c -> c.key().period(), PushActivityCount::count));

        assertEquals(Map.of(0, 2L, 1, 1L, 2, 1L), byPeriod);
    }

    @Test
    void pushes_groupedByRepo_carryProviderOwnerAndName() {
        push("alice", "widgets", PushStatus.FORWARDED, DAY0.plusSeconds(60));

        List<PushActivityCount> counts = pushStore()
                .summarizeActivity(
                        window().groupBy(Set.of(ActivityDimension.REPO)).build());

        assertEquals(
                List.of(new PushActivityCount(
                        new ActivityKey("github", "acme", "widgets", null, null), PushStatus.FORWARDED, 1)),
                counts);
    }

    @Test
    void actions_countEachTargetOnce_whateverTheRequestCount() {
        String pr = entity("widgets", 1, ScmApiEntityRecord.Kind.PULL_REQUEST, ScmApiEntityRecord.State.OPEN);
        // One edit sent as two requests, plus a comment, on the same pull request.
        action("alice", "widgets", "updatePullRequest", ScmApiActionStatus.FORWARDED, DAY0.plusSeconds(1), pr);
        action("alice", "widgets", "addLabelsToLabelable", ScmApiActionStatus.FORWARDED, DAY0.plusSeconds(2), pr);
        action("alice", "widgets", "addComment", ScmApiActionStatus.FORWARDED, DAY0.plusSeconds(3), pr);
        action("alice", "widgets", "issues.note", ScmApiActionStatus.FORWARDED, DAY0.plusSeconds(4), null);

        Map<ScmApiActivity, ScmApiActivityCount> counts = actionStore().summarizeActivity(window().build()).stream()
                .collect(Collectors.toMap(ScmApiActivityCount::activity, c -> c));

        assertEquals(2, counts.get(ScmApiActivity.EDIT).actions());
        assertEquals(1, counts.get(ScmApiActivity.EDIT).targets());
        // A comment with no identified target counts as its own.
        assertEquals(2, counts.get(ScmApiActivity.COMMENT).actions());
        assertEquals(2, counts.get(ScmApiActivity.COMMENT).targets());
    }

    @Test
    void actions_countEachPersonsContributionToATarget_whenUngrouped() {
        String pr = entity("widgets", 1, ScmApiEntityRecord.Kind.PULL_REQUEST, ScmApiEntityRecord.State.OPEN);
        action("alice", "widgets", "updatePullRequest", ScmApiActionStatus.FORWARDED, DAY0.plusSeconds(1), pr);
        action("alice", "widgets", "updatePullRequest", ScmApiActionStatus.FORWARDED, DAY0.plusSeconds(2), pr);
        action("bob", "widgets", "updatePullRequest", ScmApiActionStatus.FORWARDED, DAY0.plusSeconds(3), pr);

        List<ScmApiActivityCount> counts = actionStore().summarizeActivity(window().build());

        assertEquals(1, counts.size());
        assertEquals(3, counts.getFirst().actions());
        assertEquals(2, counts.getFirst().targets());
    }

    @Test
    void actions_keepTheOutcome_andClassifyAnUnknownMutationAsOther() {
        action("alice", "widgets", "createIssue", ScmApiActionStatus.REJECTED, DAY0.plusSeconds(1), null);
        action("alice", "widgets", "deleteRepository", ScmApiActionStatus.ERROR, DAY0.plusSeconds(2), null);

        Set<String> counts = actionStore().summarizeActivity(window().build()).stream()
                .map(c -> c.status() + ":" + c.activity() + ":" + c.actions())
                .collect(Collectors.toSet());

        assertEquals(Set.of("REJECTED:OPEN_ISSUE:1", "ERROR:OTHER:1"), counts);
    }

    @Test
    void actions_filteredAndGroupedByRepoAndUser() {
        action("alice", "widgets", "addComment", ScmApiActionStatus.FORWARDED, DAY0.plusSeconds(1), null);
        action("bob", "widgets", "addComment", ScmApiActionStatus.FORWARDED, DAY0.plusSeconds(1), null);
        action("bob", "gadgets", "addComment", ScmApiActionStatus.FORWARDED, DAY0.plusSeconds(1), null);
        action("bob", "widgets", "addComment", ScmApiActionStatus.FORWARDED, DAY3, null); // outside the window

        Set<ActivityKey> keys = actionStore()
                .summarizeActivity(window().repo("widgets")
                        .groupBy(Set.of(ActivityDimension.REPO, ActivityDimension.USER))
                        .build())
                .stream()
                .map(ScmApiActivityCount::key)
                .collect(Collectors.toSet());

        assertEquals(
                Set.of(
                        new ActivityKey("github", "acme", "widgets", "alice", null),
                        new ActivityKey("github", "acme", "widgets", "bob", null)),
                keys);
    }

    @Test
    void outcomes_countForwardedCreatesByTheirEntitysState() {
        String merged = entity("widgets", 1, ScmApiEntityRecord.Kind.PULL_REQUEST, ScmApiEntityRecord.State.MERGED);
        String open = entity("widgets", 2, ScmApiEntityRecord.Kind.PULL_REQUEST, ScmApiEntityRecord.State.OPEN);
        String issue = entity("widgets", 3, ScmApiEntityRecord.Kind.ISSUE, ScmApiEntityRecord.State.CLOSED);
        action("alice", "widgets", "createPullRequest", ScmApiActionStatus.FORWARDED, DAY0.plusSeconds(1), merged);
        action("alice", "widgets", "createPullRequest", ScmApiActionStatus.FORWARDED, DAY0.plusSeconds(2), open);
        action("alice", "widgets", "createIssue", ScmApiActionStatus.FORWARDED, DAY0.plusSeconds(3), issue);
        // A create with no registered entity, a refused create, and an edit of something opened elsewhere.
        action("alice", "widgets", "pulls.create", ScmApiActionStatus.FORWARDED, DAY0.plusSeconds(4), null);
        action("alice", "widgets", "createPullRequest", ScmApiActionStatus.REJECTED, DAY0.plusSeconds(5), null);
        action("alice", "widgets", "updatePullRequest", ScmApiActionStatus.FORWARDED, DAY0.plusSeconds(6), merged);

        Set<String> outcomes = actionStore().summarizeOutcomes(window().build()).stream()
                .map(c -> c.opened() + ":" + c.state() + ":" + c.count())
                .collect(Collectors.toSet());

        assertEquals(
                Set.of(
                        "OPEN_PULL_REQUEST:MERGED:1",
                        "OPEN_PULL_REQUEST:OPEN:1",
                        "OPEN_PULL_REQUEST:null:1",
                        "OPEN_ISSUE:CLOSED:1"),
                outcomes);
    }

    @Test
    void outcomes_groupedByPeriodAndUser() {
        String a = entity("widgets", 1, ScmApiEntityRecord.Kind.PULL_REQUEST, ScmApiEntityRecord.State.MERGED);
        String b = entity("widgets", 2, ScmApiEntityRecord.Kind.PULL_REQUEST, ScmApiEntityRecord.State.MERGED);
        action("alice", "widgets", "createPullRequest", ScmApiActionStatus.FORWARDED, DAY0.plusSeconds(1), a);
        action("bob", "widgets", "createPullRequest", ScmApiActionStatus.FORWARDED, DAY2.plusSeconds(1), b);

        List<ProposalOutcomeCount> counts = actionStore()
                .summarizeOutcomes(window().groupBy(Set.of(ActivityDimension.USER, ActivityDimension.PERIOD))
                        .periodStarts(List.of(DAY0, DAY1, DAY2))
                        .build());

        assertEquals(
                Set.of(
                        new ProposalOutcomeCount(
                                new ActivityKey(null, null, null, "alice", 0),
                                ScmApiActivity.OPEN_PULL_REQUEST,
                                ScmApiEntityRecord.State.MERGED,
                                1),
                        new ProposalOutcomeCount(
                                new ActivityKey(null, null, null, "bob", 2),
                                ScmApiActivity.OPEN_PULL_REQUEST,
                                ScmApiEntityRecord.State.MERGED,
                                1)),
                Set.copyOf(counts));
    }

    @Test
    void emptyWindow_returnsNoRows() {
        assertTrue(pushStore().summarizeActivity(window().build()).isEmpty());
        assertTrue(actionStore().summarizeActivity(window().build()).isEmpty());
        assertTrue(actionStore().summarizeOutcomes(window().build()).isEmpty());
    }
}
