package com.rbc.fogwall.e2e;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.model.MatchTarget;
import com.rbc.fogwall.db.model.MatchType;
import com.rbc.fogwall.permission.RepoPermission;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.*;

/**
 * End-to-end tests for the repository permission system in transparent proxy mode.
 *
 * <p>Identity resolves the way it does in production: the access token in the push URL is looked up against the
 * upstream ({@code GET /api/v1/user}), and the login it belongs to is matched against a user's SCM identities. The
 * "authorized" user is {@link GiteaContainer#TEST_USER}, registered in the proxy with that identity; the "unlinked"
 * user pushes with an admin token, which Gitea accepts and the proxy maps to nobody.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>Literal path grants — exact {@code /owner/repo} match
 *   <li>Glob path grants — {@code /owner/*} wildcard
 *   <li>Regex path grants — full Java regex against the path
 *   <li>Fail-closed semantics — no grant → push blocked
 *   <li>Unregistered user → push blocked with "Identity Not Linked"
 * </ul>
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PermissionE2ETest {

    static GiteaContainer gitea;
    static JettyProxyFixture proxy;
    static String testUserToken;
    static String adminToken;
    static Path tempDir;

    @BeforeAll
    static void startInfrastructure() throws Exception {
        gitea = new GiteaContainer();
        gitea.start();
        gitea.createAdminUser();
        gitea.createTestRepo();
        // Create a non-admin user and grant write access so they can push via the proxy
        gitea.createTestUser();
        gitea.addTestUserAsCollaborator();

        testUserToken = gitea.generateTestUserToken();
        adminToken = gitea.generateAdminPushToken();

        // Only TEST_USER is registered in the proxy — admin is intentionally absent (unlinked)
        proxy = new JettyProxyFixture(
                gitea.getBaseUri(),
                List.of(new JettyProxyFixture.TestUser(
                        GiteaContainer.TEST_USER, GiteaContainer.VALID_AUTHOR_EMAIL, GiteaContainer.TEST_USER)),
                null);
        tempDir = Files.createTempDirectory("fogwall-perm-e2e-");
    }

    @AfterAll
    static void stopInfrastructure() throws Exception {
        if (proxy != null) proxy.close();
        if (gitea != null) gitea.stop();
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    /** URL with {@link GiteaContainer#TEST_USER} credentials — the registered proxy user. */
    private String authorisedUrl() {
        String creds = URLEncoder.encode(GiteaContainer.TEST_USER, StandardCharsets.UTF_8)
                + ":"
                + URLEncoder.encode(testUserToken, StandardCharsets.UTF_8);
        return "http://" + creds + "@localhost:" + proxy.getPort()
                + "/proxy/" + proxy.getGiteaHostPort() + "/"
                + GiteaContainer.TEST_ORG + "/" + GiteaContainer.TEST_REPO + ".git";
    }

    /**
     * URL with admin credentials — valid for Gitea authentication but the admin user is NOT registered in the proxy
     * user store, so it is treated as an "unlinked" identity.
     */
    private String unlinkedUrl() {
        String creds = URLEncoder.encode(GiteaContainer.ADMIN_USER, StandardCharsets.UTF_8)
                + ":"
                + URLEncoder.encode(adminToken, StandardCharsets.UTF_8);
        return "http://" + creds + "@localhost:" + proxy.getPort()
                + "/proxy/" + proxy.getGiteaHostPort() + "/"
                + GiteaContainer.TEST_ORG + "/" + GiteaContainer.TEST_REPO + ".git";
    }

    private GitHelper.PushResult cloneCommitPush(String url, String dirSuffix) throws Exception {
        GitHelper git = new GitHelper(tempDir);
        Path repo = git.clone(url, dirSuffix);
        git.setAuthor(repo, GiteaContainer.VALID_AUTHOR_NAME, GiteaContainer.VALID_AUTHOR_EMAIL);
        git.writeAndStage(repo, "test-file.txt", dirSuffix + " - " + Instant.now());
        git.commit(repo, "feat: permission test commit");
        return git.pushWithResult(repo);
    }

    // ── tests ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void noGrant_registeredUser_blocked() throws Exception {
        // No grants in the store at all — fail-closed should block even a registered user.
        var result = cloneCommitPush(authorisedUrl(), "perm-no-grant");
        assertFalse(result.succeeded(), "push should be blocked when no grants exist (fail-closed)");
        assertTrue(
                result.output().contains("not allowed to push")
                        || result.output().contains("Unauthorized"),
                "output should indicate authorization failure. Output:\n" + result.output());
    }

    @Test
    @Order(2)
    void unlinkedUser_blocked_with_identity_not_linked() throws Exception {
        var result = cloneCommitPush(unlinkedUrl(), "perm-unlinked");
        assertFalse(result.succeeded(), "push should be blocked for unlinked user");
        assertTrue(
                result.output().contains("Identity Not Linked"),
                "output should indicate identity not linked. Output:\n" + result.output());
    }

    @Test
    @Order(10)
    void literal_grant_allows_push() throws Exception {
        String path = "/" + GiteaContainer.TEST_ORG + "/" + GiteaContainer.TEST_REPO;
        proxy.getPermissionService()
                .save(RepoPermission.builder()
                        .username(GiteaContainer.TEST_USER)
                        .provider(proxy.getProviderId())
                        .target(MatchTarget.SLUG)
                        .value(path)
                        .matchType(MatchType.LITERAL)
                        .grant(RepoPermission.Grant.PUSH)
                        .build());

        var result = cloneCommitPush(authorisedUrl(), "perm-literal");
        // Permission passes → push is valid → blocked pending review (not rejected outright)
        assertFalse(result.succeeded(), "valid push should be blocked pending review (not rejected)");
        assertDoesNotThrow(
                result::extractPushId, "a pending-review block should contain a push ID. Output:\n" + result.output());
    }

    @Test
    @Order(11)
    void glob_grant_allows_push() throws Exception {
        // Replace literal grant with a glob covering all repos under TEST_ORG
        proxy.getPermissionService().findAll().stream()
                .filter(p -> GiteaContainer.TEST_USER.equals(p.getUsername()))
                .forEach(p -> proxy.getPermissionService().delete(p.getId()));

        proxy.getPermissionService()
                .save(RepoPermission.builder()
                        .username(GiteaContainer.TEST_USER)
                        .provider(proxy.getProviderId())
                        .target(MatchTarget.SLUG)
                        .value("/" + GiteaContainer.TEST_ORG + "/*")
                        .matchType(MatchType.GLOB)
                        .grant(RepoPermission.Grant.PUSH)
                        .build());

        var result = cloneCommitPush(authorisedUrl(), "perm-glob");
        assertFalse(result.succeeded(), "valid push should be blocked pending review");
        assertDoesNotThrow(
                result::extractPushId,
                "should be blocked pending review (not auth error). Output:\n" + result.output());
    }

    @Test
    @Order(12)
    void regex_grant_allows_push() throws Exception {
        proxy.getPermissionService().findAll().stream()
                .filter(p -> GiteaContainer.TEST_USER.equals(p.getUsername()))
                .forEach(p -> proxy.getPermissionService().delete(p.getId()));

        proxy.getPermissionService()
                .save(RepoPermission.builder()
                        .username(GiteaContainer.TEST_USER)
                        .provider(proxy.getProviderId())
                        .target(MatchTarget.SLUG)
                        .value("^/" + GiteaContainer.TEST_ORG + "/.+")
                        .matchType(MatchType.REGEX)
                        .grant(RepoPermission.Grant.PUSH)
                        .build());

        var result = cloneCommitPush(authorisedUrl(), "perm-regex");
        assertFalse(result.succeeded(), "valid push should be blocked pending review");
        assertDoesNotThrow(
                result::extractPushId,
                "should be blocked pending review (not auth error). Output:\n" + result.output());
    }

    @Test
    @Order(20)
    void glob_grant_does_not_match_different_owner() throws Exception {
        proxy.getPermissionService().findAll().stream()
                .filter(p -> GiteaContainer.TEST_USER.equals(p.getUsername()))
                .forEach(p -> proxy.getPermissionService().delete(p.getId()));

        // Grant only for "other-owner/*" — should not match TEST_ORG
        proxy.getPermissionService()
                .save(RepoPermission.builder()
                        .username(GiteaContainer.TEST_USER)
                        .provider(proxy.getProviderId())
                        .target(MatchTarget.SLUG)
                        .value("/other-owner/*")
                        .matchType(MatchType.GLOB)
                        .grant(RepoPermission.Grant.PUSH)
                        .build());

        var result = cloneCommitPush(authorisedUrl(), "perm-glob-wrong-owner");
        assertFalse(result.succeeded(), "push should be blocked — grant is for a different owner");
        assertFalse(
                result.output().contains("pending review"),
                "should be an auth denial, not a pending-review block. Output:\n" + result.output());
    }
}
