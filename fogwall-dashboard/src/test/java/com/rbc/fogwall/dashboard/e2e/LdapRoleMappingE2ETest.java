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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;

/**
 * End-to-end tests for LDAP group-to-role mapping.
 *
 * <p>Starts a Bitnami OpenLDAP container with a test user that is a member of an LDAP group, then configures
 * {@code auth.role-mappings} to map that group to {@code ROLE_ADMIN}. Verifies that after login the {@code /api/me}
 * response includes the expected authority.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LdapRoleMappingE2ETest {

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
        config.getAuth().getLdap().setBindDn(OpenLdapContainer.MANAGER_DN);
        config.getAuth().getLdap().setBindPassword(OpenLdapContainer.ADMIN_PASSWORD);
        config.getAuth().getLdap().setGroupSearchBase(OpenLdapContainer.GROUP_SEARCH_BASE);
        config.getAuth().setRoleMappings(Map.of("ADMIN", List.of(OpenLdapContainer.ADMIN_GROUP)));

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
    void loginSucceeds() throws Exception {
        // Retried because the first login after several test classes can hit a stale pooled LDAP
        // connection: each class runs its own container on an ephemeral host port, stopped containers
        // free their ports for reuse, and the JVM-global JNDI pool can hand back a socket to a dead
        // predecessor ("java.io.IOException: LDAP connection has been closed" after a successful
        // bind). The pool discards the dead connection on failure, so one retry gets a fresh socket.
        // A test-environment artifact, not a product concern — production LDAP servers do not churn
        // across reused ports. The success criterion is a real one (authenticated /api/me), stronger
        // than the earlier not-401 check, so genuine login breakage still fails after the retries.
        int status = -1;
        String formBody = "username=" + OpenLdapContainer.TEST_USER + "&password=" + OpenLdapContainer.TEST_PASSWORD;
        for (int attempt = 1; attempt <= 3; attempt++) {
            var cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            var freshClient = HttpClient.newBuilder()
                    .cookieHandler(cookieManager)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            freshClient.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl + "/login"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            var meResp = freshClient.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl + "/api/me"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            status = meResp.statusCode();
            if (status == 200) {
                client = freshClient; // later tests reuse the authenticated session
                return;
            }
            Thread.sleep(500);
        }
        assertEquals(200, status, "Login did not produce an authenticated session after 3 attempts");
    }

    @Test
    @Order(2)
    void ldapGroupMembershipGrantsConfiguredRole() throws Exception {
        var resp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/me"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(
                resp.body().contains("ROLE_ADMIN"),
                "Expected ROLE_ADMIN from LDAP group membership in authorities; got: " + resp.body());
    }

    /**
     * Verifies deny-by-default: when role mappings are configured, a user whose LDAP groups do not match any mapping
     * must be rejected at login.
     */
    @Test
    @Order(3)
    void userNotInMappedGroup_loginDenied() throws Exception {
        // Start a fresh dashboard that maps a *different* group — testuser is not a member of it.
        var config = new FogwallConfig();
        config.getAuth().setProvider("ldap");
        config.getAuth().getLdap().setUrl(ldap.getLdapUrl());
        config.getAuth().getLdap().setUserDnPatterns(OpenLdapContainer.USER_DN_PATTERN);
        config.getAuth().getLdap().setBindDn(OpenLdapContainer.MANAGER_DN);
        config.getAuth().getLdap().setBindPassword(OpenLdapContainer.ADMIN_PASSWORD);
        config.getAuth().getLdap().setGroupSearchBase(OpenLdapContainer.GROUP_SEARCH_BASE);
        // Map a group the test user is NOT a member of
        config.getAuth().setRoleMappings(Map.of("ADMIN", List.of("no-such-group")));

        try (var restrictedDashboard = new DashboardFixture(config)) {
            var restrictedBaseUrl = restrictedDashboard.getBaseUrl();
            var cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            var restrictedClient = HttpClient.newBuilder()
                    .cookieHandler(cookieManager)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            String formBody =
                    "username=" + OpenLdapContainer.TEST_USER + "&password=" + OpenLdapContainer.TEST_PASSWORD;
            restrictedClient.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(restrictedBaseUrl + "/login"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            // After a failed login, /api/me must be inaccessible (401 or redirect to login)
            var meResp = restrictedClient.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(restrictedBaseUrl + "/api/me"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertNotEquals(200, meResp.statusCode(), "User not in mapped group must not access /api/me");
        }
    }

    /**
     * Verifies {@code auth.require-role-mapping: false}: a user whose LDAP groups match none of the configured mappings
     * is still granted {@code ROLE_USER} instead of being denied.
     */
    @Test
    @Order(4)
    void userNotInMappedGroup_requireRoleMappingFalse_loginGrantsRoleUser() throws Exception {
        var config = new FogwallConfig();
        config.getAuth().setProvider("ldap");
        config.getAuth().getLdap().setUrl(ldap.getLdapUrl());
        config.getAuth().getLdap().setUserDnPatterns(OpenLdapContainer.USER_DN_PATTERN);
        config.getAuth().getLdap().setBindDn(OpenLdapContainer.MANAGER_DN);
        config.getAuth().getLdap().setBindPassword(OpenLdapContainer.ADMIN_PASSWORD);
        config.getAuth().getLdap().setGroupSearchBase(OpenLdapContainer.GROUP_SEARCH_BASE);
        // Map a group the test user is NOT a member of, but disable deny-by-default.
        config.getAuth().setRoleMappings(Map.of("ADMIN", List.of("no-such-group")));
        config.getAuth().setRequireRoleMapping(false);

        try (var openDashboard = new DashboardFixture(config)) {
            var openBaseUrl = openDashboard.getBaseUrl();
            var cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            var openClient = HttpClient.newBuilder()
                    .cookieHandler(cookieManager)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            String formBody =
                    "username=" + OpenLdapContainer.TEST_USER + "&password=" + OpenLdapContainer.TEST_PASSWORD;
            openClient.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(openBaseUrl + "/login"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            var meResp = openClient.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(openBaseUrl + "/api/me"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(
                    200,
                    meResp.statusCode(),
                    "User not in mapped group must still log in when require-role-mapping=false");
            assertTrue(
                    meResp.body().contains("ROLE_USER"),
                    "Expected ROLE_USER to be granted unconditionally; got: " + meResp.body());
        }
    }
}
