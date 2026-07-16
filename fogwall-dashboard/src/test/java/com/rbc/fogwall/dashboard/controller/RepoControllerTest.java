package com.rbc.fogwall.dashboard.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rbc.fogwall.db.FetchStore;
import com.rbc.fogwall.db.FetchStore.RepoFetchSummary;
import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.PushStore.RepoPushSummary;
import com.rbc.fogwall.db.UrlRuleRegistry;
import com.rbc.fogwall.db.model.AccessRule;
import com.rbc.fogwall.db.model.MatchTarget;
import com.rbc.fogwall.db.model.MatchType;
import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.provider.ProviderRegistry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class RepoControllerTest {

    @InjectMocks
    RepoController controller;

    @Mock
    UrlRuleRegistry urlRuleRegistry;

    @Mock
    FetchStore fetchStore;

    @Mock
    PushStore pushStore;

    @Mock
    ProviderRegistry providerSource;

    // ── GET /api/repos/rules ──────────────────────────────────────────────────────

    @Test
    void listRules_delegatesToRegistry() {
        var rule = AccessRule.builder().provider("github").build();
        when(urlRuleRegistry.findAll()).thenReturn(List.of(rule));

        var result = controller.listRules();

        assertEquals(1, result.size());
        assertEquals("github", result.get(0).getProvider());
    }

    // ── GET /api/repos/rules/{id} ─────────────────────────────────────────────────

    @Test
    void getRule_found_returns200() {
        var rule = AccessRule.builder().build();
        when(urlRuleRegistry.findById("r1")).thenReturn(Optional.of(rule));

        assertEquals(HttpStatus.OK, controller.getRule("r1").getStatusCode());
    }

    @Test
    void getRule_notFound_returns404() {
        when(urlRuleRegistry.findById("missing")).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, controller.getRule("missing").getStatusCode());
    }

    // ── POST /api/repos/rules ─────────────────────────────────────────────────────

    @Test
    void createRule_noProvider_setsSourceToDb_returns201() {
        // null provider = applies to all providers — always valid
        var rule = AccessRule.builder().source(AccessRule.Source.CONFIG).build();

        var resp = controller.createRule(rule);

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertEquals(AccessRule.Source.DB, ((AccessRule) resp.getBody()).getSource());
        verify(urlRuleRegistry).save(rule);
    }

    @Test
    void createRule_knownProvider_returns201() {
        var p = mock(FogwallProvider.class);
        when(p.getProviderId()).thenReturn("github");
        when(providerSource.getProviders()).thenReturn(List.of(p));

        var rule = AccessRule.builder().provider("github").build();
        var resp = controller.createRule(rule);

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        verify(urlRuleRegistry).save(rule);
    }

    @Test
    void createRule_unknownProvider_returns400() {
        var p = mock(FogwallProvider.class);
        when(p.getProviderId()).thenReturn("github");
        when(providerSource.getProviders()).thenReturn(List.of(p));

        var rule = AccessRule.builder().provider("nonexistent-provider").build();
        var resp = controller.createRule(rule);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    // ── PUT /api/repos/rules/{id} ─────────────────────────────────────────────────

    @Test
    void updateRule_found_setsIdAndReturns200() {
        var existing = AccessRule.builder().build();
        var update = AccessRule.builder().build(); // no provider — always valid
        when(urlRuleRegistry.findById("r1")).thenReturn(Optional.of(existing));

        var resp = controller.updateRule("r1", update);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("r1", update.getId());
        verify(urlRuleRegistry).update(update);
    }

    @Test
    void updateRule_notFound_returns404() {
        when(urlRuleRegistry.findById("missing")).thenReturn(Optional.empty());

        assertEquals(
                HttpStatus.NOT_FOUND,
                controller.updateRule("missing", AccessRule.builder().build()).getStatusCode());
    }

    // ── DELETE /api/repos/rules/{id} ──────────────────────────────────────────────

    @Test
    void deleteRule_found_returns204() {
        when(urlRuleRegistry.findById("r1"))
                .thenReturn(Optional.of(AccessRule.builder().build()));

        assertEquals(HttpStatus.NO_CONTENT, controller.deleteRule("r1").getStatusCode());
        verify(urlRuleRegistry).delete("r1");
    }

    @Test
    void deleteRule_notFound_returns404() {
        when(urlRuleRegistry.findById("missing")).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, controller.deleteRule("missing").getStatusCode());
    }

    // ── POST /api/repos/rules/test ────────────────────────────────────────────────

    @Test
    void testRules_missingProvider_returns400() {
        var resp = controller.testRules(new RepoController.RuleTestRequest("", "acme", "repo", "PUSH"));
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void testRules_missingOwnerOrName_returns400() {
        var resp = controller.testRules(new RepoController.RuleTestRequest("github", "", "repo", "PUSH"));
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void testRules_unknownProvider_returns400() {
        var p = mock(FogwallProvider.class);
        when(p.getProviderId()).thenReturn("github");
        when(providerSource.getProviders()).thenReturn(List.of(p));

        var resp = controller.testRules(new RepoController.RuleTestRequest("nonexistent", "acme", "repo", "PUSH"));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void testRules_invalidOperation_returns400() {
        var p = mock(FogwallProvider.class);
        when(p.getProviderId()).thenReturn("github");
        when(providerSource.getProviders()).thenReturn(List.of(p));

        var resp = controller.testRules(new RepoController.RuleTestRequest("github", "acme", "repo", "MERGE"));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void testRules_allowMatch_returnsAllowDecisionWithTrail() {
        var p = mock(FogwallProvider.class);
        when(p.getProviderId()).thenReturn("github");
        when(providerSource.getProviders()).thenReturn(List.of(p));
        when(providerSource.getProvider("github")).thenReturn(Optional.of(p));

        var rule = AccessRule.builder()
                .ruleOrder(100)
                .provider("github")
                .access(AccessRule.Access.ALLOW)
                .operation(AccessRule.Operation.BOTH)
                .target(MatchTarget.OWNER)
                .value("acme")
                .matchType(MatchType.GLOB)
                .build();
        when(urlRuleRegistry.findEnabledForProvider("github")).thenReturn(List.of(rule));

        var resp = controller.testRules(new RepoController.RuleTestRequest("github", "acme", "repo", "PUSH"));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        var body = (RepoController.RuleTestResponse) resp.getBody();
        assertEquals("ALLOW", body.decision());
        assertEquals(rule.getId(), body.matchedRuleId());
        assertEquals(1, body.steps().size());
        assertEquals(true, body.steps().get(0).matched());
    }

    @Test
    void testRules_noMatch_returnsNotAllowed() {
        var p = mock(FogwallProvider.class);
        when(p.getProviderId()).thenReturn("github");
        when(providerSource.getProviders()).thenReturn(List.of(p));
        when(providerSource.getProvider("github")).thenReturn(Optional.of(p));
        when(urlRuleRegistry.findEnabledForProvider("github")).thenReturn(List.of());

        var resp = controller.testRules(new RepoController.RuleTestRequest("github", "acme", "repo", "PUSH"));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        var body = (RepoController.RuleTestResponse) resp.getBody();
        assertEquals("NOT_ALLOWED", body.decision());
        assertEquals(null, body.matchedRuleId());
    }

    @Test
    void testRules_defaultsOperationToPush() {
        var p = mock(FogwallProvider.class);
        when(p.getProviderId()).thenReturn("github");
        when(providerSource.getProviders()).thenReturn(List.of(p));
        when(providerSource.getProvider("github")).thenReturn(Optional.of(p));
        when(urlRuleRegistry.findEnabledForProvider("github")).thenReturn(List.of());

        var resp = controller.testRules(new RepoController.RuleTestRequest("github", "acme", "repo", null));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    // ── GET /api/repos/active ─────────────────────────────────────────────────────

    @Nested
    class ActiveRepos {

        @Test
        void empty_returnsEmptyList() {
            when(pushStore.summarizeByRepo()).thenReturn(List.of());
            when(fetchStore.summarizeByRepo()).thenReturn(List.of());

            assertEquals(List.of(), controller.activeRepos());
        }

        @Test
        void pushSummary_appearsInResults() {
            when(pushStore.summarizeByRepo()).thenReturn(List.of(new RepoPushSummary("github", "acme", "myrepo", 2L)));
            when(fetchStore.summarizeByRepo()).thenReturn(List.of());

            var result = controller.activeRepos();

            assertEquals(1, result.size());
            assertEquals("github", result.get(0).get("provider"));
            assertEquals("acme", result.get(0).get("owner"));
            assertEquals("myrepo", result.get(0).get("repoName"));
            assertEquals(2L, result.get(0).get("pushCount"));
            assertEquals(0L, result.get(0).get("fetchCount"));
        }

        @Test
        void fetchSummaries_mergedWithPushData() {
            when(pushStore.summarizeByRepo()).thenReturn(List.of(new RepoPushSummary("github", "acme", "myrepo", 1L)));
            when(fetchStore.summarizeByRepo())
                    .thenReturn(List.of(new RepoFetchSummary("github", "acme", "myrepo", 10L, 2L)));

            var result = controller.activeRepos();

            assertEquals(1, result.size());
            assertEquals(1L, result.get(0).get("pushCount"));
            assertEquals(8L, result.get(0).get("fetchCount")); // total(10) - blocked(2)
            assertEquals(2L, result.get(0).get("blockedFetchCount"));
        }

        @Test
        void fetchOnly_repo_appearsInResults() {
            when(pushStore.summarizeByRepo()).thenReturn(List.of());
            when(fetchStore.summarizeByRepo())
                    .thenReturn(List.of(new RepoFetchSummary("gitlab", "org", "repo", 5L, 0L)));

            var result = controller.activeRepos();

            assertEquals(1, result.size());
            assertEquals("gitlab", result.get(0).get("provider"));
            assertEquals(0L, result.get(0).get("pushCount"));
            assertEquals(5L, result.get(0).get("fetchCount"));
        }

        @Test
        void sortedByTotalActivityDescending() {
            when(pushStore.summarizeByRepo())
                    .thenReturn(List.of(
                            new RepoPushSummary("github", "acme", "busy", 3L),
                            new RepoPushSummary("github", "acme", "quiet", 1L)));
            // busy also has 10 fetches = 13 total; quiet has 1 push + 0 fetches = 1
            when(fetchStore.summarizeByRepo())
                    .thenReturn(List.of(new RepoFetchSummary("github", "acme", "busy", 10L, 0L)));

            var result = controller.activeRepos();

            assertEquals(2, result.size());
            assertEquals("busy", result.get(0).get("repoName"));
            assertEquals("quiet", result.get(1).get("repoName"));
        }
    }
}
