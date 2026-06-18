package org.finos.gitproxy.e2e;

import static org.junit.jupiter.api.Assertions.*;

import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.finos.gitproxy.approval.AutoApprovalGateway;
import org.finos.gitproxy.db.model.PushQuery;
import org.finos.gitproxy.db.model.PushStatus;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Regression tests for large pack pushes through both proxy modes, using Toxiproxy to throttle
 * upstream bandwidth so that Jetty receives pack data in small chunks.
 *
 * <p>Regression for: {@code RequestBodyWrapper} wrapped Jetty 12's {@code HttpInput} in a
 * {@link java.io.BufferedInputStream}. When the socket buffer was temporarily exhausted mid-body,
 * {@code BufferedInputStream} permanently cached the -1 return — truncating the body even though
 * more data was still incoming. The fix replaces the manual read loop with
 * {@code InputStream.readAllBytes()}, which reads directly from the servlet input stream without
 * an intermediate buffer layer.
 *
 * <p>Toxiproxy throttles the upstream connection (git client → Jetty) to 32 KB/s. A ~200 KB pack
 * takes ~6 seconds to arrive, forcing Jetty to receive it across multiple TCP segments. This
 * reliably triggers the {@code BufferedInputStream} premature-EOF bug.
 *
 * <p>Closes #145.
 */
@Tag("e2e")
class LargePackPushE2ETest {

    /**
     * Upstream bandwidth limit applied by Toxiproxy in KB/s. Low enough to force multi-segment
     * delivery of a ~200 KB pack, high enough to keep test runtime under 30s.
     */
    private static final long UPSTREAM_BANDWIDTH_KB_S = 32;

    /** Number of files per commit. 50 × ~4 KB ≈ 200 KB uncompressed. */
    private static final int FILE_COUNT = 50;

    /** Lines per file — UUID lines are ~37 chars; 110 lines ≈ 4 KB. */
    private static final int LINES_PER_FILE = 110;

    /** Data port Toxiproxy listens on for the proxy traffic. Fixed so we can expose it upfront. */
    private static final int TOXI_DATA_PORT = 8475;

    @SuppressWarnings("resource")
    private static final GenericContainer<?> TOXIPROXY =
            new GenericContainer<>(DockerImageName.parse("ghcr.io/shopify/toxiproxy:2.9.0"))
                    .withExposedPorts(8474, TOXI_DATA_PORT)
                    .withExtraHost("host.testcontainers.internal", "host-gateway");

    static GiteaContainer gitea;
    static Path tempDir;

    @BeforeAll
    static void startInfrastructure() throws Exception {
        gitea = new GiteaContainer();
        gitea.start();
        gitea.createAdminUser();
        gitea.createTestRepo();
        TOXIPROXY.start();
        tempDir = Files.createTempDirectory("git-proxy-java-largpack-e2e-");
    }

