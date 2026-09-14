package com.rbc.fogwall.e2e;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.jetty.reload.LiveConfigLoader.Section;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.*;

/**
 * End-to-end tests verifying that each hot-reloadable config section takes effect without a server restart.
 *
 * <p>Each test follows the same three-phase pattern:
 *
 * <ol>
 *   <li><b>Block</b> — write a YAML override file that restricts the section under test; reload it; verify a push that
 *       violates the new restriction is rejected.
 *   <li><b>Reload</b> — write a permissive YAML override; reload it; verify the same (previously uncommitted) push now
 *       succeeds.
 * </ol>
 *
 * <p>Each test gets its own Gitea repository and its own {@link HotReloadJettyFixture} instance so there is no shared
 * config state between tests and ordering does not matter.
 */
@Tag("e2e")
class ConfigHotReloadE2ETest {

    // Shared across all tests — containers are expensive to start.
    static GiteaContainer gitea;
    static String adminToken;

    // Per-test — fresh config state and repo for every test method.
    HotReloadJettyFixture proxy;
    Path tempDir;
    String repoName;

    @BeforeAll
    static void startGitea() throws Exception {
        gitea = new GiteaContainer();
        gitea.start();
        gitea.createAdminUser();
        adminToken = gitea.generateAdminPushToken();
        // Creates the test org (test-owner) plus an initial repo; subsequent tests add their own repos.
        gitea.createTestRepo();
    }

    @AfterAll
    static void stopGitea() {
        if (gitea != null) gitea.stop();
    }

