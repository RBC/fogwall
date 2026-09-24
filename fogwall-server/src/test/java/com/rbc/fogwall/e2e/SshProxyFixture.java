package com.rbc.fogwall.e2e;

import com.rbc.fogwall.config.FogwallConfigLoader;
import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.UrlRuleRegistry;
import com.rbc.fogwall.jetty.FogwallJettyApplication;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Starts a real fogwall SSH server for {@code @Tag("e2e")} tests, through the same path production takes:
 * {@link FogwallConfigLoader} composes {@code fogwall-test-e2e.yml} with a generated override, and
 * {@link FogwallJettyApplication#start} does the assembly — including the SSH transport, via
 * {@code SshServerRegistrar}.
 *
 * <p>Two provider entries reach the same upstream Gitea over SSH: the primary routed by the upstream host:port, the
 * alias by {@link #ALIAS_PATH_SUFFIX}, so multi-provider SSH routing is exercised on one container. The test key is
 * registered in Gitea under {@link GiteaContainer#ADMIN_USER}; the proxy user's SCM identity is linked to that login on
 * both entries so the enricher can verify the connecting fingerprint against ADMIN_USER's Gitea keys. Approval is auto,
 * so a clean push goes straight to FORWARDED.
 *
 * <p>Trust-on-first-use pins whatever SSH host key the container presents on the first upstream connect, since the
 * Gitea test container regenerates its host key on each start and it cannot be pinned ahead of time.
 *
 * <pre>{@code
 * proxy = new SshProxyFixture(gitea, pubKeyLine, adminToken);
 * // push via: proxy.pushUrl(owner, repo)
 * }</pre>
 */
class SshProxyFixture implements AutoCloseable {

    /** Proxy username the pre-seeded SSH test key resolves to. */
    static final String TEST_USER = "ssh-test-user";

    /** The two provider entries; the alias is a second route to the same Gitea for the multi-provider routing test. */
    private static final String PRIMARY_PROVIDER = "gitea-ssh-e2e";

    private static final String ALIAS_PROVIDER = "gitea-ssh-e2e-alias";

    /** Path suffix the alias entry is routed under; the primary is routed by the upstream host:port. */
    private static final String ALIAS_PATH_SUFFIX = "/gitea-alias";

    private final FogwallJettyApplication.Running running;
    private final int sshPort;
    private final String giteaSshHostPort;

    /**
     * @param gitea running Gitea container (SSH port must be exposed)
     * @param publicKeyLine OpenSSH authorized_keys line for the test identity
     * @param giteaApiToken token the enricher uses to list the upstream login's keys (Gitea requires sign-in to view)
     */
    SshProxyFixture(GiteaContainer gitea, String publicKeyLine, String giteaApiToken) throws Exception {
        URI giteaSshUri = gitea.getSshUri();
        this.giteaSshHostPort = giteaSshUri.getHost() + ":" + giteaSshUri.getPort();
        this.sshPort = findFreePort();

        Path hostKey = Files.createTempDirectory("fogwall-ssh-e2e-hostkey-").resolve("host_key");
        Path override = writeOverride(gitea, giteaSshUri, publicKeyLine, giteaApiToken, hostKey);
        try {
            running = FogwallJettyApplication.start(FogwallConfigLoader.loadLayers("test-e2e", List.of(override)));
        } finally {
            Files.deleteIfExists(override);
        }
    }

    /** The half of the configuration that only exists at runtime: the SSH port, the container, and the test key. */
    private Path writeOverride(
            GiteaContainer gitea, URI giteaSshUri, String publicKeyLine, String apiToken, Path hostKey)
            throws IOException {
        String yaml = "server:\n"
                + "  approval-mode: auto\n"
                + "  ssh:\n"
                + "    enabled: true\n"
                + "    port: " + sshPort + "\n"
                + "    host-key-path: \"" + hostKey + "\"\n"
                + "    trust-on-first-use: true\n"
                + "providers:\n"
                + providerBlock(PRIMARY_PROVIDER, gitea.getBaseUrl(), apiToken, "/" + giteaSshHostPort, giteaSshUri)
                + providerBlock(ALIAS_PROVIDER, gitea.getBaseUrl(), apiToken, ALIAS_PATH_SUFFIX, giteaSshUri)
                + "users:\n"
                + "  - username: " + TEST_USER + "\n"
                + "    emails:\n"
                + "      - " + GiteaContainer.VALID_AUTHOR_EMAIL + "\n"
                + "    scm-identities:\n"
                + "      - provider: " + PRIMARY_PROVIDER + "\n"
                + "        username: " + GiteaContainer.ADMIN_USER + "\n"
                + "      - provider: " + ALIAS_PROVIDER + "\n"
                + "        username: " + GiteaContainer.ADMIN_USER + "\n"
                + "    ssh-keys:\n"
                + "      - public-key: \"" + publicKeyLine + "\"\n"
                + "rules:\n"
                + "  allow:\n"
                + "    - enabled: true\n"
                + "      order: 1\n"
                + "      operation: BOTH\n"
                + "      match:\n"
                + "        target: OWNER\n"
                + "        value: \"*\"\n"
                + "        type: GLOB\n"
                + "permissions:\n"
                + grant(PRIMARY_PROVIDER)
                + grant(ALIAS_PROVIDER);

        Path file = Files.createTempFile("fogwall-ssh-e2e-override-", ".yml");
        Files.writeString(file, yaml);
        return file;
    }

    private static String providerBlock(String name, String httpUri, String apiToken, String pathSuffix, URI sshUri) {
        return "  " + name + ":\n"
                + "    enabled: true\n"
                + "    type: forgejo\n"
                + "    uri: " + httpUri + "\n"
                + "    api-token: " + apiToken + "\n"
                + "    path-suffix: \"" + pathSuffix + "\"\n"
                + "    ssh:\n"
                + "      uri: " + sshUri + "\n";
    }

    private static String grant(String provider) {
        return "  - username: " + TEST_USER + "\n"
                + "    provider: " + provider + "\n"
                + "    match:\n"
                + "      target: SLUG\n"
                + "      value: \".*\"\n"
                + "      type: REGEX\n"
                + "    grant: PUSH\n";
    }

    /** The host port the fogwall SSH server is listening on. */
    int getSshPort() {
        return sshPort;
    }

    /** The push URL for a repository, targeting the primary (host:port-routed) provider entry. */
    String pushUrl(String owner, String repo) {
        return "ssh://localhost:" + sshPort + "/" + giteaSshHostPort + "/" + owner + "/" + repo + ".git";
    }

    /** The push URL for a repository, targeting the alias ({@link #ALIAS_PATH_SUFFIX}-routed) provider entry. */
    String aliasPushUrl(String owner, String repo) {
        return "ssh://localhost:" + sshPort + ALIAS_PATH_SUFFIX + "/" + owner + "/" + repo + ".git";
    }

    PushStore getPushStore() {
        return running.ctx().pushStore();
    }

    UrlRuleRegistry getUrlRuleRegistry() {
        return running.ctx().urlRuleRegistry();
    }

    @Override
    public void close() throws Exception {
        running.close();
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            s.setReuseAddress(true);
            return s.getLocalPort();
        }
    }
}
