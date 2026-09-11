package com.rbc.fogwall.dashboard.e2e;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.user.UserEntry;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guards the dashboard's authorization model against the class of bug where a mutating endpoint is added but never
 * gated, and silently inherits any-authenticated access (finding H7). Two complementary checks:
 *
 * <ol>
 *   <li>{@link #everyMutatingEndpointIsClassified()} discovers every {@code POST/PUT/PATCH/DELETE} handler across the
 *       controller package by reflection and fails the build if any is missing from the explicit admin/user-facing
 *       matrix below — so a newly added mutating endpoint cannot merge without someone consciously classifying it.
 *   <li>{@link #nonAdminBlockedFromAdminMutations()} and {@link #nonAdminBlockedFromAdminReads()} boot the real Spring
 *       Security + MVC chain and assert the classification is actually enforced end to end: a normal user is 403'd on
 *       every admin surface and is not 403'd on the user-facing ones. This runs through the live filter chain and
 *       method-security proxies rather than a mock, which is the only way to confirm {@code @PreAuthorize} on the
 *       component-scanned controllers takes effect (pre-built singleton collaborators would not be proxied).
 * </ol>
 *
 * <p>The matrix is the source of truth for intended access; keep it and the matchers in {@code SecurityConfig} in step.
 */
class ApiAuthorizationMatrixTest {

    private static final String CONTROLLER_PACKAGE = "com.rbc.fogwall.dashboard.controller";
    private static final String PASSWORD = "correct-horse";

    /**
     * Admin-only mutations: everything a normal user must never reach. A new mutating endpoint that belongs here is
     * covered by {@code SecurityConfig}'s default-deny catch-all for {@code POST/PUT/PATCH/DELETE /api/**}; listing it
     * here documents the intent and asserts the catch-all actually 403s a non-admin.
     */
    private static final Set<String> ADMIN_MUTATIONS = Set.of(
            "POST /api/config/reload",
            "POST /api/groups",
            "PATCH /api/groups/{id}",
            "DELETE /api/groups/{id}",
            "POST /api/groups/{id}/members",
            "DELETE /api/groups/{id}/members/{username}",
            "POST /api/groups/{id}/permissions",
            "DELETE /api/groups/{id}/permissions/{ruleId}",
            "POST /api/repos/rules",
            "PUT /api/repos/rules/{id}",
            "POST /api/repos/rules/test",
            "DELETE /api/repos/rules/{id}",
            "DELETE /api/admin/cache",
            "DELETE /api/admin/cache/all",
            "POST /api/users",
            "POST /api/users/provision",
            "DELETE /api/users/{username}",
            "POST /api/users/{username}/reset-password",
            "POST /api/users/{username}/emails",
            "DELETE /api/users/{username}/emails/{email}",
            "POST /api/users/{username}/identities",
            "DELETE /api/users/{username}/identities/{provider}/{scmUsername}",
            "POST /api/users/{username}/permissions",
            "DELETE /api/users/{username}/permissions/{id}",
            "POST /api/users/{username}/permissions/test");

    /**
     * User-facing mutations: self-scoped (a user's own profile or OAuth links) or push-review actions that police their
     * own identity and permissions inside the controller. Each is an explicit {@code authenticated()} exception ahead
     * of the admin catch-all in {@code SecurityConfig}.
     */
    private static final Set<String> USER_MUTATIONS = Set.of(
            "POST /api/me/emails",
            "DELETE /api/me/emails/{email}",
            "POST /api/me/identities",
            "DELETE /api/me/identities/{provider}/{scmUsername}",
            "POST /api/me/ssh-keys",
            "DELETE /api/me/ssh-keys/{id}",
            "POST /api/push/{id}/authorise",
            "POST /api/push/{id}/reject",
            "POST /api/push/{id}/cancel",
            "POST /api/issues",
            "PATCH /api/issues",
            "POST /api/issues/comment",
            "POST /api/issues/state",
            "DELETE /api/scm-oauth/{providerId}/unlink");

    @Test
    void everyMutatingEndpointIsClassified() {
        Set<String> discovered = discoverMutatingEndpoints();

        Set<String> known = new HashSet<>(ADMIN_MUTATIONS);
        known.addAll(USER_MUTATIONS);

        Set<String> unclassified = new TreeSet<>(discovered);
        unclassified.removeAll(known);
        assertTrue(
                unclassified.isEmpty(),
                "Mutating endpoint(s) with no access classification. Add each to ADMIN_MUTATIONS or USER_MUTATIONS in"
                        + " this test AND ensure SecurityConfig gates it (admin mutations are covered by the default-deny"
                        + " catch-all; user-facing ones need an explicit authenticated() matcher): " + unclassified);

        Set<String> stale = new TreeSet<>(known);
        stale.removeAll(discovered);
        assertTrue(
                stale.isEmpty(), "Classified endpoints that no longer exist — remove from the test matrix: " + stale);
    }

    @Test
    void nonAdminBlockedFromAdminMutations() throws Exception {
        try (var dashboard = DashboardFixture.withLocalUsers(seededUsers())) {
            String baseUrl = dashboard.getBaseUrl();

            Session user = login(baseUrl, "user");
            for (String endpoint : ADMIN_MUTATIONS) {
                int status = probeMutation(user, baseUrl, endpoint);
                assertEquals(403, status, "A non-admin must be forbidden from admin mutation " + endpoint);
            }
            for (String endpoint : USER_MUTATIONS) {
                int status = probeMutation(user, baseUrl, endpoint);
                assertNotEquals(
                        403, status, "A normal user must not be forbidden from user-facing mutation " + endpoint);
                assertNotEquals(
                        401, status, "A logged-in user must be authenticated for user-facing mutation " + endpoint);
            }

            // Admin passes authorization on the two surfaces H7 named — proof the gate is a role check, not a blanket
            // deny that would break admins too.
            Session admin = login(baseUrl, "admin");
            for (String endpoint : List.of("PUT /api/repos/rules/{id}", "POST /api/groups")) {
                int status = probeMutation(admin, baseUrl, endpoint);
                assertNotEquals(403, status, "An admin must pass authorization on " + endpoint);
            }
        }
    }

    @Test
    void nonAdminBlockedFromAdminReads() throws Exception {
        try (var dashboard = DashboardFixture.withLocalUsers(seededUsers())) {
            String baseUrl = dashboard.getBaseUrl();
            Session user = login(baseUrl, "user");
            // Sensitive admin reads: group membership/rules and the user list. A GET needs no CSRF token, so a 403 here
            // is purely the authorization decision — and on /api/groups it also confirms the class-level @PreAuthorize
            // on a component-scanned controller actually fires.
            assertEquals(403, get(user, baseUrl, "/api/groups"), "Non-admin must not read the group list");
            assertEquals(403, get(user, baseUrl, "/api/users"), "Non-admin must not read the user list");
        }
    }

    // ── discovery ─────────────────────────────────────────────────────────────

    private static Set<String> discoverMutatingEndpoints() {
        Set<RequestMethod> mutating =
                Set.of(RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE);
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));

        Set<String> endpoints = new HashSet<>();
        for (var candidate : scanner.findCandidateComponents(CONTROLLER_PACKAGE)) {
            Class<?> controller;
            try {
                controller = Class.forName(candidate.getBeanClassName());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Discovered controller cannot be loaded: " + candidate, e);
            }
            RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            String[] basePaths = paths(classMapping);
            for (var method : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) continue;
                for (RequestMethod verb : mapping.method()) {
                    if (!mutating.contains(verb)) continue;
                    for (String base : basePaths) {
                        for (String sub : paths(mapping)) {
                            endpoints.add(verb.name() + " " + combine(base, sub));
                        }
                    }
                }
            }
        }
        return endpoints;
    }

    private static String[] paths(RequestMapping mapping) {
        if (mapping == null || mapping.path().length == 0) return new String[] {""};
        return mapping.path();
    }

    private static String combine(String base, String sub) {
        if (sub.isEmpty()) return base;
        if (base.isEmpty()) return sub;
        return base + sub;
    }

    // ── live probing ────────────────────────────────────────────────────────────

    private static List<UserEntry> seededUsers() {
        PasswordEncoder enc = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        return List.of(
                UserEntry.builder()
                        .username("admin")
                        .passwordHash(enc.encode(PASSWORD))
                        .roles(List.of("USER", "ADMIN"))
                        .build(),
                UserEntry.builder()
                        .username("user")
                        .passwordHash(enc.encode(PASSWORD))
                        .roles(List.of("USER"))
                        .build());
    }

    /** An authenticated HTTP session: a cookie-backed client plus the raw CSRF token to echo on mutations. */
    private record Session(HttpClient client, String csrf) {}

    private static Session login(String baseUrl, String username) throws Exception {
        var cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var client = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        String formBody = "username=" + username + "&password=" + PASSWORD;
        client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        var me = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/me"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, me.statusCode(), "Login must establish an authenticated session for " + username);

        // The SPA CSRF handler writes the raw XSRF-TOKEN cookie on every response and validates the X-XSRF-TOKEN
        // header against that raw value, so a mutation carrying it passes CSRF and reaches the authorization decision.
        String csrf = cookieManager.getCookieStore().getCookies().stream()
                .filter(c -> "XSRF-TOKEN".equals(c.getName()))
                .map(HttpCookie::getValue)
                .findFirst()
                .orElse(null);
        assertNotNull(csrf, "Expected an XSRF-TOKEN cookie after login for " + username);
        return new Session(client, csrf);
    }

    private static int probeMutation(Session session, String baseUrl, String endpoint) throws Exception {
        String[] parts = endpoint.split(" ", 2);
        String verb = parts[0];
        String path = concretePath(parts[1]);

        var body = "GET".equals(verb) || "DELETE".equals(verb)
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8);
        var response = session.client()
                .send(
                        HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl + path))
                                .header("Content-Type", "application/json")
                                .header("X-XSRF-TOKEN", session.csrf())
                                .method(verb, body)
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }

    private static int get(Session session, String baseUrl, String path) throws Exception {
        var response = session.client()
                .send(
                        HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl + path))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }

    /** Replaces {@code {var}} path segments with a placeholder so the request routes to the handler. */
    private static String concretePath(String template) {
        return template.replaceAll("\\{[^/}]+}", "probe");
    }
}
