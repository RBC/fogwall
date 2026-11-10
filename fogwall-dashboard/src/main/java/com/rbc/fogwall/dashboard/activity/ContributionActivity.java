package com.rbc.fogwall.dashboard.activity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Contribution activity through fogwall over a window: pushes, and pull/merge requests and issues opened, edited,
 * commented on and merged through the SCM API. Counts what passed through fogwall, not everything that happened on the
 * provider.
 *
 * @param user the person the report is about; null on the aggregate
 * @param proposalsAvailable whether any provider takes pull/merge request or issue traffic through fogwall. When false,
 *     {@code proposals} is null everywhere and no SCM API record was read.
 * @param contributors distinct people with a push or a forwarded SCM API action; null on one person's report
 * @param providers per-provider totals; empty on one person's report
 * @param repos per-repository rows, most active first; on the summary, only the most active
 * @param activeRepos how many repositories had activity, whether or not {@code repos} lists them all
 */
public record ContributionActivity(
        String user,
        ActivityWindow window,
        boolean proposalsAvailable,
        Totals totals,
        Long contributors,
        List<Period> periods,
        List<Group> providers,
        List<Group> repos,
        int activeRepos) {

    /** Pushes by status, and the proposal totals when {@code proposalsAvailable}. */
    public record Totals(Map<String, Long> pushes, Proposals proposals) {}

    /**
     * What happened to the pull/merge requests and issues, each counted once however many requests touched it.
     *
     * @param merged distinct pull/merge requests merged through fogwall, whoever opened them
     * @param denied SCM API requests refused by policy: not allowlisted, or no grant
     * @param rejected SCM API requests refused for their content
     */
    public record Proposals(
            Opened pullRequests, Opened issues, long edited, long commented, long merged, long denied, long rejected) {

        static final Proposals NONE = new Proposals(Opened.NONE, Opened.NONE, 0, 0, 0, 0, 0);
    }

    /**
     * How many were opened through fogwall, by the state fogwall last saw, and how many attempts to open one fogwall
     * refused. {@code unknown} were opened but the provider's response named nothing fogwall could follow;
     * {@code refused} never reached the provider: denied by policy, rejected for content, or failed.
     */
    public record Opened(long opened, long open, long closed, long merged, long unknown, long refused) {

        static final Opened NONE = new Opened(0, 0, 0, 0, 0, 0);
    }

    /** One period's activity, keyed by its start. */
    public record Period(Instant start, Map<String, Long> pushes, Proposals proposals) {}

    /**
     * One provider's or repository's activity. {@code owner} and {@code repo} are null on a provider row;
     * {@code contributors} is null on one person's report.
     */
    public record Group(
            String provider,
            String owner,
            String repo,
            Map<String, Long> pushes,
            Proposals proposals,
            Long contributors) {}
}
