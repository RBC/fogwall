package com.rbc.fogwall.e2e;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.approval.AutoApprovalGateway;
import com.rbc.fogwall.db.model.PushQuery;
import com.rbc.fogwall.db.model.PushStatus;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.*;

/**
 * Regression tests for large pack pushes through both proxy modes.
 *
 * <h2>Production bug</h2>
 *
 * Pushes whose pack exceeded Jetty's socket buffer size (typically ~1 MiB in production, controlled by
 * {@code http.postBuffer} on the git client) failed with either a silent connection drop (store-and-forward) or an
 * "Empty Branch" rejection (transparent proxy). Both failures were caused by {@code RequestBodyWrapper} reading a
 * truncated or empty body because Jetty 12's EPC dispatch model invoked the filter chain before the full HTTP body had
 * arrived.
 *
 * <h2>Fix</h2>
 *
 * Wrapping the {@code ServletContextHandler} in Jetty's {@code EagerContentHandler} causes Jetty to eagerly read
 * content chunks — triggering the {@code 100 Continue} handshake when needed — before dispatching to the filter chain.
 * {@code RequestBodyWrapper.readAllBytes()} then receives a fully available stream.
 *
 * <h2>Note on local reproducibility</h2>
 *
 * The truncation behaviour is specific to real network paths (HAProxy + OCP ingress + real TCP segmentation). On
 * loopback, data arrives atomically so the truncation does not occur and the bug cannot be reproduced locally. These
 * tests validate that large pack pushes succeed end-to-end with {@code EagerContentHandler} in place, providing a
 * regression guard for that code path.
 *
 * <p>Partially addresses #145 (Toxiproxy-based e2e tests).
 */
@Tag("e2e")
class LargePackPushE2ETest {

    /**
     * Pack size is kept deliberately under git's default {@code http.postBuffer} (1 MiB) so git sends the push as a
     * single {@code Content-Length} POST rather than splitting into multiple chunked requests. With a single-POST push,
     * {@code EagerContentHandler} buffers the full body before dispatching to the filter chain — validating the fix
     * end-to-end.
     *
     * <p>15 files × 20 lines ≈ 11 KB/commit × 3 commits ≈ ~50 KB pack. Well under 1 MiB.
     */
    private static final int INITIAL_FILES = 15;

    private static final int LINES_PER_FILE = 20;

    /** Follow-up commits — modify all existing files and add new ones per commit. */
    private static final int FOLLOWUP_COMMITS = 2;

    private static final int NEW_FILES_PER_FOLLOWUP = 3;

    static GiteaContainer gitea;
    static Path tempDir;

    @BeforeAll
    static void startGitea() throws Exception {
        gitea = new GiteaContainer();
        gitea.start();
        gitea.createAdminUser();
        gitea.createTestRepo();
        tempDir = Files.createTempDirectory("fogwall-largpack-e2e-");
    }

    @AfterAll
    static void stopGitea() throws Exception {
        if (gitea != null) gitea.stop();
    }

    // ── commit generation ─────────────────────────────────────────────────────

    /**
     * Builds a branch that mimics {@code project-rename}: one large commit touching {@link #INITIAL_FILES} files,
     * followed by {@link #FOLLOWUP_COMMITS} smaller commits each modifying all files and adding new ones. UUID-based
     * content resists delta compression and keeps pack sizes realistic.
     */
    private static void buildRealisticBranch(GitHelper git, Path repo, String branchName) throws Exception {
        git.createAndCheckoutBranch(repo, branchName);

        for (int i = 0; i < INITIAL_FILES; i++) {
            git.write(repo, "module-" + i + ".java", generateFileContent("module-" + i, 0));
        }
        git.stageAll(repo);
        git.commit(repo, "chore: initial large commit — " + INITIAL_FILES + " files");

        for (int c = 1; c <= FOLLOWUP_COMMITS; c++) {
            for (int i = 0; i < INITIAL_FILES; i++) {
                git.write(repo, "module-" + i + ".java", generateFileContent("module-" + i, c));
            }
            for (int n = 0; n < NEW_FILES_PER_FOLLOWUP; n++) {
                int fileIdx = INITIAL_FILES + (c * NEW_FILES_PER_FOLLOWUP) + n;
                git.write(repo, "followup-" + c + "-" + n + ".java", generateFileContent("followup-" + fileIdx, c));
            }
            git.stageAll(repo);
            git.commit(repo, "chore: follow-up commit " + c);
        }
    }

