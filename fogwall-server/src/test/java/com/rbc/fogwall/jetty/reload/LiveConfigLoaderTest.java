package com.rbc.fogwall.jetty.reload;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.rbc.fogwall.config.AttestationQuestion;
import com.rbc.fogwall.config.FogwallConfigLoader;
import com.rbc.fogwall.config.JettyConfigurationBuilder;
import com.rbc.fogwall.config.LoadedConfig;
import com.rbc.fogwall.config.MatchRule;
import com.rbc.fogwall.config.ReloadConfig;
import com.rbc.fogwall.db.UrlRuleRegistry;
import com.rbc.fogwall.jetty.reload.LiveConfigLoader.Section;
import com.rbc.fogwall.permission.RepoPermission;
import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.user.UserStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.github.gestalt.config.exceptions.GestaltException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for which sections {@link LiveConfigLoader} applies from a reload document, and when it refuses one. How a
 * declared section's keys merge onto the startup configuration is covered by {@code FogwallConfigLoaderTest}.
 */
class LiveConfigLoaderTest {

    @TempDir
    Path tempDir;

    private LoadedConfig startup;
    private ConfigHolder configHolder;
    private Path reloadFile;
    private LiveConfigLoader loader;

    @BeforeEach
    void setUp() throws IOException, GestaltException {
        Path startupFile = write("""
                diff-scan:
                  block:
                    - { match: literal, value: "STARTUP_A" }
                    - { match: literal, value: "STARTUP_B" }
                commit:
                  message:
                    block:
                      - 'STARTUP_MESSAGE'
                """);
        startup = FogwallConfigLoader.loadLayers(null, List.of(startupFile), Map.of());
        configHolder = new JettyConfigurationBuilder(startup.getConfig()).buildConfigHolder();
        reloadFile = tempDir.resolve("reload.yml");
        loader = new LiveConfigLoader(configHolder, startup, fileReloadConfig(reloadFile), null, null, null);
    }

    @Test
    void reload_declaredList_replacesStartupListWholly() throws IOException {
        Files.writeString(reloadFile, """
                diff-scan:
                  block:
                    - { match: literal, value: "RELOADED" }
                """);

        String result = loader.reload(Section.ALL);

        assertTrue(result.startsWith("Reloaded"), result);
        assertEquals(
                List.of("RELOADED"),
                configHolder.getDiffScanConfig().getBlock().getRules().stream()
                        .map(MatchRule::getValue)
                        .toList());
    }

    @Test
    void reload_undeclaredSection_isNotReapplied() throws IOException {
        var commitBefore = configHolder.getCommitConfig();
        Files.writeString(reloadFile, """
                diff-scan:
                  block: []
                """);

        loader.reload(Section.ALL);

        assertSame(commitBefore, configHolder.getCommitConfig(), "commit wasn't declared, so it must not be rebuilt");
    }

    @Test
    void reload_specificSection_appliesOnlyThatSection() throws IOException {
        var commitBefore = configHolder.getCommitConfig();
        Files.writeString(reloadFile, """
                diff-scan:
                  block: []
                commit:
                  message:
                    block:
                      - 'RELOADED_MESSAGE'
                """);

        loader.reload(Section.DIFF_SCAN);

        assertTrue(configHolder.getDiffScanConfig().getBlock().getRules().isEmpty());
        assertSame(commitBefore, configHolder.getCommitConfig(), "only diff-scan was requested");
    }

    @Test
    void reload_nonReloadableKey_isIgnoredAndTheRestApplies() throws IOException {
        Files.writeString(reloadFile, """
                server:
                  port: 1
                diff-scan:
                  block: []
                """);

        String result = loader.reload(Section.ALL);

        assertTrue(result.startsWith("Reloaded"), result);
        assertTrue(configHolder.getDiffScanConfig().getBlock().getRules().isEmpty());
    }

    @Test
    void reload_scmApiBlock_appliesAndRestartOnlyScmApiKeysAreIgnored() throws IOException {
        Files.writeString(reloadFile, """
                scm-api:
                  node-id-cache-ttl: PT1H
                  block:
                    - { match: literal, value: "RELOADED_SCM_API" }
                """);

        String result = loader.reload(Section.SCM_API);

        assertTrue(result.startsWith("Reloaded"), result);
        assertEquals(
                List.of("RELOADED_SCM_API"),
                configHolder.getScmApiBlockConfig().getRules().stream()
                        .map(MatchRule::getValue)
                        .toList());
    }

    @Test
    void reload_contentPatterns_replacesLiveConfig() throws IOException {
        Files.writeString(reloadFile, """
                content-patterns:
                  enabled: true
                  bundles: [national-id-ca]
                  scan-scm-api: false
                """);

        String result = loader.reload(Section.CONTENT_PATTERNS);

        assertTrue(result.startsWith("Reloaded"), result);
        var contentPatterns = configHolder.getContentPatternConfig();
        assertTrue(contentPatterns.isEnabled());
        assertEquals(List.of("national-id-ca"), contentPatterns.getBundles());
        assertFalse(contentPatterns.isScanScmApi());
    }

