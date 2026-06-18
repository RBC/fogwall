package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.db.FetchStore;
import com.rbc.fogwall.db.memory.InMemoryUrlRuleRegistry;
import com.rbc.fogwall.db.model.AccessRule;
import com.rbc.fogwall.db.model.FetchRecord;
import com.rbc.fogwall.db.model.MatchTarget;
import com.rbc.fogwall.db.model.MatchType;
import com.rbc.fogwall.git.GitRequestDetails;
import com.rbc.fogwall.git.HttpOperation;
import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.provider.GenericProxyProvider;
import com.rbc.fogwall.provider.GitHubProvider;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UrlRuleFilterTest {

    private static final FogwallProvider GITHUB = new GitHubProvider("/proxy");

    private static class FakeResponse {
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        final AtomicBoolean committed = new AtomicBoolean(false);
        final HttpServletResponse mock;

        FakeResponse() throws IOException {
            mock = mock(HttpServletResponse.class);
            when(mock.getOutputStream()).thenReturn(new ServletOutputStream() {
                @Override
                public void write(int b) {
                    body.write(b);
                    committed.set(true);
                }

                @Override
                public void write(byte[] b, int off, int len) {
                    body.write(b, off, len);
                    committed.set(true);
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener l) {}
            });
            when(mock.isCommitted()).thenAnswer(inv -> committed.get());
        }
    }

    private static ServletInputStream emptyServletInputStream() {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
        return new ServletInputStream() {
            @Override
            public int read() throws IOException {
                return bais.read();
            }

            @Override
            public boolean isFinished() {
                return bais.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener l) {}
        };
    }

    private HttpServletRequest mockPushRequest(GitRequestDetails details) throws IOException {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(GIT_REQUEST_ATTR, details);
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("POST");
        when(req.getContentType()).thenReturn("application/x-git-receive-pack-request");
        when(req.getRequestURI()).thenReturn("/proxy/github.com/owner/repo.git/git-receive-pack");
        when(req.getAttribute(GIT_REQUEST_ATTR)).thenReturn(details);
        when(req.getInputStream()).thenReturn(emptyServletInputStream());
        doAnswer(inv -> {
                    attrs.put(inv.getArgument(0), inv.getArgument(1));
                    return null;
                })
                .when(req)
                .setAttribute(anyString(), any());
        when(req.getAttribute(anyString())).thenAnswer(inv -> attrs.get(inv.getArgument(0)));
        return req;
    }

    private HttpServletRequest mockInfoRefsRequest(GitRequestDetails details, String service) throws IOException {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(GIT_REQUEST_ATTR, details);
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("GET");
        when(req.getContentType()).thenReturn(null);
        when(req.getRequestURI()).thenReturn("/proxy/github.com/owner/repo.git/info/refs");
        when(req.getQueryString()).thenReturn("service=" + service);
        when(req.getParameter("service")).thenReturn(service);
        when(req.getAttribute(GIT_REQUEST_ATTR)).thenReturn(details);
        when(req.getInputStream()).thenReturn(emptyServletInputStream());
        doAnswer(inv -> {
                    attrs.put(inv.getArgument(0), inv.getArgument(1));
                    return null;
                })
                .when(req)
                .setAttribute(anyString(), any());
        when(req.getAttribute(anyString())).thenAnswer(inv -> attrs.get(inv.getArgument(0)));
        return req;
    }

    private GitRequestDetails makeDetails(String owner, String name, String slug) {
        GitRequestDetails details = new GitRequestDetails();
        details.setOperation(HttpOperation.PUSH);
        details.setRepoRef(GitRequestDetails.RepoRef.builder()
                .owner(owner)
                .name(name)
                .slug(slug)
                .build());
        return details;
    }

    private GitRequestDetails makeInfoDetails(String owner, String name, String slug) {
        GitRequestDetails details = new GitRequestDetails();
        details.setOperation(HttpOperation.INFO);
        details.setResult(GitRequestDetails.GitResult.ALLOWED);
        details.setRepoRef(GitRequestDetails.RepoRef.builder()
                .owner(owner)
                .name(name)
                .slug(slug)
                .build());
        return details;
    }

    private UrlRuleAggregateFilter aggregateWith(AccessRule... rules) {
        var registry = new InMemoryUrlRuleRegistry();
        for (AccessRule r : rules) registry.save(r);
        return new UrlRuleAggregateFilter(50, GITHUB, registry);
    }

    // --- UrlRuleAggregateFilter ---

    @Test
    void aggregate_orderBelowMinimum_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new UrlRuleAggregateFilter(49, GITHUB, new InMemoryUrlRuleRegistry()));
    }

    @Test
    void aggregate_ruleMatches_passes() throws Exception {
        var aggregate = aggregateWith(AccessRule.builder()
                .ruleOrder(100)
                .access(AccessRule.Access.ALLOW)
                .operations(AccessRule.Operations.BOTH)
                .target(MatchTarget.OWNER)
                .value("owner")
                .matchType(MatchType.GLOB)
                .build());
        GitRequestDetails details = makeDetails("owner", "repo", "/owner/repo");
        FakeResponse resp = new FakeResponse();

        aggregate.doHttpFilter(mockPushRequest(details), resp.mock);

        assertFalse(resp.committed.get(), "Request matching an allow rule should pass");
    }

    @Test
    void aggregate_noRuleMatch_blocks() throws Exception {
        var aggregate = aggregateWith(AccessRule.builder()
                .ruleOrder(100)
                .access(AccessRule.Access.ALLOW)
                .operations(AccessRule.Operations.BOTH)
                .target(MatchTarget.OWNER)
                .value("allowed")
                .matchType(MatchType.GLOB)
                .build());
        GitRequestDetails details = makeDetails("not-allowed", "repo", "/not-allowed/repo");
        FakeResponse resp = new FakeResponse();

        aggregate.doHttpFilter(mockPushRequest(details), resp.mock);

        assertTrue(resp.committed.get(), "Request not matching any allow rule should be blocked");
    }

    @Test
    void aggregate_emptyRules_blocks() throws Exception {
        var aggregate = new UrlRuleAggregateFilter(50, GITHUB, new InMemoryUrlRuleRegistry());
        GitRequestDetails details = makeDetails("owner", "repo", "/owner/repo");
        FakeResponse resp = new FakeResponse();

        aggregate.doHttpFilter(mockPushRequest(details), resp.mock);

        assertTrue(resp.committed.get(), "No rules configured — fail-closed should block");
    }

    @Test
    void aggregate_denyAtLowerOrder_blocksEvenWithAllowRule() throws Exception {
        var deny = AccessRule.builder()
                .ruleOrder(100)
                .access(AccessRule.Access.DENY)
                .operations(AccessRule.Operations.BOTH)
                .target(MatchTarget.OWNER)
                .value("blocked-owner")
                .matchType(MatchType.GLOB)
                .build();
        var allow = AccessRule.builder()
                .ruleOrder(200)
                .access(AccessRule.Access.ALLOW)
                .operations(AccessRule.Operations.BOTH)
                .target(MatchTarget.OWNER)
                .value("blocked-owner")
                .matchType(MatchType.GLOB)
                .build();
        var aggregate = aggregateWith(deny, allow);
        GitRequestDetails details = makeDetails("blocked-owner", "repo", "/blocked-owner/repo");
        FakeResponse resp = new FakeResponse();

        aggregate.doHttpFilter(mockPushRequest(details), resp.mock);

        assertTrue(resp.committed.get(), "Lower-order deny rule wins over higher-order allow rule");
    }

    @Test
    void aggregate_allowAtLowerOrder_passesEvenWithDenyRule() throws Exception {
        var allow = AccessRule.builder()
                .ruleOrder(100)
                .access(AccessRule.Access.ALLOW)
                .operations(AccessRule.Operations.BOTH)
                .target(MatchTarget.OWNER)
                .value("allowed-owner")
                .matchType(MatchType.GLOB)
                .build();
        var deny = AccessRule.builder()
                .ruleOrder(200)
                .access(AccessRule.Access.DENY)
                .operations(AccessRule.Operations.BOTH)
                .target(MatchTarget.OWNER)
                .value("allowed-owner")
                .matchType(MatchType.GLOB)
                .build();
        var aggregate = aggregateWith(allow, deny);
        GitRequestDetails details = makeDetails("allowed-owner", "repo", "/allowed-owner/repo");
        FakeResponse resp = new FakeResponse();

        aggregate.doHttpFilter(mockPushRequest(details), resp.mock);

        assertFalse(resp.committed.get(), "Lower-order allow rule wins over higher-order deny rule");
    }

    @Test
    void aggregate_denyOnlyRules_nonMatchedBlocks() throws Exception {
        var deny = AccessRule.builder()
                .ruleOrder(100)
                .access(AccessRule.Access.DENY)
                .operations(AccessRule.Operations.BOTH)
                .target(MatchTarget.OWNER)
                .value("blocked-owner")
                .matchType(MatchType.GLOB)
                .build();
        var aggregate = aggregateWith(deny);
        GitRequestDetails details = makeDetails("other-owner", "repo", "/other-owner/repo");
        FakeResponse resp = new FakeResponse();

        aggregate.doHttpFilter(mockPushRequest(details), resp.mock);

        assertTrue(resp.committed.get(), "No allow rules — fail-closed blocks unmatched requests");
    }

    // --- /info/refs blocking ---

    @Test
    void infoRefs_noAllowRule_returns403() throws Exception {
        var aggregate = new UrlRuleAggregateFilter(50, GITHUB, new InMemoryUrlRuleRegistry());
        GitRequestDetails details = makeInfoDetails("owner", "repo", "/owner/repo");
        FakeResponse resp = new FakeResponse();

        aggregate.doHttpFilter(mockInfoRefsRequest(details, "git-upload-pack"), resp.mock);

        verify(resp.mock).sendError(eq(403), anyString());
        assertEquals(GitRequestDetails.GitResult.REJECTED, details.getResult());
    }

    @Test
    void infoRefs_allowedByRule_passes() throws Exception {
        var aggregate = aggregateWith(AccessRule.builder()
                .ruleOrder(100)
                .access(AccessRule.Access.ALLOW)
                .operations(AccessRule.Operations.BOTH)
                .target(MatchTarget.SLUG)
                .value("/owner/repo")
                .matchType(MatchType.LITERAL)
                .build());
        GitRequestDetails details = makeInfoDetails("owner", "repo", "/owner/repo");
        FakeResponse resp = new FakeResponse();

        aggregate.doHttpFilter(mockInfoRefsRequest(details, "git-upload-pack"), resp.mock);

        verify(resp.mock, never()).sendError(anyInt());
        assertEquals(GitRequestDetails.GitResult.ALLOWED, details.getResult());
    }

    @Test
    void infoRefs_pushOnlyDenyRule_doesNotBlockFetchInfoRefs() throws Exception {
        var pushDeny = AccessRule.builder()
                .ruleOrder(100)
                .access(AccessRule.Access.DENY)
                .operations(AccessRule.Operations.PUSH)
                .target(MatchTarget.SLUG)
                .value("/owner/repo")
                .matchType(MatchType.LITERAL)
                .build();
        var fetchAllow = AccessRule.builder()
                .ruleOrder(200)
                .access(AccessRule.Access.ALLOW)
                .operations(AccessRule.Operations.BOTH)
                .target(MatchTarget.SLUG)
                .value("/owner/repo")
                .matchType(MatchType.LITERAL)
                .build();
        var aggregate = aggregateWith(pushDeny, fetchAllow);
        GitRequestDetails details = makeInfoDetails("owner", "repo", "/owner/repo");
        FakeResponse resp = new FakeResponse();

        aggregate.doHttpFilter(mockInfoRefsRequest(details, "git-upload-pack"), resp.mock);

        verify(resp.mock, never()).sendError(anyInt());
    }

    @Test
    void infoRefs_receivePack_deniedByRule_returns403() throws Exception {
        var deny = AccessRule.builder()
                .ruleOrder(100)
                .access(AccessRule.Access.DENY)
                .operations(AccessRule.Operations.BOTH)
                .target(MatchTarget.SLUG)
                .value("/owner/repo")
                .matchType(MatchType.LITERAL)
                .build();
        var allow = AccessRule.builder()
                .ruleOrder(200)
                .access(AccessRule.Access.ALLOW)
                .operations(AccessRule.Operations.BOTH)
                .target(MatchTarget.SLUG)
                .value("/owner/repo")
                .matchType(MatchType.LITERAL)
                .build();
        var aggregate = aggregateWith(deny, allow);
        GitRequestDetails details = makeInfoDetails("owner", "repo", "/owner/repo");
        FakeResponse resp = new FakeResponse();

        aggregate.doHttpFilter(mockInfoRefsRequest(details, "git-receive-pack"), resp.mock);

        verify(resp.mock).sendError(eq(403), anyString());
    }

    // --- Gap 2: recordFetch on blocked /info/refs ---

    @Test
    void infoRefs_fetchBlocked_notAllowed_recordsFetch() throws Exception {
        FetchStore fetchStore = mock(FetchStore.class);
        var registry = new InMemoryUrlRuleRegistry();
        var aggregate = new UrlRuleAggregateFilter(50, GITHUB, "/proxy", fetchStore, registry);
        GitRequestDetails details = makeInfoDetails("owner", "repo", "/owner/repo");
        FakeResponse resp = new FakeResponse();

        aggregate.doHttpFilter(mockInfoRefsRequest(details, "git-upload-pack"), resp.mock);

        verify(resp.mock).sendError(eq(403), anyString());
        ArgumentCaptor<FetchRecord> captor = ArgumentCaptor.forClass(FetchRecord.class);
        verify(fetchStore).record(captor.capture());
        assertEquals(FetchRecord.Result.BLOCKED, captor.getValue().getResult());
    }

    @Test
    void infoRefs_fetchBlocked_denyRule_recordsFetch() throws Exception {
        FetchStore fetchStore = mock(FetchStore.class);
        var registry = new InMemoryUrlRuleRegistry();
        registry.save(AccessRule.builder()
                .ruleOrder(100)
                .access(AccessRule.Access.DENY)
                .operations(AccessRule.Operations.BOTH)
                .target(MatchTarget.SLUG)
                .value("/owner/repo")
                .matchType(MatchType.LITERAL)
                .build());
        var aggregate = new UrlRuleAggregateFilter(50, GITHUB, "/proxy", fetchStore, registry);
        GitRequestDetails details = makeInfoDetails("owner", "repo", "/owner/repo");
        FakeResponse resp = new FakeResponse();

        aggregate.doHttpFilter(mockInfoRefsRequest(details, "git-upload-pack"), resp.mock);

        verify(resp.mock).sendError(eq(403), anyString());
        ArgumentCaptor<FetchRecord> captor = ArgumentCaptor.forClass(FetchRecord.class);
        verify(fetchStore).record(captor.capture());
        assertEquals(FetchRecord.Result.BLOCKED, captor.getValue().getResult());
    }

    @Test
    void infoRefs_pushBlocked_doesNotRecordFetch() throws Exception {
        FetchStore fetchStore = mock(FetchStore.class);
        var registry = new InMemoryUrlRuleRegistry();
        var aggregate = new UrlRuleAggregateFilter(50, GITHUB, "/proxy", fetchStore, registry);
        GitRequestDetails details = makeInfoDetails("owner", "repo", "/owner/repo");
        FakeResponse resp = new FakeResponse();

        aggregate.doHttpFilter(mockInfoRefsRequest(details, "git-receive-pack"), resp.mock);

        verify(resp.mock).sendError(eq(403), anyString());
        verify(fetchStore, never()).record(any());
    }

    @Test
    void infoRefs_fetchAllowed_doesNotRecordFetch() throws Exception {
        FetchStore fetchStore = mock(FetchStore.class);
        var registry = new InMemoryUrlRuleRegistry();
        registry.save(AccessRule.builder()
                .ruleOrder(100)
                .access(AccessRule.Access.ALLOW)
                .operations(AccessRule.Operations.BOTH)
                .target(MatchTarget.SLUG)
                .value("/owner/repo")
                .matchType(MatchType.LITERAL)
                .build());
        var aggregate = new UrlRuleAggregateFilter(50, GITHUB, "/proxy", fetchStore, registry);
        GitRequestDetails details = makeInfoDetails("owner", "repo", "/owner/repo");
        FakeResponse resp = new FakeResponse();

        aggregate.doHttpFilter(mockInfoRefsRequest(details, "git-upload-pack"), resp.mock);

        verify(resp.mock, never()).sendError(anyInt());
        verify(fetchStore, never()).record(any());
    }

    @Test
    void infoRefs_customBlockedStatus_returns404() throws Exception {
        var provider = GenericProxyProvider.builder()
                .name("custom")
                .type("github")
                .uri(java.net.URI.create("https://github.com"))
                .basePath("/proxy")
                .blockedInfoRefsStatus(404)
                .build();
        var aggregate = new UrlRuleAggregateFilter(50, provider, new InMemoryUrlRuleRegistry());
        GitRequestDetails details = makeInfoDetails("owner", "repo", "/owner/repo");
        FakeResponse resp = new FakeResponse();

        aggregate.doHttpFilter(mockInfoRefsRequest(details, "git-upload-pack"), resp.mock);

        verify(resp.mock).sendError(eq(404), anyString());
    }
}
