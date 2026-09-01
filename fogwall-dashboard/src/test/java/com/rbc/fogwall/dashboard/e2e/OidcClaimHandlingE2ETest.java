package com.rbc.fogwall.dashboard.e2e;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.config.FogwallConfig;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;

/**
 * End-to-end tests for OIDC claim handling — the configurations that historically broke against real enterprise IdPs
 * rather than the spec-perfect happy path {@link OidcAuthE2ETest} covers:
 *
 * <ul>
 *   <li><b>skip-user-info + a non-default user-name-attribute</b> — the ID token is the only claims source, and the
 *       principal name comes from a voluntary claim ({@code email}). This mirrors an Entra ID deployment, where the
 *       UserInfo endpoint omits the claims Spring needs.
 *   <li><b>user-name-attribute naming a claim the token doesn't carry</b> — OIDC guarantees only
 *       {@code iss/sub/aud/exp/iat}; every profile/email claim is voluntary. This must fail as a clean authentication
 *       error (redirect to the login page), never a 500.
 *   <li><b>an ID token minted for a different audience</b> — must be rejected by stock ID-token validation. Pins the
 *       audience check so no future provider workaround can silently strip it again.
 * </ul>
 */
@Tag("e2e")
class OidcClaimHandlingE2ETest {

    static final String TEST_EMAIL = "user1@example.com";

    /**
     * Extra issuer whose token callback forces {@code aud} to a different application's identifier. Matched on
     * {@code grant_type}, which is present on every token request regardless of client auth method.
     */
    private static final String JSON_CONFIG = """
            {
              "interactiveLogin": true,
              "tokenCallbacks": [
                {
                  "issuerId": "wrong-aud",
                  "requestMappings": [
                    {
                      "requestParam": "grant_type",
                      "match": "authorization_code",
                      "claims": { "aud": ["some-other-application"] }
                    }
                  ]
                }
              ]
            }
            """;

    static MockOAuth2Container mockOAuth2;

    @BeforeAll
    static void startInfrastructure() {
        mockOAuth2 = new MockOAuth2Container(JSON_CONFIG);
        mockOAuth2.start();
    }

    @AfterAll
    static void stopInfrastructure() {
        if (mockOAuth2 != null) mockOAuth2.stop();
    }

    /** Entra-shaped config: claims come from the ID token only, principal named by the email claim. */
    private static FogwallConfig entraShapedConfig(String issuerUri) {
        var config = new FogwallConfig();
        config.getAuth().setProvider("oidc");
        config.getAuth().getOidc().setIssuerUri(issuerUri);
        config.getAuth().getOidc().setClientId(MockOAuth2Container.CLIENT_ID);
        config.getAuth().getOidc().setClientSecret(MockOAuth2Container.CLIENT_SECRET);
        config.getAuth().getOidc().setSkipUserInfo(true);
        config.getAuth().getOidc().setUserNameAttribute("email");
        return config;
    }

    @Test
    void skipUserInfoWithEmailNameAttributeAuthenticates() throws Exception {
        try (var dashboard = new DashboardFixture(entraShapedConfig(mockOAuth2.getIssuerUri()))) {
            var client = newClient();
            String claims = "{\"email\":\"" + TEST_EMAIL + "\"}";

            var callbackResp = login(client, dashboard.getBaseUrl(), claims);

            String location = callbackResp.headers().firstValue("Location").orElse("(none)");
            assertEquals(
                    302, callbackResp.statusCode(), "Expected redirect after callback; body=" + callbackResp.body());
            assertFalse(location.contains("error"), "Login should succeed; redirected to: " + location);

            var meResp = get(client, dashboard.getBaseUrl() + "/api/me");
            assertEquals(200, meResp.statusCode(), "Expected authenticated /api/me; body=" + meResp.body());
            assertTrue(
                    meResp.body().contains(TEST_EMAIL),
                    "Principal should be named by the email claim; got: " + meResp.body());
        }
    }

    @Test
    void missingNameAttributeClaimFailsAsCleanAuthError() throws Exception {
        try (var dashboard = new DashboardFixture(entraShapedConfig(mockOAuth2.getIssuerUri()))) {
            var client = newClient();

            // No claims posted — the ID token carries sub but no email, and user-name-attribute is email.
            var callbackResp = login(client, dashboard.getBaseUrl(), null);

            String location = callbackResp.headers().firstValue("Location").orElse("(none)");
            assertEquals(
                    302,
                    callbackResp.statusCode(),
                    "A missing name-attribute claim must fail as an authentication error (redirect), not a server"
                            + " error; got " + callbackResp.statusCode() + " body=" + callbackResp.body());
            assertTrue(location.contains("error"), "Expected redirect to the login error page; got: " + location);

            var meResp = get(client, dashboard.getBaseUrl() + "/api/me");
            assertEquals(401, meResp.statusCode(), "No session should exist after the failed login");
        }
    }