    @Test
    void reload_unknownProviderReference_refusesWholeReload() throws IOException {
        var diffScanBefore = configHolder.getDiffScanConfig();
        Files.writeString(reloadFile, """
                diff-scan:
                  block: []
                permissions:
                  - username: someone
                    provider: unknown-provider
                    match:
                      target: SLUG
                      value: '/**'
                    grant: PUSH
                """);

        String result = loader.reload(Section.ALL);

        assertTrue(result.contains("failed"), result);
        assertSame(
                diffScanBefore,
                configHolder.getDiffScanConfig(),
                "a bad reference anywhere in the document must leave every section as it was");
    }

    @Test
    void reload_emptyDocument_isRefusedAndChangesNothing() throws IOException {
        var commitBefore = configHolder.getCommitConfig();
        var diffScanBefore = configHolder.getDiffScanConfig();
        Files.writeString(reloadFile, "");

        String result = loader.reload(Section.ALL);

        assertTrue(result.contains("failed"), result);
        assertSame(commitBefore, configHolder.getCommitConfig());
        assertSame(diffScanBefore, configHolder.getDiffScanConfig());
    }

    @Test
    void reload_everySection_isRebuiltFromTheDocument() throws IOException {
        var urlRuleRegistry = mock(UrlRuleRegistry.class);
        var allSections =
                new LiveConfigLoader(configHolder, startup, fileReloadConfig(reloadFile), urlRuleRegistry, null, null);
        List<Object> before = List.of(
                configHolder.getCommitConfig(),
                configHolder.getDiffScanConfig(),
                configHolder.getSecretScanConfig(),
                configHolder.getBinaryBlobConfig(),
                configHolder.getScmApiBlockConfig(),
                configHolder.getContentPatternConfig());
        Files.writeString(reloadFile, """
                commit:
                  message:
                    block:
                      - 'RELOADED_MESSAGE'
                diff-scan:
                  block: []
                secret-scan:
                  enabled: false
                binary-blob:
                  enabled: false
                scm-api:
                  block:
                    - { match: literal, value: "RELOADED_SCM_API" }
                content-patterns:
                  enabled: false
                rules:
                  allow:
                    - enabled: true
                      provider: github
                      match:
                        target: SLUG
                        value: /owner/repo
                permissions:
                  - username: someone
                    provider: github
                    match:
                      target: SLUG
                      value: /owner/repo
                    grant: PUSH
                attestations:
                  - id: reloaded-question
                    type: checkbox
                    label: Reloaded
                """);

        String result = allSections.reload(Section.ALL);

        assertTrue(result.startsWith("Reloaded"), result);
        List<Object> after = List.of(
                configHolder.getCommitConfig(),
                configHolder.getDiffScanConfig(),
                configHolder.getSecretScanConfig(),
                configHolder.getBinaryBlobConfig(),
                configHolder.getScmApiBlockConfig(),
                configHolder.getContentPatternConfig());
        for (int i = 0; i < before.size(); i++) {
            assertNotSame(before.get(i), after.get(i), "section " + i + " was declared, so it must be rebuilt");
        }
        assertEquals(
                List.of("reloaded-question"),
                configHolder.getAttestations().stream()
                        .map(AttestationQuestion::getId)
                        .toList());
        verify(urlRuleRegistry).seedFromConfig(argThat(rules -> !rules.isEmpty()));
    }

    @Test
    void reload_permissions_seedsThroughTheRunningUserStore() throws IOException {
        var repoPermissionService = mock(RepoPermissionService.class);
        var userStore = mock(UserStore.class);
        var permissionsLoader = new LiveConfigLoader(
                configHolder, startup, fileReloadConfig(reloadFile), null, repoPermissionService, userStore);
        Files.writeString(reloadFile, """
                permissions:
                  - username: someone
                    provider: github
                    match:
                      target: SLUG
                      value: /owner/repo
                    grant: PUSH
                """);

        String result = permissionsLoader.reload(Section.PERMISSIONS);

        assertTrue(result.startsWith("Reloaded"), result);
        verify(userStore).upsertUser("someone");
        verify(repoPermissionService)
                .seedFromConfig(argThat(perms ->
                        perms.stream().map(RepoPermission::getUsername).toList().equals(List.of("someone"))));
    }

    private Path write(String yaml) throws IOException {
        Path f = Files.createTempFile(tempDir, "startup-", ".yml");
        Files.writeString(f, yaml);
        return f;
    }

    private static ReloadConfig fileReloadConfig(Path file) {
        var fileSource = new ReloadConfig.FileSourceConfig();
        fileSource.setEnabled(true);
        fileSource.setPath(file.toString());
        var reloadConfig = new ReloadConfig();
        reloadConfig.setFile(fileSource);
        return reloadConfig;
    }
}
