package com.rbc.fogwall.servlet.filter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.provider.BitbucketProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.junit.jupiter.api.Test;

class BitbucketIdentityFilterTest {

    /**
     * Historical note (RBC/fogwall#425): {@code BitbucketIdentityFilter} used to be constructed without a working
     * {@code pathPrefix}, which made the (since-removed) provider-path check in {@code shouldFilter()} compare the
     * request URI against the bare {@code provider.servletPath()} instead of the full mapped path — never matching a
     * real proxied request, so the filter silently never ran. {@code shouldFilter()} no longer does any path matching
     * of its own (that scoping is Jetty's job via URL-pattern-scoped registration — see
     * {@link FilterProviderScopingTest} for a regression test against the real dispatch mechanism); this test just
     * confirms the filter applies to a push-shaped request, per the operation gating it still does.
     */
    @Test
    void shouldFilter_matchesPushRequest() {
        BitbucketProvider provider = new BitbucketProvider("bitbucket", URI.create("https://bitbucket.org"), null);
        BitbucketIdentityFilter filter = new BitbucketIdentityFilter(provider);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI())
                .thenReturn("/proxy" + provider.servletPath() + "/owner/repo.git/git-receive-pack");
        when(request.getMethod()).thenReturn("POST");
        when(request.getContentType()).thenReturn("application/x-git-receive-pack-request");

        assertTrue(filter.shouldFilter().test(request), "filter must apply to a push request");
    }
}