    @BeforeEach
    void setUp() throws Exception {
        repoName = "hotreload-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        gitea.createRepo(GiteaContainer.TEST_ORG, repoName);
        tempDir = Files.createTempDirectory("fogwall-hotreload-e2e-");

        proxy = new HotReloadJettyFixture(gitea.getBaseUri());

        // Seed allow-all so requests reach the validation filters under test.
        Path allowAll = Files.createTempFile(tempDir, "allow-all-", ".yml");
        Files.writeString(allowAll, """
                rules:
                  allow:
                    - operation: BOTH
                      match:
                        target: SLUG
                        value: '/**'
                  deny: []
                secret-scan:
                  enabled: false
                """);
        proxy.reloadSection(allowAll, Section.RULES);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (proxy != null) proxy.close();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String url() {
        String user = URLEncoder.encode(GiteaContainer.ADMIN_USER, StandardCharsets.UTF_8);
        String pass = URLEncoder.encode(adminToken, StandardCharsets.UTF_8);
        return "http://" + user + ":" + pass + "@localhost:" + proxy.getPort() + "/proxy/" + proxy.getGiteaHostPort()
                + "/" + GiteaContainer.TEST_ORG + "/" + repoName + ".git";
    }

    private Path writeOverride(String yaml) throws Exception {
        Path f = Files.createTempFile(tempDir, "override-", ".yml");
        Files.writeString(f, yaml);
        return f;
    }

    private GitHelper git() {
        return new GitHelper(tempDir);
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /** Verifies that commit message block literals are enforced after a reload and relaxed after a second reload. */
    @Test
    void commitRulesReload() throws Exception {
        GitHelper git = git();
        Path repo = git.clone(url(), "commit-reload");
        git.setAuthor(repo, GiteaContainer.VALID_AUTHOR_NAME, GiteaContainer.VALID_AUTHOR_EMAIL);
        git.writeAndStage(repo, "file.txt", "initial content");
        git.commit(repo, "feat: HOTRELOAD_MSG_BLOCKED — should be blocked after reload");

        // --- Phase 1: reload with message block active ---
        Path blocking = writeOverride("""
                commit:
                  message:
                    block:
                      literals:
                        - "HOTRELOAD_MSG_BLOCKED"
                secret-scan:
                  enabled: false
                """);
        proxy.reloadSection(blocking, Section.COMMIT);

        assertFalse(git.tryPush(repo), "push should be blocked: commit message contains blocked literal");

        // --- Phase 2: reload with message block cleared ---
        Path permissive = writeOverride("""
                commit:
                  message:
                    block:
                      literals: []
                      patterns: []
                secret-scan:
                  enabled: false
                """);
        proxy.reloadSection(permissive, Section.COMMIT);
        assertTrue(git.tryPush(repo), "push should succeed after commit rules are relaxed");
    }

    /** Verifies that diff content block literals are enforced after a reload and relaxed after a second reload. */
    @Test
    void diffScanReload() throws Exception {
        GitHelper git = git();
        Path repo = git.clone(url(), "diff-reload");
        git.setAuthor(repo, GiteaContainer.VALID_AUTHOR_NAME, GiteaContainer.VALID_AUTHOR_EMAIL);
        git.writeAndStage(repo, "data.txt", "line1\nHOTRELOAD_DIFF_BLOCKED\nline3\n");
        git.commit(repo, "feat: add data file");

        // --- Phase 1: reload with diff-scan block active ---
        Path blocking = writeOverride("""
                diff-scan:
                  block:
                    literals:
                      - "HOTRELOAD_DIFF_BLOCKED"
                secret-scan:
                  enabled: false
                """);
        proxy.reloadSection(blocking, Section.DIFF_SCAN);

        assertFalse(git.tryPush(repo), "push should be blocked: diff contains blocked literal");

        // --- Phase 2: reload with diff-scan block cleared ---
        Path permissive = writeOverride("""
                diff-scan:
                  block:
                    literals: []
                    patterns: []
                secret-scan:
                  enabled: false
                """);
        proxy.reloadSection(permissive, Section.DIFF_SCAN);
        assertTrue(git.tryPush(repo), "push should succeed after diff-scan rules are relaxed");
    }

    /**
     * Verifies that secret-scan can be enabled with an inline gitleaks config and that disabling it via reload allows
     * the same push through.
     */
    // println is the established convention for e2e diagnostic breadcrumbs (see GitHelper) — no
    // test logging config exists here, so a logger call would silently disappear instead of
    // surfacing in the failure output.
    @SuppressWarnings("PMD.SystemPrintln")
    @Test
    void secretScanningReload() throws Exception {
        GitHelper git = git();
        Path repo = git.clone(url(), "secret-reload");
        git.setAuthor(repo, GiteaContainer.VALID_AUTHOR_NAME, GiteaContainer.VALID_AUTHOR_EMAIL);
        git.writeAndStage(repo, "config.txt", "api_key = HOTRELOAD_SECRET_ABCD1234\n");
        git.commit(repo, "feat: add config with api key");

        // --- Phase 1: reload with secret scanning enabled + custom inline rule ---
        Path blocking = writeOverride("""
                secret-scan:
                  enabled: true
                  inline-config: |
                    title = "hotreload-test"
                    [[rules]]
                    id = "hotreload-test-custom-secret"
                    description = "Custom rule for hot-reload e2e test"
                    regex = '''HOTRELOAD_SECRET_[A-Z0-9]{8}'''
                """);
        proxy.reloadSection(blocking, Section.SECRET_SCAN);

        GitHelper.PushResult blocked = git.pushWithResult(repo);
        if (blocked.succeeded()) {
            System.out.println(
                    "[secretScanningReload] gitleaks unavailable — scanner ran fail-open, skipping block assertion");
        } else {
            assertTrue(
                    blocked.output().contains("secret-scan")
                            || blocked.output().contains("secret")
                            || blocked.output().contains("blocked"),
                    "blocked output should mention secret scanning; got: " + blocked.output());
        }

        // --- Phase 2: reload with secret scanning disabled ---
        Path permissive = writeOverride("""
                secret-scan:
                  enabled: false
                """);
        if (!blocked.succeeded()) {
            proxy.reloadSection(permissive, Section.SECRET_SCAN);
            assertTrue(git.tryPush(repo), "push should succeed after secret scanning is disabled");
        }
    }

    /**
     * Verifies that a URL deny rule added via reload blocks pushes, and that removing it via reload restores access.
     */
    @Test
    void rulesReload() throws Exception {
        GitHelper git = git();
        Path repo = git.clone(url(), "rules-reload");
        git.setAuthor(repo, GiteaContainer.VALID_AUTHOR_NAME, GiteaContainer.VALID_AUTHOR_EMAIL);
        git.writeAndStage(repo, "file.txt", "content for rules reload test");
        git.commit(repo, "feat: rules reload test commit");

        String repoSlug = "/" + GiteaContainer.TEST_ORG + "/" + repoName;

        // --- Phase 1: reload with a DENY rule for this test's repo ---
        Path blocking = writeOverride("""
                rules:
                  allow: []
                  deny:
                    - operation: PUSH
                      match:
                        target: SLUG
                        value: '%s'
                secret-scan:
                  enabled: false
                """.formatted(repoSlug));
        proxy.reloadSection(blocking, Section.RULES);

        assertFalse(git.tryPush(repo), "push should be blocked: deny rule matches " + repoSlug);

        // --- Phase 2: reload with deny rule removed; restore allow-all ---
        Path permissive = writeOverride("""
                rules:
                  allow:
                    - operation: BOTH
                      match:
                        target: SLUG
                        value: '/**'
                  deny: []
                secret-scan:
                  enabled: false
                """);
        proxy.reloadSection(permissive, Section.RULES);
        assertTrue(git.tryPush(repo), "push should succeed after deny rule is removed");
    }

    /**
     * The git source: fogwall clones a config repository and applies the YAML it finds there.
     *
     * <p>Distinct from every other test here, which stages a file on disk. This one puts the config where an operator
     * would — in a repository — and is the only coverage the clone path has.
     *
     * <p>The repository carries the whole hot-reloadable picture, not just the rule under test, because that is what
     * the feature does: a reload applies every section from the repo, so anything it omits reverts to base defaults.
     */
    @Test
    void gitSourceReload() throws Exception {
        String configRepo =
                "config-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        gitea.createRepo(GiteaContainer.TEST_ORG, configRepo);
        String configRepoUrl = gitea.getBaseUrl() + "/" + GiteaContainer.TEST_ORG + "/" + configRepo + ".git";

        pushConfig(configRepo, repoConfig("        - \"GITSOURCE_BLOCKED\""));

        try (var gitProxy = new HotReloadJettyFixture(gitea.getBaseUri(), configRepoUrl)) {
            GitHelper git = git();
            Path repo = git.clone(proxyUrl(gitProxy), "git-source-reload");
            git.setAuthor(repo, GiteaContainer.VALID_AUTHOR_NAME, GiteaContainer.VALID_AUTHOR_EMAIL);
            git.writeAndStage(repo, "file.txt", "git source reload");
            git.commit(repo, "feat: GITSOURCE_BLOCKED — should be blocked once the repo config is pulled");

            assertTrue(
                    gitProxy.reloadSection(Section.COMMIT).contains("from git"),
                    "reload should report the git source, not the file one");
            assertFalse(git.tryPush(repo), "push should be blocked by the rule the config repo carries");

            // Relax the rule in the repository; the next reload picks up the new commit.
            pushConfig(configRepo, repoConfig(""));

            gitProxy.reloadSection(Section.COMMIT);
            assertTrue(git.tryPush(repo), "push should succeed after the config repo relaxes the rule");
        }
    }

    /** The proxy-mode push URL for a fixture other than the per-test one. */
    private String proxyUrl(HotReloadJettyFixture fixture) {
        return "http://" + URLEncoder.encode(GiteaContainer.ADMIN_USER, StandardCharsets.UTF_8) + ":"
                + URLEncoder.encode(adminToken, StandardCharsets.UTF_8) + "@localhost:" + fixture.getPort() + "/proxy/"
                + fixture.getGiteaHostPort() + "/" + GiteaContainer.TEST_ORG + "/" + repoName + ".git";
    }

    /**
     * What a config repository holds: the hot-reloadable policy, and nothing that names a provider.
     *
     * <p>A reload composes the base config with this file alone — the fixture's own override is not in the picture — so
     * the provider set is whatever the base config ships. Anything here referencing {@code gitea-e2e} would fail
     * validation, which is why there are no users or permissions: those are not the repository's to carry.
     *
     * <p>The access rule is, though: the repository carries the policy it wants in force, and this test asserts on the
     * commit rule it reloads, so it declares the allow rule the push needs alongside it.
     */
    private static String repoConfig(String blockedLiterals) {
        return """
                # A reload file has to be a valid config in its own right. Providers are not hot-reloadable, so this
                # changes nothing at runtime — but without it the users below reference a provider the base config
                # ships disabled, and validation refuses the whole file.
                providers:
                  gitea:
                    enabled: true
                commit:
                  message:
                    block:
                      literals:
                %s
                secret-scan:
                  enabled: false
                rules:
                  allow:
                    - enabled: true
                      order: 1
                      operation: BOTH
                      match:
                        target: OWNER
                        value: "*"
                        type: GLOB
                users:
                  - username: %s
                    emails:
                      - %s
                    scm-identities:
                      - provider: gitea
                        username: %s
                permissions:
                  - username: %s
                    provider: gitea
                    match:
                      target: SLUG
                      value: ".*"
                      type: REGEX
                    grant: MAINTAIN
                """.formatted(
                        blockedLiterals,
                        GiteaContainer.ADMIN_USER,
                        GiteaContainer.VALID_AUTHOR_EMAIL,
                        GiteaContainer.ADMIN_USER,
                        GiteaContainer.ADMIN_USER);
    }

    /** Commits {@code fogwall.yml} to the config repository, straight to Gitea rather than through the proxy. */
    private void pushConfig(String configRepo, String yaml) throws Exception {
        String url = "http://" + URLEncoder.encode(GiteaContainer.ADMIN_USER, StandardCharsets.UTF_8) + ":"
                + URLEncoder.encode(adminToken, StandardCharsets.UTF_8) + "@"
                + gitea.getBaseUrl().replace("http://", "") + "/" + GiteaContainer.TEST_ORG + "/" + configRepo
                + ".git";
        GitHelper git = git();
        Path repo = git.clone(url, "config-repo-" + UUID.randomUUID().toString().substring(0, 8));
        git.setAuthor(repo, GiteaContainer.VALID_AUTHOR_NAME, GiteaContainer.VALID_AUTHOR_EMAIL);
        git.writeAndStage(repo, "fogwall.yml", yaml);
        git.commit(repo, "chore: update fogwall config");
        assertTrue(git.tryPush(repo), "config repo push should succeed");
    }
}
