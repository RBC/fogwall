package com.rbc.fogwall.servlet.filter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jetty.ee11.servlet.FilterHolder;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for RBC/fogwall#425: confirms that a provider-scoped filter only runs for requests under its own
 * provider's URL pattern, exercised through a real Jetty {@link ServletContextHandler}/{@link FilterHolder} dispatch —
 * not by calling {@code doHttpFilter()} or {@code shouldFilter()} directly, which is the pattern that let the original
 * {@code BitbucketIdentityFilter} bug (constructed without a working path prefix, silently never ran) go undetected.
 *
 * <p>Since {@code shouldFilter()} no longer does its own provider-path check (removed as redundant — see #425), this
 * scoping is provided entirely by Jetty's own URL-pattern-scoped {@code context.addFilter(holder, urlPattern, ...)}
 * registration, mirroring exactly how {@code FogwallServletRegistrar} registers one filter instance per provider. This
 * test proves that mechanism actually works end-to-end.
 */
class FilterProviderScopingTest {

    private Server server;
    private int port;
    private final AtomicBoolean bitbucketFilterRan = new AtomicBoolean(false);
    private final AtomicBoolean githubFilterRan = new AtomicBoolean(false);

    @BeforeEach
    void startServer() throws Exception {
        server = new Server();
        var connector = new ServerConnector(server);
        connector.setPort(0);
        server.addConnector(connector);

        var context = new ServletContextHandler("/", false, false);
        context.addFilter(
                new FilterHolder(new MarkerFilter(bitbucketFilterRan)),
                "/proxy/bitbucket.org/*",
                EnumSet.of(DispatcherType.REQUEST));
        context.addFilter(
                new FilterHolder(new MarkerFilter(githubFilterRan)),
                "/proxy/github.com/*",
                EnumSet.of(DispatcherType.REQUEST));
        context.addServlet(new ServletHolder(new OkServlet()), "/*");

        server.setHandler(context);
        server.start();
        port = ((ServerConnector) server.getConnectors()[0]).getLocalPort();
    }

    @AfterEach
    void stopServer() throws Exception {
        if (server != null) server.stop();
    }

    @Test
    void filterOnlyRunsForRequestsUnderItsOwnProviderPath() throws Exception {
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/proxy/bitbucket.org/owner/repo.git/git-receive-pack"))
                .header("Content-Type", "application/x-git-receive-pack-request")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        client.send(request, HttpResponse.BodyHandlers.discarding());

        assertTrue(
                bitbucketFilterRan.get(),
                "filter registered under /proxy/bitbucket.org/* should run for a request on that path");
        assertFalse(
                githubFilterRan.get(),
                "filter registered under a different provider's path must not run for this request");
    }

    /** Records that it ran, and nothing else — the real per-filter logic isn't what's under test here. */
    private static class MarkerFilter extends AbstractFogwallFilter {
        private final AtomicBoolean ran;

        MarkerFilter(AtomicBoolean ran) {
            super(0);
            this.ran = ran;
        }

        @Override
        public void doHttpFilter(HttpServletRequest request, HttpServletResponse response) {
            ran.set(true);
        }
    }

    private static class OkServlet extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
            resp.setStatus(200);
        }
    }
}
