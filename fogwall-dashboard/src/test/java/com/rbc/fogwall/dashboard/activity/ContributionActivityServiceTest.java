package com.rbc.fogwall.dashboard.activity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.config.FogwallConfig;
import com.rbc.fogwall.config.ProviderConfig;
import com.rbc.fogwall.dashboard.activity.ContributionActivity.Group;
import com.rbc.fogwall.dashboard.activity.ContributionActivity.Proposals;
import com.rbc.fogwall.dashboard.activity.ContributionActivityService.Filters;
import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.ScmApiActionStore;
import com.rbc.fogwall.db.model.ActivityDimension;
import com.rbc.fogwall.db.model.ActivityKey;
import com.rbc.fogwall.db.model.ActivityQuery;
import com.rbc.fogwall.db.model.ProposalOutcomeCount;
import com.rbc.fogwall.db.model.PushActivityCount;
import com.rbc.fogwall.db.model.PushStatus;
import com.rbc.fogwall.db.model.ScmApiActionStatus;
import com.rbc.fogwall.db.model.ScmApiActivity;
import com.rbc.fogwall.db.model.ScmApiActivityCount;
import com.rbc.fogwall.db.model.ScmApiEntityRecord.State;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContributionActivityServiceTest {

    private static final ActivityWindow WINDOW = ActivityWindow.of(
            Optional.of(Instant.parse("2026-11-01T00:00:00Z")),
            Optional.of(Instant.parse("2026-11-03T00:00:00Z")),
            Instant.parse("2026-11-10T00:00:00Z"));

    private PushStore pushStore;
    private ScmApiActionStore actionStore;
    private FogwallConfig config;
    private ContributionActivityService service;

    @BeforeEach
    void setUp() {
        pushStore = mock(PushStore.class);
        actionStore = mock(ScmApiActionStore.class);
        config = new FogwallConfig();
        service = new ContributionActivityService(pushStore, actionStore, config);
        when(pushStore.summarizeActivity(any())).thenReturn(List.of());
        when(actionStore.summarizeActivity(any())).thenReturn(List.of());
        when(actionStore.summarizeOutcomes(any())).thenReturn(List.of());
    }

    private void enableScmApi() {
        ProviderConfig github = new ProviderConfig();
        github.getScmApi().setEnabled(true);
        config.getProviders().put("github", github);
    }

    private static ActivityKey repo(String repo, String user) {
        return new ActivityKey("github", "acme", repo, user, null);
    }

    private static ActivityKey period(int index) {
        return new ActivityKey(null, null, null, null, index);
    }

    @Test
    void withProposalsDisabledEverywhere_readsNoScmApiRecord() {
        ProviderConfig github = new ProviderConfig();
        config.getProviders().put("github", github);

        ContributionActivity report = service.aggregate(WINDOW, Filters.NONE);

        assertFalse(report.proposalsAvailable());
        assertNull(report.totals().proposals());
        report.periods().forEach(p -> assertNull(p.proposals()));
        verifyNoInteractions(actionStore);
    }

    @Test
    void issueFormAlone_makesProposalsAvailable() {
        ProviderConfig github = new ProviderConfig();
        github.setIssuesEnabled(true);
        config.getProviders().put("github", github);

        assertTrue(service.proposalsAvailable());
    }

    @Test
    void forUser_filtersEveryQueryToThatUser_andGroupsReposWithoutUsers() {
        enableScmApi();

        ContributionActivity report = service.forUser("alice", WINDOW, Filters.NONE);

        assertEquals("alice", report.user());
        assertNull(report.contributors());
        assertTrue(report.providers().isEmpty());
        verify(pushStore, atLeastOnce()).summarizeActivity(any());
        verify(pushStore, never()).summarizeActivity(argThat(q -> !"alice".equals(q.user())));
        verify(actionStore, never()).summarizeActivity(argThat(q -> !"alice".equals(q.user())));
        verify(actionStore, never()).summarizeOutcomes(argThat(q -> !"alice".equals(q.user())));
        verify(pushStore, never()).summarizeActivity(argThat(q -> q.groups(ActivityDimension.USER)));
    }

    @Test
    void aggregate_foldsReposIntoProvidersAndCountsDistinctContributors() {
        enableScmApi();
        when(pushStore.summarizeActivity(argThat(q -> q.groups(ActivityDimension.REPO))))
                .thenReturn(List.of(
                        new PushActivityCount(repo("widgets", "alice"), PushStatus.FORWARDED, 2),
                        new PushActivityCount(repo("widgets", "bob"), PushStatus.REJECTED, 1),
                        new PushActivityCount(repo("gadgets", "alice"), PushStatus.FORWARDED, 1)));
        when(actionStore.summarizeActivity(argThat(q -> q.groups(ActivityDimension.REPO))))
                .thenReturn(List.of(
                        // carol only commented, dave was only refused: carol contributes, dave does not.
                        new ScmApiActivityCount(
                                repo("widgets", "carol"), ScmApiActionStatus.FORWARDED, ScmApiActivity.COMMENT, 3, 2),
                        new ScmApiActivityCount(
                                repo("widgets", "dave"), ScmApiActionStatus.REJECTED, ScmApiActivity.OTHER, 4, 4),
                        new ScmApiActivityCount(
                                repo("widgets", "alice"),
                                ScmApiActionStatus.REJECTED,
                                ScmApiActivity.OPEN_ISSUE,
                                1,
                                1)));
        when(actionStore.summarizeOutcomes(argThat(q -> q.groups(ActivityDimension.REPO))))
                .thenReturn(List.of(
                        new ProposalOutcomeCount(
                                new ActivityKey("github", "acme", "widgets", null, null),
                                ScmApiActivity.OPEN_PULL_REQUEST,
                                State.MERGED,
                                2),
                        new ProposalOutcomeCount(
                                new ActivityKey("github", "acme", "widgets", null, null),
                                ScmApiActivity.OPEN_PULL_REQUEST,
                                null,
                                1)));

        ContributionActivity report = service.aggregate(WINDOW, Filters.NONE);

        assertEquals(3L, report.contributors());
        assertEquals(Map.of("FORWARDED", 3L, "REJECTED", 1L), report.totals().pushes());
        Proposals proposals = report.totals().proposals();
        assertEquals(3, proposals.pullRequests().opened());
        assertEquals(2, proposals.pullRequests().merged());
        assertEquals(1, proposals.pullRequests().unknown());
        assertEquals(2, proposals.commented());
        // dave's four refused by policy and alice's one refused for content are both rejections.
        assertEquals(5, proposals.rejected());
        // alice's issue create was rejected for its content: an attempt, not an opened issue.
        assertEquals(1, proposals.issues().refused());
        assertEquals(0, proposals.issues().opened());
        assertEquals(0, proposals.pullRequests().refused());

        Group widgets = report.repos().getFirst();
        assertEquals("widgets", widgets.repo());
        assertEquals(3L, widgets.contributors());
        assertEquals(2, report.repos().size());

        assertEquals(1, report.providers().size());
        assertEquals("github", report.providers().getFirst().provider());
        assertNull(report.providers().getFirst().repo());
        assertEquals(3L, report.providers().getFirst().contributors());
    }

    @Test
    void periods_coverEveryPeriodOfTheWindow_emptyOnesIncluded() {
        enableScmApi();
        when(pushStore.summarizeActivity(argThat(q -> q.groups(ActivityDimension.PERIOD))))
                .thenReturn(List.of(new PushActivityCount(period(1), PushStatus.FORWARDED, 5)));

        ContributionActivity report = service.forUser("alice", WINDOW, Filters.NONE);

        assertEquals(WINDOW.periodStarts().size(), report.periods().size());
        assertEquals(Map.of(), report.periods().get(0).pushes());
        assertEquals(Map.of("FORWARDED", 5L), report.periods().get(1).pushes());
        assertEquals(Proposals.NONE, report.periods().get(0).proposals());
    }

    @Test
    void summary_isUnfiltered_andListsOnlyTheMostActiveRepositories() {
        List<PushActivityCount> rows = new ArrayList<>();
        for (int i = 1; i <= ContributionActivityService.SUMMARY_REPOS + 2; i++) {
            rows.add(new PushActivityCount(repo("repo" + i, "alice"), PushStatus.FORWARDED, i));
        }
        when(pushStore.summarizeActivity(argThat(q -> q.groups(ActivityDimension.REPO))))
                .thenReturn(rows);

        ContributionActivity report = service.summary(WINDOW);

        assertNull(report.user());
        assertEquals(ContributionActivityService.SUMMARY_REPOS, report.repos().size());
        assertEquals("repo7", report.repos().getFirst().repo());
        verify(pushStore, never())
                .summarizeActivity(argThat(q -> q.user() != null || q.provider() != null || q.repo() != null));
    }

    @Test
    void repoPushes_queryOneRepositoryByPeriod() {
        service.repoPushes(WINDOW, "github", "acme", "widgets");

        verify(pushStore)
                .summarizeActivity(argThat((ActivityQuery q) -> "github".equals(q.provider())
                        && "acme".equals(q.owner())
                        && "widgets".equals(q.repo())
                        && q.groups(ActivityDimension.PERIOD)
                        && q.user() == null));
        verifyNoInteractions(actionStore);
    }
}