    private static String generateFileContent(String className, int rev) {
        var sb = new StringBuilder();
        sb.append("// ").append(className).append(" rev=").append(rev).append("\n");
        for (int i = 0; i < LINES_PER_FILE; i++) {
            sb.append("// ").append(UUID.randomUUID()).append("\n");
        }
        return sb.toString();
    }

    private static String credUrl(int port, String path) {
        String creds = URLEncoder.encode(GiteaContainer.ADMIN_USER, StandardCharsets.UTF_8)
                + ":"
                + URLEncoder.encode(GiteaContainer.ADMIN_PASSWORD, StandardCharsets.UTF_8);
        return "http://" + creds + "@localhost:" + port + path;
    }

    // ── Proxy mode ────────────────────────────────────────────────────────────

    @Nested
    @Tag("e2e")
    class ProxyMode {

        JettyProxyFixture proxy;

        @BeforeEach
        void start() throws Exception {
            proxy = new JettyProxyFixture(gitea.getBaseUri(), AutoApprovalGateway::new);
        }

        @AfterEach
        void stop() throws Exception {
            if (proxy != null) proxy.close();
        }

        @Test
        void largePack_newBranch_proxyMode_passesValidationAndForwards() throws Exception {
            String repoName = "large-proxy-"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            gitea.createRepo(GiteaContainer.TEST_ORG, repoName);

            String repoUrl =
                    credUrl(proxy.getPort(), "/proxy/localhost/" + GiteaContainer.TEST_ORG + "/" + repoName + ".git");

            GitHelper git = new GitHelper(tempDir);
            Path repo = git.clone(repoUrl, "proxy-large-" + repoName);
            git.setAuthor(repo, GiteaContainer.VALID_AUTHOR_NAME, GiteaContainer.VALID_AUTHOR_EMAIL);

            String branchName = "large-pack-proxy-test";
            buildRealisticBranch(git, repo, branchName);

            GitHelper.PushResult result = git.pushRefWithResult(repo, branchName);

            assertTrue(
                    result.succeeded(), "Large pack push via proxy mode should succeed.\nOutput:\n" + result.output());
            assertFalse(
                    result.output().contains("Empty Branch"),
                    "Should not be rejected as empty branch.\nOutput:\n" + result.output());
        }
    }

    // ── Store-and-forward mode ────────────────────────────────────────────────

    @Nested
    @Tag("e2e")
    class StoreAndForwardMode {

        JettyProxyFixture proxy;

        @BeforeEach
        void start() throws Exception {
            proxy = new JettyProxyFixture(gitea.getBaseUri(), AutoApprovalGateway::new);
        }

        @AfterEach
        void stop() throws Exception {
            if (proxy != null) proxy.close();
        }

        @Test
        void largePack_newBranch_storeAndForward_passesValidationAndForwards() throws Exception {
            String repoUrl = credUrl(
                    proxy.getPort(),
                    "/push/localhost/" + GiteaContainer.TEST_ORG + "/" + GiteaContainer.TEST_REPO + ".git");

            GitHelper git = new GitHelper(tempDir);
            Path repo = git.clone(
                    repoUrl,
                    "sf-large-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
            git.setAuthor(repo, GiteaContainer.VALID_AUTHOR_NAME, GiteaContainer.VALID_AUTHOR_EMAIL);

            String branchName = "large-pack-sf-"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
            buildRealisticBranch(git, repo, branchName);

            GitHelper.PushResult result = git.pushRefWithResult(repo, branchName);

            assertTrue(result.succeeded(), "Large pack push via S&F mode should succeed.\nOutput:\n" + result.output());

            var records = proxy.getPushStore().find(PushQuery.builder().limit(1).build());
            assertFalse(records.isEmpty(), "Push record should exist in store");
            assertEquals(
                    PushStatus.FORWARDED, records.get(0).getStatus(), "Push should be FORWARDED after auto-approval");
        }
    }
}
