package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;
import static com.rbc.fogwall.servlet.FogwallServlet.PRE_APPROVED_ATTR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.git.GitRequestDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * A prior approval is a judgement about content, so it may stand in for content validation. It says nothing about
 * whether the person re-pushing that content is allowed to write here, so authorization filters must still run.
 */
class PreApprovedShortCircuitTest {

    private static class RecordingFilter extends AbstractFogwallFilter {
        final AtomicBoolean ran = new AtomicBoolean(false);
        private final boolean skipWhenPreApproved;

        RecordingFilter(boolean skipWhenPreApproved) {
            super(100);
            this.skipWhenPreApproved = skipWhenPreApproved;
        }

        @Override
        public boolean skipWhenPreApproved() {
            return skipWhenPreApproved;
        }

        @Override
        public void doHttpFilter(HttpServletRequest request, HttpServletResponse response) {
            ran.set(true);
        }
    }

    private static HttpServletRequest preApprovedPushRequest() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getContentType()).thenReturn("application/x-git-receive-pack-request");
        when(req.getRequestURI()).thenReturn("/proxy/github.com/owner/repo.git/git-receive-pack");
        when(req.getAttribute(PRE_APPROVED_ATTR)).thenReturn(Boolean.TRUE);
        when(req.getAttribute(GIT_REQUEST_ATTR)).thenReturn(new GitRequestDetails());
        when(req.getHeaderNames()).thenReturn(java.util.Collections.emptyEnumeration());
        return req;
    }

    @Test
    void contentFilterIsSkippedWhenPreApproved() throws Exception {
        RecordingFilter filter = new RecordingFilter(true);

        filter.doFilter(preApprovedPushRequest(), mock(HttpServletResponse.class), mock(FilterChain.class));

        assertFalse(filter.ran.get(), "A content filter's verdict is covered by the approval");
    }

    @Test
    void authorizationFilterStillRunsWhenPreApproved() throws Exception {
        RecordingFilter filter = new RecordingFilter(false);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(preApprovedPushRequest(), mock(HttpServletResponse.class), chain);

        assertTrue(filter.ran.get(), "Authorization is not something an approval can grant");
        verify(chain).doFilter(any(), any());
    }

    @Test
    void authorizationFiltersOptOutOfTheShortCircuit() {
        assertFalse(
                new CheckUserPushPermissionFilter(null, null).skipWhenPreApproved(),
                "CheckUserPushPermissionFilter must re-check the pusher on a pre-approved re-push");
        assertFalse(
                new UrlRuleAggregateFilter(100, null, null).skipWhenPreApproved(),
                "UrlRuleAggregateFilter must re-check the repository on a pre-approved re-push");
    }
}
