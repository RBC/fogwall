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
 * End-to-end tests for OIDC authentication via the dashboard Spring Security stack.
 *
 * <p>Starts a {@link MockOAuth2Container} and a Jetty server configured with {@code auth.provider=oidc}. Drives the
 * full OAuth2 authorization code flow:
 *
 * <ol>
 *   <li>Unauthenticated request to {@code /api/me} → 401 (SPA entry-point, no redirect)
 *   <li>Trigger the OIDC flow via {@code /oauth2/authorization/fogwall} → redirect chain to mock server login
 *   <li>POST username to the mock server authorize endpoint
 *   <li>Mock server redirects back to the dashboard callback URL with an auth code
 *   <li>Dashboard exchanges the code → session cookie → 200 on {@code /api/me}
 * </ol>
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OidcAuthE2ETest {

    static MockOAuth2Container mockOAuth2;
    static DashboardFixture dashboard;
    static String baseUrl;

    @BeforeAll
    static void startInfrastructure() throws Exception {
        mockOAuth2 = new MockOAuth2Container();
        mockOAuth2.start();

        var config = new FogwallConfig();
        config.getAuth().setProvider("oidc");
        config.getAuth().getOidc().setIssuerUri(mockOAuth2.getIssuerUri());
        config.getAuth().getOidc().setClientId(MockOAuth2Container.CLIENT_ID);
        config.getAuth().getOidc().setClientSecret(MockOAuth2Container.CLIENT_SECRET);

        dashboard = new DashboardFixture(config);
        baseUrl = dashboard.getBaseUrl();
    }

    @AfterAll
    static void stopInfrastructure() throws Exception {
        if (dashboard != null) dashboard.close();
        if (mockOAuth2 != null) mockOAuth2.stop();
    }

    @Test
    @Order(1)
    void unauthenticatedApiRequestReturns401() throws Exception {
        var client = freshClient();
        var resp = get(client, baseUrl + "/api/me");
        assertEquals(401, resp.statusCode(), "Expected 401 for unauthenticated /api/me");
    }

    @Test
    @Order(2)
    void fullOidcFlowAuthenticatesUser() throws Exception {
        var cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        // Manual redirect following — we need to POST credentials to the mock server mid-flow.
        var client = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        // Step 1: Trigger the OIDC flow. /oauth2/authorization/fogwall redirects to the mock server's
        // authorize endpoint. Follow until we land on the login form (status 200).
        // NOTE: /api/me returns 401 (SPA entry-point) rather than redirecting; start here instead.
        var authorizePageResponse = followUntil200(client, baseUrl + "/oauth2/authorization/fogwall");
        assertEquals(200, authorizePageResponse.statusCode(), "Expected mock server login page");

        // Step 2: POST username to the same authorize URL (mock server login form has a single
        // "username" field; any value is accepted, no password required).
        URI authorizeUri = authorizePageResponse.uri();
        String loginBody = "username=" + MockOAuth2Container.TEST_USER;

        var loginResp = client.send(
                HttpRequest.newBuilder()
                        .uri(authorizeUri)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(loginBody, StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(302, loginResp.statusCode(), "Expected redirect from mock server after login POST");
        String callbackUrl = loginResp.headers().firstValue("Location").orElseThrow();
        assertTrue(
                callbackUrl.contains("/login/oauth2/code/"),
                "Expected redirect to dashboard callback URL; got: " + callbackUrl);

        // Step 3: Follow the callback to the dashboard. Spring Security exchanges the code for tokens
        // (server-side call to mock server /token), validates the id_token, creates a session, and
        // redirects to the success URL.
        var callbackResp = client.send(
                HttpRequest.newBuilder().uri(URI.create(callbackUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        String callbackLocation = callbackResp.headers().firstValue("Location").orElse("(none)");
        assertEquals(
                302,
                callbackResp.statusCode(),
                "Expected 302 from dashboard callback; got " + callbackResp.statusCode()
                        + " location=" + callbackLocation
                        + " body=" + callbackResp.body());
        assertFalse(
                callbackLocation.contains("error"), "Dashboard callback redirected to error page: " + callbackLocation);

        String successUrl = callbackLocation;
        if (!successUrl.startsWith("http")) successUrl = baseUrl + successUrl;
        client.send(
                HttpRequest.newBuilder().uri(URI.create(successUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        // Step 4: Verify the session cookie allows access to the API.
        var meResp = get(client, baseUrl + "/api/me");
        assertEquals(200, meResp.statusCode(), "Expected 200 for /api/me after OIDC login; body=" + meResp.body());
        assertTrue(
                meResp.body().contains(MockOAuth2Container.TEST_USER),
                "Response should include the authenticated username; got: " + meResp.body());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    /**
     * Follows redirects manually until a non-3xx response is returned, then returns the response (including URI of the
     * final request). Used instead of {@code HttpClient.Redirect.NORMAL} so we can inspect the final URL and POST to
     * it.
     */
    private static HttpResponse<String> followUntil200(HttpClient client, String startUrl) throws Exception {
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

    private static HttpClient freshClient() {
        return HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    private static HttpResponse<String> get(HttpClient client, String url) throws Exception {
        return client.send(
                HttpRequest.newBuilder().uri(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