    @AfterAll
    static void stopInfrastructure() throws Exception {
        TOXIPROXY.stop();
        if (gitea != null) gitea.stop();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static void writeLargeCommit(GitHelper git, Path repoDir) throws Exception {
        for (int i = 0; i < FILE_COUNT; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < LINES_PER_FILE; j++) {
                sb.append(UUID.randomUUID()).append('\n');
            }
            git.writeAndStage(repoDir, "large-file-" + i + ".txt", sb.toString());
        }
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
        ToxiproxyClient toxiClient;
        Proxy toxiProxy;

        @BeforeEach
        void startProxy() throws Exception {
            proxy = new JettyProxyFixture(gitea.getBaseUri(), AutoApprovalGateway::new);
            toxiClient = new ToxiproxyClient(
                    TOXIPROXY.getHost(), TOXIPROXY.getMappedPort(8474));
        }

        @AfterEach
        void stopProxy() throws Exception {
            if (toxiProxy != null) toxiProxy.delete();
            if (proxy != null) proxy.close();
        }

        @Test
        void largePack_newBranch_throttledUpstream_proxyMode_passesValidation() throws Exception {
            String repoName = "large-proxy-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            gitea.createRepo(GiteaContainer.TEST_ORG, repoName);

            toxiProxy = toxiClient.createProxy("proxy-" + repoName,
                    "0.0.0.0:" + TOXI_DATA_PORT,
                    "host.testcontainers.internal:" + proxy.getPort());
            toxiProxy.toxics().bandwidth("bw-upstream", ToxicDirection.UPSTREAM, UPSTREAM_BANDWIDTH_KB_S);
            int throttledPort = TOXIPROXY.getMappedPort(TOXI_DATA_PORT);

            String repoUrl = credUrl(throttledPort,
                    "/proxy/localhost/" + GiteaContainer.TEST_ORG + "/" + repoName + ".git");

            GitHelper git = new GitHelper(tempDir);
            Path repo = git.clone(repoUrl, "proxy-large-" + repoName);
            git.setAuthor(repo, GiteaContainer.VALID_AUTHOR_NAME, GiteaContainer.VALID_AUTHOR_EMAIL);
            git.createAndCheckoutBranch(repo, "large-pack-test");
            writeLargeCommit(git, repo);
            git.commit(repo, "feat: large pack regression test via proxy mode");

            GitHelper.PushResult result = git.pushWithResult(repo);

            assertTrue(result.succeeded(),
                    "Large pack push via throttled proxy mode should succeed. Output:\n" + result.output());
            assertFalse(result.output().contains("Empty Branch"),
                    "Should not be rejected as empty branch");
        }
    }

    // ── Store-and-forward mode ────────────────────────────────────────────────

    @Nested
    @Tag("e2e")
    class StoreAndForwardMode {

        JettyProxyFixture proxy;
        ToxiproxyClient toxiClient;
        Proxy toxiProxy;

        @BeforeEach
        void startProxy() throws Exception {
            proxy = new JettyProxyFixture(gitea.getBaseUri(), AutoApprovalGateway::new);
            toxiClient = new ToxiproxyClient(
                    TOXIPROXY.getHost(), TOXIPROXY.getMappedPort(8474));
        }

        @AfterEach
        void stopProxy() throws Exception {
            if (toxiProxy != null) toxiProxy.delete();
            if (proxy != null) proxy.close();
        }

        @Test
        void largePack_newBranch_throttledUpstream_storeAndForward_passesValidationAndForwards()
                throws Exception {
            toxiProxy = toxiClient.createProxy("sf-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                    "0.0.0.0:" + TOXI_DATA_PORT,
                    "host.testcontainers.internal:" + proxy.getPort());
            toxiProxy.toxics().bandwidth("bw-upstream", ToxicDirection.UPSTREAM, UPSTREAM_BANDWIDTH_KB_S);
            int throttledPort = TOXIPROXY.getMappedPort(TOXI_DATA_PORT);

            String repoUrl = credUrl(throttledPort,
                    "/push/localhost/" + GiteaContainer.TEST_ORG + "/" + GiteaContainer.TEST_REPO + ".git");

            GitHelper git = new GitHelper(tempDir);
            Path repo = git.clone(repoUrl,
                    "sf-large-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
            git.setAuthor(repo, GiteaContainer.VALID_AUTHOR_NAME, GiteaContainer.VALID_AUTHOR_EMAIL);
            git.createAndCheckoutBranch(repo,
                    "large-pack-sf-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6));
            writeLargeCommit(git, repo);
            git.commit(repo, "feat: large pack regression test via S&F mode");

            GitHelper.PushResult result = git.pushWithResult(repo);

            assertTrue(result.succeeded(),
                    "Large pack push via throttled S&F mode should succeed. Output:\n" + result.output());
            assertFalse(result.output().contains("Empty Branch"),
                    "Should not be rejected as empty branch");

            var records = proxy.getPushStore()
                    .find(PushQuery.builder().limit(1).build());
            assertFalse(records.isEmpty(), "Push record should exist in store");
            assertEquals(PushStatus.FORWARDED, records.get(0).getStatus(),
                    "Push should be FORWARDED after auto-approval");
        }
    }
}