    @Test
    void missingNameAttributeWithUserInfoEnabledFailsAsCleanAuthError() throws Exception {
        // Same missing-claim scenario but with the UserInfo call enabled: Spring builds the principal
        // from the UserInfo *response alone*, so this exercises the other conversion path (the one a
        // live Entra ID tenant hits when user-name-attribute names a claim its userinfo omits).
        var config = entraShapedConfig(mockOAuth2.getIssuerUri());
        config.getAuth().getOidc().setSkipUserInfo(false);

        try (var dashboard = new DashboardFixture(config)) {
            var client = newClient();

            var callbackResp = login(client, dashboard.getBaseUrl(), null);

            String location = callbackResp.headers().firstValue("Location").orElse("(none)");
            assertEquals(
                    302,
                    callbackResp.statusCode(),
                    "A name attribute missing from the UserInfo response must fail as an authentication error"
                            + " (redirect), not a server error; got " + callbackResp.statusCode() + " body="
                            + callbackResp.body());
            assertTrue(location.contains("error"), "Expected redirect to the login error page; got: " + location);

            var meResp = get(client, dashboard.getBaseUrl() + "/api/me");
            assertEquals(401, meResp.statusCode(), "No session should exist after the failed login");
        }
    }

    @Test
    void wrongAudienceTokenIsRejected() throws Exception {
        // Default user-name-attribute (sub) — this test is about token validation, not claim mapping.
        var config = new FogwallConfig();
        config.getAuth().setProvider("oidc");
        config.getAuth().getOidc().setIssuerUri(mockOAuth2.getIssuerUri("wrong-aud"));
        config.getAuth().getOidc().setClientId(MockOAuth2Container.CLIENT_ID);
        config.getAuth().getOidc().setClientSecret(MockOAuth2Container.CLIENT_SECRET);
        config.getAuth().getOidc().setSkipUserInfo(true);

        try (var dashboard = new DashboardFixture(config)) {
            var client = newClient();

            var callbackResp = login(client, dashboard.getBaseUrl(), null);

            String location = callbackResp.headers().firstValue("Location").orElse("(none)");
            assertEquals(302, callbackResp.statusCode(), "Expected redirect after callback");
            assertTrue(
                    location.contains("error"),
                    "An ID token minted for a different audience must be rejected; redirected to: " + location);

            var meResp = get(client, dashboard.getBaseUrl() + "/api/me");
            assertEquals(401, meResp.statusCode(), "No session should exist after the rejected token");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    /**
     * Drives the authorization code flow against the mock server: starts the flow, posts the username (and optional
     * custom claims JSON, which the mock server merges into the ID token), then follows the redirect back to the
     * dashboard callback and returns that callback response — the point where token validation and principal
     * construction succeed or fail.
     */
    private static HttpResponse<String> login(HttpClient client, String dashboardBaseUrl, String claimsJson)
            throws Exception {
        var authorizePage =
                followUntil200(client, dashboardBaseUrl, dashboardBaseUrl + "/oauth2/authorization/fogwall");
        assertEquals(200, authorizePage.statusCode(), "Expected mock server login page");

        String loginBody = "username=" + MockOAuth2Container.TEST_USER;
        if (claimsJson != null) {
            loginBody += "&claims=" + URLEncoder.encode(claimsJson, StandardCharsets.UTF_8);
        }
        var loginResp = client.send(
                HttpRequest.newBuilder()
                        .uri(authorizePage.uri())
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(loginBody, StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(302, loginResp.statusCode(), "Expected redirect from mock server after login POST");

        String callbackUrl = loginResp.headers().firstValue("Location").orElseThrow();
        assertTrue(callbackUrl.contains("/login/oauth2/code/"), "Expected callback URL; got: " + callbackUrl);
        return client.send(
                HttpRequest.newBuilder().uri(URI.create(callbackUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> followUntil200(HttpClient client, String baseUrl, String startUrl)
            throws Exception {
        String url = startUrl;
        for (int i = 0; i < 10; i++) {
            var resp = client.send(
                    HttpRequest.newBuilder().uri(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 300 || resp.statusCode() >= 400) {
                return resp;
            }
            String location = resp.headers()
                    .firstValue("Location")
                    .orElseThrow(() -> new AssertionError("3xx without Location: " + resp.uri()));
            if (!location.startsWith("http")) location = baseUrl + location;
            url = location;
        }
        throw new AssertionError("Too many redirects starting from " + startUrl);
    }

    private static HttpClient newClient() {
        return HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    private static HttpResponse<String> get(HttpClient client, String url) throws Exception {
        return client.send(
                HttpRequest.newBuilder().uri(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
