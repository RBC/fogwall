package com.rbc.fogwall.e2e;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.model.AccessRule;
import com.rbc.fogwall.db.model.FetchActivity;
import com.rbc.fogwall.db.model.FetchActivityQuery;
import com.rbc.fogwall.db.model.FetchRefusal;
import com.rbc.fogwall.db.model.MatchTarget;
import com.rbc.fogwall.db.model.MatchType;
import com.rbc.fogwall.git.ProxyMode;
import com.rbc.fogwall.git.UpstreamFailure;
import com.rbc.fogwall.servlet.filter.UrlRuleEvaluator;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.*;

/**
 * End-to-end tests for URL allow/deny rule enforcement through both the transparent proxy path ({@code /proxy/...}) and
 * the server mode path ({@code /push/...}).
 *
 * <p>Rule set mirrors {@code docker/fogwall-docker-default.yml}:
 *
 * <ul>
 *   <li>Allow slug {@code /test-owner/test-repo} — PUSH and FETCH
 *   <li>Allow owner glob {@code otherorg/*} — PUSH and FETCH
 *   <li>Deny slug {@code /otherorg/other-secret} — overrides the owner allow (deny wins)
 *   <li>Deny name glob {@code *-readonly} — PUSH only (fetch still allowed)
 *   <li>Deny name regex {@code (?i)(^|-)secret(-|$).*} — PUSH only
 * </ul>
 *
 * <p>Each major rule scenario is validated through both proxy and server modes to confirm the shared
 * {@link UrlRuleEvaluator} behaves identically in both.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UrlRuleE2ETest {

    static GiteaContainer gitea;
    static String adminToken;
    /** Proxy with URL rules configured, auto-approves clean pushes. */
    static JettyProxyFixture proxy;

    static Path tempDir;

    @BeforeAll
    static void startInfrastructure() throws Exception {
        gitea = new GiteaContainer();
        gitea.start();
        gitea.createAdminUser();
        adminToken = gitea.generateAdminPushToken();
        gitea.createTestRepo(); // creates test-owner/test-repo

        // Create additional repos needed for the deny-rule tests.
        // Blocked repos don't actually need to exist on Gitea — the deny fires before any upstream
        // contact — but we create them to keep the Gitea side consistent with the allow-rule tests.
        gitea.createRepo(GiteaContainer.TEST_ORG, "test-repo-readonly");
        gitea.createRepo(GiteaContainer.TEST_ORG, "secret-store");
        gitea.createOrg("otherorg");
        gitea.createRepo("otherorg", "allowed-repo");
        gitea.createRepo("otherorg", "other-secret");
        gitea.createRepo("otherorg", "private-repo", true);

        proxy = new JettyProxyFixture(gitea.getBaseUri(), buildRules());
        tempDir = Files.createTempDirectory("fogwall-urlrule-e2e-");
    }

    @AfterAll
    static void stopInfrastructure() throws Exception {
        if (proxy != null) proxy.close();
        if (gitea != null) gitea.stop();
    }

    /** Text of the discovery refusal for a repository no allow rule covers. */
    private static final String NOT_ALLOWED = "this repository is not in the allow list";

    /** Text of the discovery refusal for a repository a deny rule matches. */
    private static final String DENIED = "this repository has been explicitly blocked by an administrator";

    /**
     * The push was refused on discovery with fogwall's reason and the configured status. Anything else means the
     * request went on to the upstream: in server mode the GitServlet then fetched the refused repository and listed its
     * refs, and git refused the push itself as non-fast-forward.
     */
    private static void assertRefused(GitHelper.PushResult result, String reason) {
        assertFalse(result.succeeded(), result.output());
        assertTrue(result.output().contains("remote: Repository access denied: " + reason), result.output());
        assertTrue(result.output().contains("The requested URL returned error: 403"), result.output());
    }

    /** The fetch counts for one repository, mode and result, written out of memory first. */
    private static List<FetchActivity> fetchActivity(
            String owner, String repo, ProxyMode mode, FetchActivity.Result result) {
        return proxy
                .flushFetchActivity()
                .find(FetchActivityQuery.builder()
                        .owner(owner)
                        .repoName(repo)
                        .result(result)
                        .build())
                .stream()
                .filter(r -> r.getMode() == mode)
                .toList();
    }

    private static void assertCountedAllowed(String owner, String repo, ProxyMode mode) {
        List<FetchActivity> rows = fetchActivity(owner, repo, mode, FetchActivity.Result.ALLOWED);
        assertFalse(rows.isEmpty(), "an allowed clone through " + mode + " must be counted");
        assertNotNull(rows.getFirst().getRuleId(), "an allowed clone names the rule that allowed it");
    }

    private static void assertCountedDenied(String owner, String repo, ProxyMode mode) {
        List<FetchActivity> rows = fetchActivity(owner, repo, mode, FetchActivity.Result.BLOCKED);
        assertEquals(1, rows.size(), rows.toString());
        assertEquals(1, rows.getFirst().getFetchCount());
        assertEquals(FetchRefusal.DENY_RULE, rows.getFirst().getRefusal());
        assertNotNull(rows.getFirst().getRuleId());
    }

    // ── URL helpers ──────────────────────────────────────────────────────────

    private String proxyUrl(String org, String repo) {
        String creds = encode(GiteaContainer.ADMIN_USER) + ":" + encode(adminToken);
        // getPushBase/getProxyBase already have the correct host segment (e.g. /proxy/localhost)
        return proxy.getProxyBase().replace("http://", "http://" + creds + "@") + "/" + org + "/" + repo + ".git";
    }

    private String pushUrl(String org, String repo) {
        String creds = encode(GiteaContainer.ADMIN_USER) + ":" + encode(adminToken);
        return proxy.getPushBase().replace("http://", "http://" + creds + "@") + "/" + org + "/" + repo + ".git";
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** Clone → commit → push through the transparent proxy path. Returns true if push exited 0. */
    private boolean proxyPush(String suffix, String org, String repo, String message) throws Exception {
        return cloneCommitPush(proxyUrl(org, repo), suffix, message);
    }

    /** Clone → commit → push through the server mode path. Returns true if push exited 0. */
    private boolean sfPush(String suffix, String org, String repo, String message) throws Exception {
        return cloneCommitPush(pushUrl(org, repo), suffix, message);
    }

    private boolean cloneCommitPush(String url, String suffix, String message) throws Exception {
        GitHelper git = new GitHelper(tempDir);
        Path repoDir = git.clone(url, suffix);
        git.setAuthor(repoDir, GiteaContainer.VALID_AUTHOR_NAME, GiteaContainer.VALID_AUTHOR_EMAIL);
        git.writeAndStage(repoDir, "file.txt", message + " - " + Instant.now());
        git.commit(repoDir, message);
        return git.tryPush(repoDir);
    }

    /**
     * For push-blocked tests: clone directly from Gitea (bypassing the proxy), make a commit, then attempt the push via
     * the proxy. This avoids the clone failing when the URL rule also blocks FETCH on that repo.
     *
     * <p>For repos that don't exist on Gitea at all (e.g. unknown-org), use the allowed test repo as the clone source
     * and redirect the push remote to the target URL.
     */
    private GitHelper.PushResult cloneFromGiteaThenPushViaProxy(
            String cloneOrg, String cloneRepo, String pushProxyUrl, String suffix, String message) throws Exception {
        // Clone directly from Gitea — no proxy, no URL rule enforcement on clone
        String directUrl = gitea.getBaseUrl() + "/" + cloneOrg + "/" + cloneRepo + ".git";
        String directWithCreds = directUrl.replace(
                "http://", "http://" + encode(GiteaContainer.ADMIN_USER) + ":" + encode(adminToken) + "@");
        GitHelper git = new GitHelper(tempDir);
        Path repoDir = git.clone(directWithCreds, suffix);
        git.setAuthor(repoDir, GiteaContainer.VALID_AUTHOR_NAME, GiteaContainer.VALID_AUTHOR_EMAIL);
        git.writeAndStage(repoDir, "file.txt", message + " - " + Instant.now());
        git.commit(repoDir, message);
        // Set push remote to the proxy URL targeting the blocked/denied repo
        git.setRemoteUrl(repoDir, "origin", pushProxyUrl);
        return git.pushWithResult(repoDir);
    }

    // ── Allow rules ──────────────────────────────────────────────────────────

    @Test
    @Order(10)
    void proxy_allowedSlug_passes() throws Exception {
        assertTrue(
                proxyPush(
                        "proxy-allow-slug",
                        GiteaContainer.TEST_ORG,
                        GiteaContainer.TEST_REPO,
                        "feat: allowed by slug rule"),
                "push to /test-owner/test-repo should be allowed");
        assertCountedAllowed(GiteaContainer.TEST_ORG, GiteaContainer.TEST_REPO, ProxyMode.TRANSPARENT);
    }

    @Test
    @Order(11)
    void sf_allowedSlug_passes() throws Exception {
        assertTrue(
                sfPush(
                        "sf-allow-slug",
                        GiteaContainer.TEST_ORG,
                        GiteaContainer.TEST_REPO,
                        "feat: allowed by slug rule in server mode"),
                "push to /test-owner/test-repo should be allowed in server mode");
        assertCountedAllowed(GiteaContainer.TEST_ORG, GiteaContainer.TEST_REPO, ProxyMode.SERVER);
    }

    @Test
    @Order(12)
    void proxy_allowedOwnerGlob_passes() throws Exception {
        assertTrue(
                proxyPush("proxy-allow-glob", "otherorg", "allowed-repo", "feat: allowed by owner glob"),
                "push to otherorg/allowed-repo should be allowed by otherorg/* rule");
    }

    @Test
    @Order(13)
    void sf_allowedOwnerGlob_passes() throws Exception {
        assertTrue(
                sfPush("sf-allow-glob", "otherorg", "allowed-repo", "feat: allowed by owner glob in server mode"),
                "push to otherorg/allowed-repo should be allowed in server mode");
    }

    // ── Not in allow list ────────────────────────────────────────────────────

    @Test
    @Order(20)
    void proxy_repoNotInAllowList_blocked() throws Exception {
        // Clone from the allowed test repo, then redirect the push remote to an unconfigured org.
        // The proxy URL rule blocks before any upstream contact.
        var result = cloneFromGiteaThenPushViaProxy(
                GiteaContainer.TEST_ORG,
                GiteaContainer.TEST_REPO,
                proxyUrl("unknown-org", "some-repo"),
                "proxy-notallowed",
                "feat: this org has no allow rule");
        assertRefused(result, NOT_ALLOWED);
    }

    @Test
    @Order(21)
    void sf_repoNotInAllowList_blocked() throws Exception {
        var result = cloneFromGiteaThenPushViaProxy(
                GiteaContainer.TEST_ORG,
                GiteaContainer.TEST_REPO,
                pushUrl("unknown-org", "some-repo"),
                "sf-notallowed",
                "feat: this org has no allow rule");
        assertRefused(result, NOT_ALLOWED);
    }

    // ── Deny overrides allow ──────────────────────────────────────────────────

    @Test
    @Order(30)
    void proxy_denySlug_overridesOwnerAllowRule() throws Exception {
        // otherorg/* is allowed but /otherorg/other-secret is explicitly denied.
        // Clone from the allowed allowed-repo, then push to other-secret via the proxy.
        var result = cloneFromGiteaThenPushViaProxy(
                "otherorg",
                "allowed-repo",
                proxyUrl("otherorg", "other-secret"),
                "proxy-deny-slug",
                "feat: this repo is explicitly denied");
        assertRefused(result, DENIED);
    }

    @Test
    @Order(31)
    void sf_denySlug_overridesOwnerAllowRule() throws Exception {
        var result = cloneFromGiteaThenPushViaProxy(
                "otherorg",
                "allowed-repo",
                pushUrl("otherorg", "other-secret"),
                "sf-deny-slug",
                "feat: this repo is explicitly denied");
        assertRefused(result, DENIED);
    }

    // ── Deny by name glob (*-readonly, push only) ─────────────────────────────

    @Test
    @Order(40)
    void proxy_denyNameGlob_pushBlocked() throws Exception {
        // The *-readonly deny is PUSH-only — clone from the same repo (fetch is allowed), then push via proxy.
        var result = cloneFromGiteaThenPushViaProxy(
                GiteaContainer.TEST_ORG,
                "test-repo-readonly",
                proxyUrl(GiteaContainer.TEST_ORG, "test-repo-readonly"),
                "proxy-deny-glob",
                "feat: readonly repos should not accept pushes");
        assertRefused(result, DENIED);
    }

    @Test
    @Order(41)
    void sf_denyNameGlob_pushBlocked() throws Exception {
        var result = cloneFromGiteaThenPushViaProxy(
                GiteaContainer.TEST_ORG,
                "test-repo-readonly",
                pushUrl(GiteaContainer.TEST_ORG, "test-repo-readonly"),
                "sf-deny-glob",
                "feat: readonly repos should not accept pushes");
        assertRefused(result, DENIED);
    }

    // ── Deny by name regex (push only) ───────────────────────────────────────

    @Test
    @Order(50)
    void proxy_denyNameRegex_pushBlocked() throws Exception {
        var result = cloneFromGiteaThenPushViaProxy(
                GiteaContainer.TEST_ORG,
                "secret-store",
                proxyUrl(GiteaContainer.TEST_ORG, "secret-store"),
                "proxy-deny-regex",
                "feat: secret repos should not accept pushes");
        assertRefused(result, DENIED);
    }

    @Test
    @Order(51)
    void sf_denyNameRegex_pushBlocked() throws Exception {
        var result = cloneFromGiteaThenPushViaProxy(
                GiteaContainer.TEST_ORG,
                "secret-store",
                pushUrl(GiteaContainer.TEST_ORG, "secret-store"),
                "sf-deny-regex",
                "feat: secret repos should not accept pushes");
        assertRefused(result, DENIED);
    }

    // ── Fetch refused on discovery ────────────────────────────────────────────

    @Test
    @Order(60)
    void proxy_denySlug_cloneRefused() throws Exception {
        var result = new GitHelper(tempDir).cloneWithResult(proxyUrl("otherorg", "other-secret"), "proxy-clone-deny");
        assertRefused(result, DENIED);
        assertCountedDenied("otherorg", "other-secret", ProxyMode.TRANSPARENT);
    }

    /** A refused clone must not reach the upstream: the refusal comes before any ref is listed. */
    @Test
    @Order(61)
    void sf_denySlug_cloneRefused() throws Exception {
        var result = new GitHelper(tempDir).cloneWithResult(pushUrl("otherorg", "other-secret"), "sf-clone-deny");
        assertRefused(result, DENIED);
        assertCountedDenied("otherorg", "other-secret", ProxyMode.SERVER);
    }

    // ── Upstream failures, server mode ────────────────────────────────────────

    @Test
    @Order(70)
    void sf_missingUpstreamRepository_saysNotFoundUpstream() throws Exception {
        var result = new GitHelper(tempDir).cloneWithResult(pushUrl("otherorg", "does-not-exist"), "sf-missing");

        assertFalse(result.succeeded(), result.output());
        assertTrue(result.output().contains("remote: " + UpstreamFailure.NOT_FOUND_MESSAGE), result.output());
        assertTrue(result.output().contains("not found"), result.output());
    }

    /** A rejected token used to read as a missing repository; git now reports an authentication failure. */
    @Test
    @Order(71)
    void sf_rejectedCredential_saysAuthenticationFailed() throws Exception {
        String url = proxy.getPushBase()
                        .replace("http://", "http://" + encode(GiteaContainer.ADMIN_USER) + ":not-a-valid-token@")
                + "/otherorg/private-repo.git";

        var result = new GitHelper(tempDir).cloneWithResult(url, "sf-bad-token");

        assertFalse(result.succeeded(), result.output());
        assertTrue(result.output().contains("remote: " + UpstreamFailure.NOT_AUTHORIZED_MESSAGE), result.output());
        assertTrue(result.output().contains("Authentication failed"), result.output());
    }

    // ── Rule set — mirrors docker/fogwall-docker-default.yml ───────────────

    /**
     * Builds the URL rule set used by this test class.
     *
     * <p>Rule structure (matches the docker-default config):
     *
     * <ol>
     *   <li>DENY order=100: slug {@code /otherorg/other-secret} — BOTH
     *   <li>DENY order=101: name glob {@code *-readonly} — PUSH only
     *   <li>DENY order=102: name regex {@code (?i)(^|-)secret(-|$).*} — PUSH only
     *   <li>ALLOW order=110: slug {@code /test-owner/test-repo} — BOTH
     *   <li>ALLOW order=111: owner {@code otherorg} — BOTH
     * </ol>
     */
    private static List<AccessRule> buildRules() {
        return List.of(
                AccessRule.builder()
                        .ruleOrder(100)
                        .access(AccessRule.Access.DENY)
                        .operation(AccessRule.Operation.BOTH)
                        .target(MatchTarget.SLUG)
                        .value("/otherorg/other-secret")
                        .matchType(MatchType.LITERAL)
                        .build(),
                AccessRule.builder()
                        .ruleOrder(101)
                        .access(AccessRule.Access.DENY)
                        .operation(AccessRule.Operation.PUSH)
                        .target(MatchTarget.NAME)
                        .value("*-readonly")
                        .matchType(MatchType.GLOB)
                        .build(),
                AccessRule.builder()
                        .ruleOrder(102)
                        .access(AccessRule.Access.DENY)
                        .operation(AccessRule.Operation.PUSH)
                        .target(MatchTarget.NAME)
                        .value("(?i)(^|-)secret(-|$).*")
                        .matchType(MatchType.REGEX)
                        .build(),
                AccessRule.builder()
                        .ruleOrder(110)
                        .access(AccessRule.Access.ALLOW)
                        .operation(AccessRule.Operation.BOTH)
                        .target(MatchTarget.SLUG)
                        .value("/test-owner/test-repo")
                        .matchType(MatchType.LITERAL)
                        .build(),
                AccessRule.builder()
                        .ruleOrder(111)
                        .access(AccessRule.Access.ALLOW)
                        .operation(AccessRule.Operation.BOTH)
                        .target(MatchTarget.OWNER)
                        .value("otherorg")
                        .matchType(MatchType.GLOB)
                        .build());
    }
}
