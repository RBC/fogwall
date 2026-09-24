package com.rbc.fogwall.e2e;

import com.rbc.fogwall.config.FogwallConfigLoader;
import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.jetty.FogwallJettyApplication;
import com.rbc.fogwall.jetty.FogwallServletRegistrar;
import com.rbc.fogwall.jetty.reload.LiveConfigLoader;
import com.rbc.fogwall.jetty.reload.LiveConfigLoader.Section;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * A {@link JettyProxyFixture} with a reload source attached, for tests that change configuration mid-run and assert the
 * next request sees it.
 *
 * <p>Reloads go through the production {@link LiveConfigLoader}: the fixture points {@code reload.file.path} at a file
 * it owns, a test writes YAML into it, and {@link #reloadSection} triggers the same call the {@code POST
 * /api/config/reload} endpoint makes. Nothing here decides which config section a change belongs to — that is the
 * behaviour under test.
 *
 * <p>Auto-approve, so an allowed push completes without waiting on a review step.
 */
class HotReloadJettyFixture implements AutoCloseable {

    private static final String PROVIDER_NAME = "gitea";

    private final FogwallJettyApplication.Running running;
    private final String providerId;
    private final String giteaHostPort;
    private final Path reloadFile;

    /** Reloads come from a file this fixture owns. */
    HotReloadJettyFixture(URI giteaUri) throws Exception {
        this(giteaUri, null);
    }

    /**
     * Reloads come from a git repository, cloned on every reload — the source an operator points at a config repo. Pass
     * the clone URL of a repository holding {@code fogwall.yml} on its default branch.
     */
    HotReloadJettyFixture(URI giteaUri, String configRepoUrl) throws Exception {
        this.giteaHostPort = giteaUri.getHost() + ":" + giteaUri.getPort();
        this.reloadFile = Files.createTempFile("fogwall-e2e-reload-", ".yml");
        // An empty document: the loader warns if the watched file is missing, and a reload before any test has
        // written to it should change nothing rather than fail.
        Files.writeString(reloadFile, "{}\n");

        Path override = writeOverride(giteaUri, reloadFile, configRepoUrl);
        try {
            running = FogwallJettyApplication.start(FogwallConfigLoader.loadLayers("test-e2e", List.of(override)));
        } finally {
            Files.deleteIfExists(override);
        }
        this.providerId = running.providers().getFirst().getProviderId();
    }

    private static Path writeOverride(URI giteaUri, Path reloadFile, String configRepoUrl) throws IOException {
        // Only one source at a time: reload() takes the file source when it is enabled and never reaches the git one.
        String source = configRepoUrl == null ? """
                reload:
                  file:
                    enabled: true
                    path: %s
                """.formatted(reloadFile) : """
                reload:
                  file:
                    enabled: false
                  git:
                    enabled: true
                    url: %s
                    branch: main
                    file-path: fogwall.yml
                    interval-seconds: 0
                """.formatted(configRepoUrl);

        String yaml = """
                server:
                  approval-mode: auto
                providers:
                  %s:
                    enabled: true
                    type: forgejo
                    uri: %s
                %srules:
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
                      - provider: %s
                        username: %s
                permissions:
                  - username: %s
                    provider: %s
                    match:
                      target: SLUG
                      value: ".*"
                      type: REGEX
                    grant: MAINTAIN
                """.formatted(
                        PROVIDER_NAME,
                        giteaUri,
                        source,
                        GiteaContainer.ADMIN_USER,
                        GiteaContainer.VALID_AUTHOR_EMAIL,
                        PROVIDER_NAME,
                        GiteaContainer.ADMIN_USER,
                        GiteaContainer.ADMIN_USER,
                        PROVIDER_NAME);

        Path file = Files.createTempFile("fogwall-e2e-override-", ".yml");
        Files.writeString(file, yaml);
        return file;
    }

    /**
     * Applies {@code overrideYaml} to the running server by writing it to the watched reload file and triggering the
     * section reload, exactly as the REST endpoint does.
     */
    void reloadSection(Path overrideYaml, Section section) throws Exception {
        Files.writeString(reloadFile, Files.readString(overrideYaml));
        running.liveConfigLoader().reload(section);
    }

    /** Triggers a reload from whichever source is configured, without staging a file first. */
    String reloadSection(Section section) {
        return running.liveConfigLoader().reload(section);
    }

    int getPort() {
        return running.port();
    }

    PushStore getPushStore() {
        return running.ctx().pushStore();
    }

    String getProviderId() {
        return providerId;
    }

    String getGiteaHostPort() {
        return giteaHostPort;
    }

    String getPushBase() {
        return "http://localhost:" + getPort() + FogwallServletRegistrar.PUSH_PATH_PREFIX + "/" + giteaHostPort;
    }

    String getProxyBase() {
        return "http://localhost:" + getPort() + FogwallServletRegistrar.PROXY_PATH_PREFIX + "/" + giteaHostPort;
    }

    @Override
    public void close() throws Exception {
        try {
            running.close();
        } finally {
            Files.deleteIfExists(reloadFile);
        }
    }
}
