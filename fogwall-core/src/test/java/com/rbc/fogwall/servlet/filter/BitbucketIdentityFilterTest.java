package com.rbc.fogwall.servlet.filter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.provider.BitbucketProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.junit.jupiter.api.Test;

class BitbucketIdentityFilterTest {

    /**
     * Regression test for RBC/fogwall#425: {@code BitbucketIdentityFilter} was constructed without a
     * {@code pathPrefix}, which made {@code AbstractProviderAwareFogwallFilter}'s inherited {@code shouldFilter()}
     * compare the request URI against the bare {@code provider.servletPath()} (e.g. {@code /bitbucket.org}) instead of
     * the full mapped path ({@code /proxy/bitbucket.org}). That never matched a real proxied request, so the filter
     * silently never ran — Bitbucket push identity resolution (needed for correct outbound credential rewriting) was
     * dead on every real push. This test exercises {@code shouldFilter()} directly against a realistic request URI,
     * since {@link #doHttpFilter} being called directly (the pattern most filter tests in this package use) would never
     * have caught this class of bug.
     */
    @Test
    void shouldFilter_matchesRealProxyRequestUri() {
        BitbucketProvider provider = new BitbucketProvider("bitbucket", URI.create("https://bitbucket.org"), null);
        BitbucketIdentityFilter filter = new BitbucketIdentityFilter(provider, "/proxy");

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI())
                .thenReturn("/proxy" + provider.servletPath() + "/owner/repo.git/git-receive-pack");
        when(request.getMethod()).thenReturn("POST");
        when(request.getContentType()).thenReturn("application/x-git-receive-pack-request");

        assertTrue(
                filter.shouldFilter().test(request),
                "filter must apply to a real proxied push request for its own provider");
    }

    @Test
    void shouldFilter_doesNotMatchDifferentProviderPath() {
        BitbucketProvider provider = new BitbucketProvider("bitbucket", URI.create("https://bitbucket.org"), null);
        BitbucketIdentityFilter filter = new BitbucketIdentityFilter(provider, "/proxy");

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/proxy/github.com/owner/repo.git/git-receive-pack");
        when(request.getMethod()).thenReturn("POST");
        when(request.getContentType()).thenReturn("application/x-git-receive-pack-request");

        assertFalse(filter.shouldFilter().test(request), "filter must not apply to a different provider's path");
    }
}
