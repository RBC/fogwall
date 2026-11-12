package com.rbc.fogwall.dashboard.activity;

import com.rbc.fogwall.config.FogwallConfig;
import com.rbc.fogwall.dashboard.activity.ContributionActivity.Group;
import com.rbc.fogwall.dashboard.activity.ContributionActivity.Opened;
import com.rbc.fogwall.dashboard.activity.ContributionActivity.Period;
import com.rbc.fogwall.dashboard.activity.ContributionActivity.Proposals;
import com.rbc.fogwall.dashboard.activity.ContributionActivity.Totals;
import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.ScmApiActionStore;
import com.rbc.fogwall.db.model.ActivityDimension;
import com.rbc.fogwall.db.model.ActivityKey;
import com.rbc.fogwall.db.model.ActivityQuery;
import com.rbc.fogwall.db.model.ProposalOutcomeCount;
import com.rbc.fogwall.db.model.PushActivityCount;
import com.rbc.fogwall.db.model.ScmApiActionStatus;
import com.rbc.fogwall.db.model.ScmApiActivity;
import com.rbc.fogwall.db.model.ScmApiActivityCount;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Builds {@link ContributionActivity} reports from the push and SCM API action stores. Reads only: no store of its own,
 * nothing on the push or SCM API path. Every report is a fixed handful of grouped queries over a bounded window.
 */
@Component
@RequiredArgsConstructor
public class ContributionActivityService {

    private final PushStore pushStore;
    private final ScmApiActionStore scmApiActionStore;
    private final FogwallConfig fogwallConfig;

    /** The filters a report is narrowed by. Exact matches; empty means "don't filter on this". */
    public record Filters(Optional<String> provider, Optional<String> owner, Optional<String> repo) {

