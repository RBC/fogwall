package com.rbc.fogwall.dashboard.e2e;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.config.FogwallConfig;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;

/**
 * End-to-end tests for LDAP authentication via the dashboard Spring Security stack.
 *
 * <p>Starts a real Bitnami OpenLDAP container and a Jetty server running the full Spring MVC + Spring Security stack
 * configured with {@code auth.provider=ldap}. Exercises the form-login → LDAP bind → session flow.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LdapAuthE2ETest {

    static OpenLdapContainer ldap;
    static DashboardFixture dashboard;
    static HttpClient client;
    static String baseUrl;

    @BeforeAll
    static void startInfrastructure() throws Exception {
        ldap = new OpenLdapContainer();
        ldap.start();

        var config = new FogwallConfig();
        config.getAuth().setProvider("ldap");
        config.getAuth().getLdap().setUrl(ldap.getLdapUrl());
        config.getAuth().getLdap().setUserDnPatterns(OpenLdapContainer.USER_DN_PATTERN);

        dashboard = new DashboardFixture(config);
        baseUrl = dashboard.getBaseUrl();

        var cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        client = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @AfterAll
    static void stopInfrastructure() throws Exception {
        if (dashboard != null) dashboard.close();
        if (ldap != null) ldap.stop();
    }

    @Test
    @Order(1)
    void unauthenticatedRequestReturns401() throws Exception {
        var req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/me"))
                .GET()
                .build();

        var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, resp.statusCode(), "Expected 401 for unauthenticated /api/me");
    }

    @Test
    @Order(2)
    void loginWithValidLdapCredentialsSucceeds() throws Exception {
        String formBody = "username=" + OpenLdapContainer.TEST_USER + "&password=" + OpenLdapContainer.TEST_PASSWORD;

        var req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
                .build();

        // Spring Security's form login redirects to the default success URL on success.
        var resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        // After successful login + redirect to /, the final status should be 200 (or a Spring MVC
        // response for /). We just check that we did NOT get a 401 or stay on the login page.
        assertNotEquals(401, resp.statusCode(), "Should not be 401 after valid LDAP login");
        assertNotEquals(403, resp.statusCode(), "Should not be 403 after valid LDAP login");
    }

    @Test
    @Order(3)
    void authenticatedUserCanAccessMeEndpoint() throws Exception {
        // Re-use the session cookie from the previous login test. The cookie manager retains it.
        var req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/me"))
                .GET()
                .build();

        var resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode(), "Expected 200 for authenticated /api/me");
        assertTrue(
                resp.body().contains(OpenLdapContainer.TEST_USER),
                "Response should contain the authenticated username; got: " + resp.body());
    }

    @Test
    @Order(4)
    void loginWithWrongPasswordFails() throws Exception {
        // Fresh client with no session
        var freshCookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var freshClient = HttpClient.newBuilder()
                .cookieHandler(freshCookieManager)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        String formBody = "username=" + OpenLdapContainer.TEST_USER + "&password=wrongpassword";

        var req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
                .build();

        var resp = freshClient.send(req, HttpResponse.BodyHandlers.ofString());

        // Spring Security redirects to /login?error on failure; after following redirects the page
        // should not have authenticated successfully. We check the /api/me endpoint still returns 401.
        var meReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/me"))
                .GET()
                .build();
        var meResp = freshClient.send(meReq, HttpResponse.BodyHandlers.ofString());

        assertEquals(401, meResp.statusCode(), "Should be 401 after failed LDAP login attempt");
    }
}
