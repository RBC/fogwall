package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.db.FetchActivityRecorder;
import com.rbc.fogwall.db.model.FetchActivity;
import com.rbc.fogwall.db.model.FetchRefusal;
import com.rbc.fogwall.git.GitRequestDetails;
import com.rbc.fogwall.git.HttpOperation;
import com.rbc.fogwall.git.ProxyMode;
import com.rbc.fogwall.provider.GitHubProvider;
import com.rbc.fogwall.servlet.FetchDecision;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FetchActivityFilterTest {

    private static final GitHubProvider GITHUB = new GitHubProvider("/proxy");

    private final FetchActivityRecorder recorder = mock(FetchActivityRecorder.class);
    private final FetchActivityFilter filter = new FetchActivityFilter(GITHUB, ProxyMode.TRANSPARENT, recorder);

    private static HttpServletRequest request(HttpOperation operation) {
        GitRequestDetails details = new GitRequestDetails();
        details.setOperation(operation);
        details.setRepoRef(GitRequestDetails.RepoRef.builder()
                .owner("acme")
                .name("widgets")
                .slug("/acme/widgets")
                .build());
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(GIT_REQUEST_ATTR, details);
        HttpServletRequest req = mock(HttpServletRequest.class);
        doAnswer(inv -> attributes.put(inv.getArgument(0), inv.getArgument(1)))
                .when(req)
                .setAttribute(anyString(), any());
        when(req.getAttribute(anyString())).thenAnswer(inv -> attributes.get(inv.<String>getArgument(0)));
        return req;
    }

    private void run(HttpServletRequest req, FilterChain chain) throws Exception {
        filter.doFilter(req, mock(HttpServletResponse.class), chain);
    }

    @Test
    void allowedPack_isCounted() throws Exception {
        HttpServletRequest req = request(HttpOperation.FETCH);

        run(req, (r, w) -> FetchDecision.allowed(r, "rule-1"));

        verify(recorder)
                .record(
                        GITHUB.getProviderId(),
                        "acme",
                        "widgets",
                        FetchActivity.Transport.HTTP,
                        ProxyMode.TRANSPARENT,
                        FetchActivity.Result.ALLOWED,
                        null,
                        "rule-1");
    }

    /** The pack request that follows an allowed discovery is the one counted, so a clone counts once. */
    @Test
    void allowedDiscovery_isNotCounted() throws Exception {
        run(request(HttpOperation.INFO), (r, w) -> FetchDecision.allowed(r, "rule-1"));

        verifyNoInteractions(recorder);
    }

    @Test
    void refusedDiscovery_isCounted() throws Exception {
        run(request(HttpOperation.INFO), (r, w) -> FetchDecision.blocked(r, FetchRefusal.DENY_RULE, "deny-1"));

        verify(recorder)
                .record(
                        GITHUB.getProviderId(),
                        "acme",
                        "widgets",
                        FetchActivity.Transport.HTTP,
                        ProxyMode.TRANSPARENT,
                        FetchActivity.Result.BLOCKED,
                        FetchRefusal.DENY_RULE,
                        "deny-1");
    }

    /** A check after the URL rules that refuses must win over their allow. */
    @Test
    void laterRefusal_isCountedInsteadOfTheAllow() throws Exception {
        run(request(HttpOperation.FETCH), (r, w) -> {
            FetchDecision.allowed(r, "rule-1");
            FetchDecision.blocked(r, FetchRefusal.CREDENTIAL_REFUSED, null);
            FetchDecision.allowed(r, "rule-1");
        });

        verify(recorder).record(any(), any(), any(), any(), any(), eq(FetchActivity.Result.BLOCKED), any(), any());
    }

    @Test
    void requestWithNoDecision_isNotCounted() throws Exception {
        run(request(HttpOperation.PUSH), (r, w) -> {});

        verifyNoInteractions(recorder);
    }

    @Test
    void decisionIsCounted_evenWhenTheChainThrows() {
        HttpServletRequest req = request(HttpOperation.FETCH);

        assertThrows(
                ServletException.class,
                () -> run(req, (r, w) -> {
                    FetchDecision.blocked(r, FetchRefusal.FETCH_DISABLED, null);
                    throw new ServletException("boom");
                }));

        verify(recorder).record(any(), any(), any(), any(), any(), eq(FetchActivity.Result.BLOCKED), any(), any());
    }
}
