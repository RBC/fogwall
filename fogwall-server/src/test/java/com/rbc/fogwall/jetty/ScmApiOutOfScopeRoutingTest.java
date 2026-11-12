package com.rbc.fogwall.jetty;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.db.ScmApiActionStore;
import com.rbc.fogwall.db.model.ScmApiActionRecord;
import com.rbc.fogwall.db.model.ScmApiActionStatus;
import com.rbc.fogwall.observability.FogwallTelemetry;
import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.service.PushIdentityResolver;
import com.rbc.fogwall.service.ResolvedScmIdentity;
import com.rbc.fogwall.servlet.ScmApiGitHubMetaServlet;
import com.rbc.fogwall.user.UserEntry;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Pins how the out-of-scope handler is mounted on a real Jetty context: it answers only what no dialect mount claims,
 * behind authentication and audit, and the {@code gh} version handshake is served without either.
 */
class ScmApiOutOfScopeRoutingTest {

    private Server server;
    private int port;
    private ScmApiActionStore actionStore;
    private PushIdentityResolver resolver;

    private static class DialectServlet extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setStatus(200);
            resp.getWriter().write("dialect");
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        actionStore = mock(ScmApiActionStore.class);
        resolver = mock(PushIdentityResolver.class);
        var fogwallContext = mock(FogwallContext.class);
        when(fogwallContext.telemetry()).thenReturn(FogwallTelemetry.disabled());
        when(fogwallContext.scmApiActionStore()).thenReturn(actionStore);
        when(fogwallContext.pushIdentityResolver()).thenReturn(resolver);
        var provider = mock(FogwallProvider.class);
        when(provider.getName()).thenReturn("github");
        when(provider.getProviderId()).thenReturn("github");

        server = new Server();
        var connector = new ServerConnector(server);
        connector.setPort(0);
        server.addConnector(connector);

        var context = new ServletContextHandler("/", false, false);
        context.addServlet(new ServletHolder(new DialectServlet()), "/api/graphql");
        context.addServlet(new ServletHolder(new ScmApiGitHubMetaServlet()), ScmApiGitHubMetaServlet.MOUNT);
        FogwallServletRegistrar.registerScmApiOutOfScope(context, provider, fogwallContext);
        server.setHandler(context);
        server.start();
        port = connector.getLocalPort();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) server.stop();
    }

    private HttpResponse<String> send(String method, String path, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void anAuthenticatedCallerIsToldWhatTheListenerCoversAndTheRefusalIsAudited() throws Exception {
        when(resolver.resolveIdentity(any(), any(), eq("tok")))
                .thenReturn(Optional.of(new ResolvedScmIdentity(
                        UserEntry.builder().username("alice").build(), "alice-gh")));

        var response = send("GET", "/api/v3/repos/acme/widgets/actions/runs", "tok");

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("outside the github SCM API"), response.body());
        assertTrue(response.body().contains("\"message\""), "gh renders the message field");
        var saved = ArgumentCaptor.forClass(ScmApiActionRecord.class);
        verify(actionStore).save(saved.capture());
        assertEquals(ScmApiActionStatus.REJECTED, saved.getValue().getStatus());
        assertEquals("alice", saved.getValue().getResolvedUser());
    }

    @Test
    void anAnonymousCallerIsRefusedBeforeReachingTheHandler() throws Exception {
        var response = send("GET", "/api/v3/repos/acme/widgets/actions/runs", null);

        assertEquals(401, response.statusCode());
        verifyNoInteractions(actionStore);
    }

    @Test
    void theDialectMountIsNotShadowedByTheFallback() throws Exception {
        var response = send("POST", "/api/graphql", null);

        assertEquals(200, response.statusCode());
        assertEquals("dialect", response.body());
    }

    @Test
    void theVersionHandshakeAnswersWithoutACredential() throws Exception {
        var response = send("GET", ScmApiGitHubMetaServlet.MOUNT, null);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"installed_version\":\"3.18.0\""), response.body());
        verifyNoInteractions(resolver);
    }
}