        public static final Filters NONE = new Filters(Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * Whether any provider takes pull/merge request or issue traffic through fogwall, through the SCM API proxy or the
     * dashboard's issue form. When none does, a report reads no SCM API record.
     */
    public boolean proposalsAvailable() {
        return fogwallConfig.getProviders().values().stream()
                .anyMatch(p -> p.getScmApi().isEnabled() || p.isIssuesEnabled());
    }

    /** One person's activity, by period and by repository. */
    public ContributionActivity forUser(String user, ActivityWindow window, Filters filters) {
        return report(Optional.of(user), window, filters);
    }

    /** Everyone's activity, by period, provider and repository, with distinct contributor counts. */
    public ContributionActivity aggregate(ActivityWindow window, Filters filters) {
        return report(Optional.empty(), window, filters);
    }

    /** How many repositories {@link #summary} lists. */
    public static final int SUMMARY_REPOS = 5;

    /**
     * The aggregate every signed-in user may see: unfiltered, naming no person, with only the most active repositories.
     */
    public ContributionActivity summary(ActivityWindow window) {
        ContributionActivity all = aggregate(window, Filters.NONE);
        return new ContributionActivity(
                null,
                all.window(),
                all.proposalsAvailable(),
                all.totals(),
                all.contributors(),
                all.periods(),
                all.providers(),
                all.repos().stream().limit(SUMMARY_REPOS).toList(),
                all.activeRepos());
    }

    /** One repository's pushes by period and status, for the Repos page. */
    public List<Period> repoPushes(ActivityWindow window, String provider, String owner, String repo) {
        ActivityQuery query = base(
                        window,
                        Optional.empty(),
                        new Filters(Optional.of(provider), Optional.of(owner), Optional.of(repo)))
                .groupBy(Set.of(ActivityDimension.PERIOD))
                .periodStarts(window.periodStarts())
                .build();
        return periods(window, pushStore.summarizeActivity(query), null, null);
    }

    private ContributionActivity report(Optional<String> user, ActivityWindow window, Filters filters) {
        boolean aggregate = user.isEmpty();
        boolean proposals = proposalsAvailable();
        Set<ActivityDimension> byRepo =
                aggregate ? Set.of(ActivityDimension.REPO, ActivityDimension.USER) : Set.of(ActivityDimension.REPO);
        ActivityQuery repoQuery = base(window, user, filters).groupBy(byRepo).build();
        ActivityQuery outcomeQuery = base(window, user, filters)
                .groupBy(Set.of(ActivityDimension.REPO))
                .build();
        ActivityQuery periodQuery = base(window, user, filters)
                .groupBy(Set.of(ActivityDimension.PERIOD))
                .periodStarts(window.periodStarts())
                .build();

        List<PushActivityCount> pushesByRepo = pushStore.summarizeActivity(repoQuery);
        List<ScmApiActivityCount> actionsByRepo =
                proposals ? scmApiActionStore.summarizeActivity(repoQuery) : List.of();
        List<ProposalOutcomeCount> outcomesByRepo =
                proposals ? scmApiActionStore.summarizeOutcomes(outcomeQuery) : List.of();

        List<Period> periods = periods(
                window,
                pushStore.summarizeActivity(periodQuery),
                proposals ? scmApiActionStore.summarizeActivity(periodQuery) : null,
                proposals ? scmApiActionStore.summarizeOutcomes(periodQuery) : null);

        Function<ActivityKey, GroupKey> repoOf = k -> new GroupKey(k.provider(), k.owner(), k.repo());
        Function<ActivityKey, GroupKey> providerOf = k -> new GroupKey(k.provider(), null, null);
        List<Group> repos = groups(pushesByRepo, actionsByRepo, outcomesByRepo, repoOf, proposals, aggregate);
        List<Group> providers = aggregate
                ? groups(pushesByRepo, actionsByRepo, outcomesByRepo, providerOf, proposals, true)
                : List.of();

        Totals totals =
                new Totals(pushTotals(pushesByRepo), proposals ? proposals(actionsByRepo, outcomesByRepo) : null);
        Long contributors =
                aggregate ? (long) contributors(pushesByRepo, actionsByRepo).size() : null;
        return new ContributionActivity(
                user.orElse(null), window, proposals, totals, contributors, periods, providers, repos, repos.size());
    }

    private static ActivityQuery.ActivityQueryBuilder base(
            ActivityWindow window, Optional<String> user, Filters filters) {
        return ActivityQuery.builder()
                .from(window.from())
                .to(window.to())
                .user(user.orElse(null))
                .provider(filters.provider().orElse(null))
                .owner(filters.owner().orElse(null))
                .repo(filters.repo().orElse(null));
    }

    private record GroupKey(String provider, String owner, String repo) {}

    private static List<Period> periods(
            ActivityWindow window,
            List<PushActivityCount> pushes,
            List<ScmApiActivityCount> actions,
            List<ProposalOutcomeCount> outcomes) {
        List<Period> periods = new ArrayList<>();
        for (int i = 0; i < window.periodStarts().size(); i++) {
            int index = i;
            Proposals proposals = actions == null
                    ? null
                    : proposals(
                            actions.stream()
                                    .filter(c -> c.key().period() == index)
                                    .toList(),
                            outcomes.stream()
                                    .filter(c -> c.key().period() == index)
                                    .toList());
            periods.add(new Period(
                    window.periodStarts().get(i),
                    pushTotals(pushes.stream()
                            .filter(c -> c.key().period() == index)
                            .toList()),
                    proposals));
        }
        return periods;
    }

    private static List<Group> groups(
            List<PushActivityCount> pushes,
            List<ScmApiActivityCount> actions,
            List<ProposalOutcomeCount> outcomes,
            Function<ActivityKey, GroupKey> keyOf,
            boolean proposals,
            boolean countContributors) {
        Map<GroupKey, List<PushActivityCount>> pushesBy = new HashMap<>();
        Map<GroupKey, List<ScmApiActivityCount>> actionsBy = new HashMap<>();
        Map<GroupKey, List<ProposalOutcomeCount>> outcomesBy = new HashMap<>();
        pushes.forEach(c -> pushesBy.computeIfAbsent(keyOf.apply(c.key()), k -> new ArrayList<>())
                .add(c));
        actions.forEach(c -> actionsBy
                .computeIfAbsent(keyOf.apply(c.key()), k -> new ArrayList<>())
                .add(c));
        outcomes.forEach(c -> outcomesBy
                .computeIfAbsent(keyOf.apply(c.key()), k -> new ArrayList<>())
                .add(c));

        Set<GroupKey> keys = new HashSet<>(pushesBy.keySet());
        actionsBy.forEach((k, rows) -> {
            if (rows.stream().anyMatch(ContributionActivityService::contributes)) keys.add(k);
        });
        keys.addAll(outcomesBy.keySet());

        List<Group> groups = new ArrayList<>();
        for (GroupKey key : keys) {
            List<PushActivityCount> p = pushesBy.getOrDefault(key, List.of());
            List<ScmApiActivityCount> a = actionsBy.getOrDefault(key, List.of());
            groups.add(new Group(
                    key.provider(),
                    key.owner(),
                    key.repo(),
                    pushTotals(p),
                    proposals ? proposals(a, outcomesBy.getOrDefault(key, List.of())) : null,
                    countContributors ? (long) contributors(p, a).size() : null));
        }
        groups.sort(Comparator.comparingLong(ContributionActivityService::volume)
                .reversed()
                .thenComparing(Group::provider, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(Group::owner, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(Group::repo, Comparator.nullsFirst(Comparator.naturalOrder())));
        return groups;
    }

    private static long volume(Group group) {
        long pushes =
                group.pushes().values().stream().mapToLong(Long::longValue).sum();
        Proposals p = group.proposals();
        return p == null
                ? pushes
                : pushes + p.pullRequests().opened() + p.issues().opened() + p.edited() + p.commented() + p.merged();
    }

    private static Map<String, Long> pushTotals(List<PushActivityCount> counts) {
        Map<String, Long> totals = new TreeMap<>();
        counts.forEach(c -> totals.merge(c.status().name(), c.count(), Long::sum));
        return totals;
    }

    /** A forwarded action that made a contribution, as opposed to a refusal or an unrecognised mutation. */
    private static boolean contributes(ScmApiActivityCount count) {
        return count.status() == ScmApiActionStatus.FORWARDED && count.activity() != ScmApiActivity.OTHER;
    }

    /** People with a push in any state, or a forwarded SCM API action. */
    private static Set<String> contributors(List<PushActivityCount> pushes, List<ScmApiActivityCount> actions) {
        Set<String> users = new HashSet<>();
        pushes.forEach(c -> {
            if (c.key().user() != null) users.add(c.key().user());
        });
        actions.forEach(c -> {
            if (c.key().user() != null && contributes(c)) users.add(c.key().user());
        });
        return users;
    }

    private static Proposals proposals(List<ScmApiActivityCount> actions, List<ProposalOutcomeCount> outcomes) {
        if (actions.isEmpty() && outcomes.isEmpty()) {
            return Proposals.NONE;
        }
        Map<ScmApiActivity, Long> contributed = new LinkedHashMap<>();
        long rejected = 0;
        for (ScmApiActivityCount c : actions) {
            if (contributes(c)) contributed.merge(c.activity(), c.targets(), Long::sum);
            if (c.status() == ScmApiActionStatus.REJECTED) rejected += c.actions();
        }
        return new Proposals(
                opened(actions, outcomes, ScmApiActivity.OPEN_PULL_REQUEST),
                opened(actions, outcomes, ScmApiActivity.OPEN_ISSUE),
                contributed.getOrDefault(ScmApiActivity.EDIT, 0L),
                contributed.getOrDefault(ScmApiActivity.COMMENT, 0L),
                contributed.getOrDefault(ScmApiActivity.MERGE, 0L),
                rejected);
    }

    private static Opened opened(
            List<ScmApiActivityCount> actions, List<ProposalOutcomeCount> outcomes, ScmApiActivity kind) {
        long refused = actions.stream()
                .filter(c -> c.activity() == kind && c.status() != ScmApiActionStatus.FORWARDED)
                .mapToLong(ScmApiActivityCount::actions)
                .sum();
        long open = 0, closed = 0, merged = 0, unknown = 0;
        for (ProposalOutcomeCount c : outcomes) {
            if (c.opened() != kind) continue;
            if (c.state() == null) {
                unknown += c.count();
                continue;
            }
            switch (c.state()) {
                case OPEN -> open += c.count();
                case CLOSED -> closed += c.count();
                case MERGED -> merged += c.count();
            }
        }
        return new Opened(open + closed + merged + unknown, open, closed, merged, unknown, refused);
    }
}
