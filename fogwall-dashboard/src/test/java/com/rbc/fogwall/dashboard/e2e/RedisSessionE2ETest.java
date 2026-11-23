package com.rbc.fogwall.dashboard.e2e;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.config.FogwallConfig;
import com.rbc.fogwall.user.UserEntry;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end test for {@code server.session-store: redis}.
 *
 * <p>Boots the full dashboard against a real Valkey container, logs in through the form-login flow, and checks that the
 * session is stored in Valkey and still authenticates after the dashboard is restarted. A restart is what separates a
 * Redis-backed session from the in-memory default. Session persistence does not depend on the identity provider, so the
 * login uses local users.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RedisSessionE2ETest {

    private static final String USERNAME = "redis-user";
    private static final String PASSWORD = "redis-pass-123";
    private static final String SESSION_KEY_PATTERN = "spring:session:sessions:*";

    @SuppressWarnings("resource")
    static final GenericContainer<?> VALKEY =
            new GenericContainer<>(DockerImageName.parse("docker.io/valkey/valkey:8")).withExposedPorts(6379);

    static FogwallConfig config;
    static List<UserEntry> users;
    static DashboardFixture dashboard;
    static HttpClient client;

    @BeforeAll
    static void start() throws Exception {
        VALKEY.start();

        users = List.of(UserEntry.builder()
                .username(USERNAME)
                .passwordHash(PasswordEncoderFactories.createDelegatingPasswordEncoder()
                        .encode(PASSWORD))
                .roles(List.of("USER"))
                .build());

        config = new FogwallConfig();
        config.getAuth().setProvider("local");
        config.getServer().setSessionStore("redis");
        config.getServer().getRedis().setHost(VALKEY.getHost());
        config.getServer().getRedis().setPort(VALKEY.getMappedPort(6379));

        dashboard = new DashboardFixture(config, users);

        client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @AfterAll
    static void stop() throws Exception {
        if (dashboard != null) dashboard.close();
        VALKEY.stop();
    }

    @Test
    @Order(1)
    void unauthenticatedRequestReturns401() throws Exception {
        assertEquals(401, meStatus(), "Expected 401 before login");
    }

    @Test
    @Order(2)
    void loginAuthenticatesTheSession() throws Exception {
        String form = "username=" + USERNAME + "&password=" + PASSWORD;
        client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(dashboard.getBaseUrl() + "/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        // A failed form login is also a redirect, so prove the session is authenticated.
        assertEquals(200, meStatus(), "Expected 200 from /api/me after login");
    }

    @Test
    @Order(3)
    void sessionIsStoredInValkey() throws Exception {
        var result = VALKEY.execInContainer("valkey-cli", "--scan", "--pattern", SESSION_KEY_PATTERN);

        assertEquals(0, result.getExitCode(), result.getStderr());
        assertFalse(
                result.getStdout().isBlank(),
                "Expected a spring:session:sessions:* key in Valkey; the session is not in the Redis store");
    }

    @Test
    @Order(4)
    void sessionSurvivesDashboardRestart() throws Exception {
        dashboard.close();
        dashboard = new DashboardFixture(config, users);

        assertEquals(200, meStatus(), "The session cookie from before the restart should still authenticate");
    }

    private static int meStatus() throws Exception {
        return client.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create(dashboard.getBaseUrl() + "/api/me"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString())
                .statusCode();
    }
}
