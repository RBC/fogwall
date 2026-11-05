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
import tools.jackson.databind.json.JsonMapper;

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
        // No retries: the "LDAP connection has been closed" failures this test once retried around
        // were the openldap container's silent slapd restart during startup, fixed at the source by
        // OpenLdapContainer's wait strategy. A login failure here is a real finding.
        String formBody = "username=" + OpenLdapContainer.TEST_USER + "&password=" + OpenLdapContainer.TEST_PASSWORD;
        client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        var meResp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/me"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, meResp.statusCode(), "Login must establish an authenticated session; got " + meResp.body());
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
     * Verifies {@code auth.default-role: READER}: a user whose LDAP groups match none of the configured mappings signs
     * in as {@code READER} instead of being denied, and does not act.
     */
    @Test
    @Order(4)
    void userNotInMappedGroup_defaultRoleReader_loginGrantsReader() throws Exception {
        var config = new FogwallConfig();
        config.getAuth().setProvider("ldap");
        config.getAuth().getLdap().setUrl(ldap.getLdapUrl());
        config.getAuth().getLdap().setUserDnPatterns(OpenLdapContainer.USER_DN_PATTERN);
        config.getAuth().getLdap().setBindDn(OpenLdapContainer.MANAGER_DN);
        config.getAuth().getLdap().setBindPassword(OpenLdapContainer.ADMIN_PASSWORD);
        config.getAuth().getLdap().setGroupSearchBase(OpenLdapContainer.GROUP_SEARCH_BASE);
        // Map a group the test user is NOT a member of, but disable deny-by-default.
        config.getAuth().setRoleMappings(Map.of("ADMIN", List.of("no-such-group")));
        config.getAuth().setDefaultRole("READER");

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
                    200, meResp.statusCode(), "User not in mapped group must still log in when default-role is READER");
            assertTrue(
                    meResp.body().contains("ROLE_READER") && !meResp.body().contains("ROLE_USER"),
                    "Expected ROLE_READER and no ROLE_USER for an unmapped user; got: " + meResp.body());
        }
    }

    /**
     * The roles a directory asserts are compared with the stored ones at each sign-in, and a difference is recorded as
     * a role change against the identity provider: one sign-in that gains roles, and a later one that loses a group.
     */
    @Test
    @Order(5)
    void aChangeInDirectoryGroups_isRecordedAsARoleChangeAtTheNextSignIn() throws Exception {
        String member = "cn=" + OpenLdapContainer.TEST_USER + ",ou=users," + OpenLdapContainer.BASE_DN;
        String adminGroup = "cn=" + OpenLdapContainer.ADMIN_GROUP + ",ou=groups," + OpenLdapContainer.BASE_DN;
        ldap.modify("dn: cn=contributors,ou=groups," + OpenLdapContainer.BASE_DN + "\n"
                + "changetype: add\n"
                + "objectClass: groupOfNames\n"
                + "cn: contributors\n"
                + "member: " + member + "\n");
        // A groupOfNames must keep a member, so the admin group gets a second one before the test user leaves it.
        ldap.modify("dn: " + adminGroup + "\nchangetype: modify\nadd: member\nmember: " + OpenLdapContainer.MANAGER_DN
                + "\n");

        var config = new FogwallConfig();
        config.getAuth().setProvider("ldap");
        config.getAuth().getLdap().setUrl(ldap.getLdapUrl());
        config.getAuth().getLdap().setUserDnPatterns(OpenLdapContainer.USER_DN_PATTERN);
        config.getAuth().getLdap().setBindDn(OpenLdapContainer.MANAGER_DN);
        config.getAuth().getLdap().setBindPassword(OpenLdapContainer.ADMIN_PASSWORD);
        config.getAuth().getLdap().setGroupSearchBase(OpenLdapContainer.GROUP_SEARCH_BASE);
        config.getAuth()
                .setRoleMappings(
                        Map.of("ADMIN", List.of(OpenLdapContainer.ADMIN_GROUP), "USER", List.of("contributors")));

        try (var audited = new DashboardFixture(config)) {
            String auditedUrl = audited.getBaseUrl();
            HttpClient asAdmin = signIn(auditedUrl);

            ldap.modify("dn: " + adminGroup + "\nchangetype: modify\ndelete: member\nmember: " + member + "\n");
            try {
                signIn(auditedUrl);
            } finally {
                ldap.modify("dn: " + adminGroup + "\nchangetype: modify\nadd: member\nmember: " + member + "\n");
            }

            // The first session still carries the ADMIN it signed in with, so it can read the record. The fixtures in
            // this class share one in-memory database and the earlier tests sign the same user in under other
            // mappings, so only the two newest events are this test's.
            var events = asAdmin.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(auditedUrl + "/api/audit/role-changes?limit=2&subject="
                                    + OpenLdapContainer.TEST_USER))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, events.statusCode(), events.body());
            var rows = JsonMapper.builder().build().readTree(events.body());
            assertEquals(2, rows.size(), events.body());
            var lost = rows.get(0);
            var gained = rows.get(1);
            for (var row : List.of(lost, gained)) {
                assertEquals("IDP", row.get("origin").asString());
                assertEquals("ldap", row.get("actor").asString());
            }
            assertEquals("[\"ADMIN\",\"USER\"]", gained.get("roles").toString());
            assertEquals("[\"ADMIN\",\"USER\"]", lost.get("previousRoles").toString());
            assertEquals("[\"USER\"]", lost.get("roles").toString());
        }
    }

    /** Signs the test user in on a fresh session and returns its client. */
    private static HttpClient signIn(String url) throws Exception {
        var signedIn = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        String formBody = "username=" + OpenLdapContainer.TEST_USER + "&password=" + OpenLdapContainer.TEST_PASSWORD;
        signedIn.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(url + "/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        var me = signedIn.send(
                HttpRequest.newBuilder().uri(URI.create(url + "/api/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, me.statusCode(), "sign-in must establish a session; got " + me.body());
        return signedIn;
    }
}
