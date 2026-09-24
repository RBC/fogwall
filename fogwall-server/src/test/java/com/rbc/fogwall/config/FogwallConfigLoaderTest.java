package com.rbc.fogwall.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.github.gestalt.config.exceptions.GestaltException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * Tests for {@link FogwallConfigLoader}: the bundled defaults, how layers merge, profile resolution, environment
 * overrides, and hot reload composition.
 */
class FogwallConfigLoaderTest {

    // --- server defaults ---

    @Test
    void defaultPort_is8080() throws GestaltException {
        FogwallConfig config = FogwallConfigLoader.load();
        assertEquals(8080, config.getServer().getPort());
    }

    @Test
    void defaultApprovalMode_isAuto() throws GestaltException {
        FogwallConfig config = FogwallConfigLoader.load();
        assertEquals("auto", config.getServer().getApprovalMode());
    }

    @Test
    void defaultHeartbeatInterval_is10() throws GestaltException {
        FogwallConfig config = FogwallConfigLoader.load();
        assertEquals(10, config.getServer().getHeartbeatIntervalSeconds());
    }

    @Test
    void defaultTrustForwardedHeaders_isTrue() throws GestaltException {
        // Backcompat: existing ingress deployments depend on forwarded-header resolution for login
        // redirects and cookie security, so the default must stay on.
        FogwallConfig config = FogwallConfigLoader.load();
        assertTrue(config.getServer().isTrustForwardedHeaders());
    }

    @Test
    void defaultOtel_isDisabled() throws GestaltException {
        // Observability is opt-in: the default push path must carry no instrumentation cost.
        OtelConfig otel = FogwallConfigLoader.load().getOtel();
        assertNotNull(otel);
        assertFalse(otel.isEnabled());
        assertTrue(otel.getTracing().isEnabled());
        assertTrue(otel.getMetrics().isEnabled());
    }

    // --- database defaults ---

    @Test
    void databaseType_isConfigured() throws GestaltException {
        String type = FogwallConfigLoader.load().getDatabase().getType();
        assertNotNull(type);
        assertFalse(type.isBlank());
    }

    // --- providers ---

    @Test
    void defaultProviders_includesGitHub() throws GestaltException {
        assertTrue(FogwallConfigLoader.load().getProviders().containsKey("github"));
    }

    @Test
    void defaultProviders_includesGitLab() throws GestaltException {
        assertTrue(FogwallConfigLoader.load().getProviders().containsKey("gitlab"));
    }

    @Test
    void defaultProviders_includesCodeberg() throws GestaltException {
        assertTrue(FogwallConfigLoader.load().getProviders().containsKey("codeberg"));
    }

    @Test
    void defaultProviders_includesGitea() throws GestaltException {
        assertTrue(FogwallConfigLoader.load().getProviders().containsKey("gitea"));
    }

    @Test
    void defaultProviders_githubIsEnabled() throws GestaltException {
        ProviderConfig github = FogwallConfigLoader.load().getProviders().get("github");
        assertNotNull(github);
        assertTrue(github.isEnabled());
    }

    @Test
    void deepMerge_localOverride_doesNotWipeOtherProviders() throws GestaltException {
        // Even if a local override touches only one key, all three built-in providers must survive.
        var providers = FogwallConfigLoader.load().getProviders();
        assertTrue(providers.containsKey("github"));
        assertTrue(providers.containsKey("gitlab"));
        assertTrue(providers.containsKey("codeberg"));
        assertTrue(providers.containsKey("gitea"));
    }

    // --- commit config presence ---

    @Test
    void commitConfig_secretScanning_hasDefault() throws GestaltException {
        var ss = FogwallConfigLoader.load().getSecretScan();
        assertNotNull(ss);
        // base config ships with secret scanning enabled
        assertTrue(ss.isEnabled());
    }

    // --- rules ---

    @Test
    void rules_allowList_returnsNonNull() throws GestaltException {
        assertNotNull(FogwallConfigLoader.load().getRules().getAllow());
    }

    // --- loadWithOverride (externalized config path) ---
    //
    // These tests exercise the same code path used by:
    //   • LiveConfigLoader (hot-reload from a watched file or a git-sourced file)
    //   • The Docker /app/conf/ pattern when fogwall_CONFIG_PROFILES is not set and an
    //     explicit reload is triggered via POST /api/config/reload
    //
    // When a test here fails, the cause is almost always a renamed config key. Check
    // whether the YAML key in the override file still matches the field name in the
    // corresponding Settings POJO (e.g. SecretScanSettings, DiffScanSettings, CommitSettings).

    @TempDir
    Path tempDir;

    @Test
    void loadWithOverride_secretScan_disabledOverridesDefault() throws GestaltException, IOException {
        // Base config ships with secret-scan.enabled: true — override to false.
        Path override = writeYaml("""
                secret-scan:
                  enabled: false
                """);
        var config = FogwallConfigLoader.loadWithOverride(override);
        assertFalse(
                config.getSecretScan().isEnabled(),
                "override file secret-scan.enabled: false should win over base default");
    }

    @Test
    void loadWithOverride_diffScan_deprecatedObjectShape_foldsToMatchers() throws GestaltException, IOException {
        // The old { literals, patterns } object still loads, folded into matchers and flagged deprecated.
        Path override = writeYaml("""
                diff-scan:
                  block:
                    literals:
                      - "CANARY_STRING"
                """);
        var config = FogwallConfigLoader.loadWithOverride(override);
        var block = config.getDiffScan().getBlock();
        assertTrue(block.isDeprecatedShape(), "the { literals, patterns } object is the deprecated shape");
        assertEquals(1, block.getMatchers().size());
        assertEquals("literal", block.getMatchers().get(0).getMatch());
        assertEquals("CANARY_STRING", block.getMatchers().get(0).getValue());
    }

    @Test
    void loadWithOverride_commitMessage_deprecatedObjectShape_foldsToMatchers() throws GestaltException, IOException {
        Path override = writeYaml("""
                commit:
                  message:
                    block:
                      literals:
                        - "DO_NOT_PUSH"
                """);
        var config = FogwallConfigLoader.loadWithOverride(override);
        var block = config.getCommit().getMessage().getBlock();
        assertTrue(block.isDeprecatedShape());
        assertEquals("DO_NOT_PUSH", block.getMatchers().get(0).getValue());
    }

    @Test
    void loadWithOverride_diffScan_listShape_scalarAndObject() throws GestaltException, IOException {
        // The current shape: a scalar entry binds as regex, a mapping sets match explicitly.
        Path override = writeYaml("""
                diff-scan:
                  block:
                    - '(?i)secret'
                    - { match: literal, value: "CANARY_STRING" }
                """);
        var config = FogwallConfigLoader.loadWithOverride(override);
        var block = config.getDiffScan().getBlock();
        assertFalse(block.isDeprecatedShape());
        var matchers = block.getMatchers();
        assertEquals(2, matchers.size());
        assertEquals("regex", matchers.get(0).getMatch());
        assertEquals("(?i)secret", matchers.get(0).getValue());
        assertEquals("literal", matchers.get(1).getMatch());
        assertEquals("CANARY_STRING", matchers.get(1).getValue());
    }

    @Test
    void loadWithOverride_commitMessage_listShape_scalar() throws GestaltException, IOException {
        Path override = writeYaml("""
                commit:
                  message:
                    block:
                      - '(?i)do not merge'
                """);
        var config = FogwallConfigLoader.loadWithOverride(override);
        var block = config.getCommit().getMessage().getBlock();
        assertFalse(block.isDeprecatedShape());
        assertEquals(1, block.getMatchers().size());
        assertEquals("regex", block.getMatchers().get(0).getMatch());
        assertEquals("(?i)do not merge", block.getMatchers().get(0).getValue());
    }

    @Test
    void loadWithOverride_attributionPolicy_bindsNewKey() throws GestaltException, IOException {
        Path override = writeYaml("""
                commit:
                  attribution-policy:
                    committer: strict
                    author: strict
                """);
        var config = FogwallConfigLoader.loadWithOverride(override);
        assertEquals("strict", config.getCommit().getAttributionPolicy().getCommitter());
        assertEquals("strict", config.getCommit().getAttributionPolicy().getAuthor());
    }

    @Test
    void loadWithOverride_legacyIdentityVerificationKey_acceptedButIgnored() throws GestaltException, IOException {
        // The renamed key must not crash startup on an unknown property, but its value is intentionally NOT applied —
        // operators must migrate to commit.attribution-policy. JettyConfigurationBuilder logs a deprecation warning
        // when the
        // deprecated field is populated.
        Path override = writeYaml("""
                commit:
                  identity-verification:
                    committer: strict
                    author: strict
                """);
        var config = FogwallConfigLoader.loadWithOverride(override);
        // Accepted: binds to the deprecated field (so the builder can detect it and warn), no validation failure.
        assertNotNull(
                config.getCommit().getIdentityVerification(),
                "legacy key should bind to the deprecated field so the migration warning can fire");
        assertEquals("strict", config.getCommit().getIdentityVerification().getCommitter());
        // Ignored: attribution-policy keeps its defaults — the legacy value has no effect.
        assertEquals(
                "warn",
                config.getCommit().getAttributionPolicy().getCommitter(),
                "legacy key must not change the effective attribution-policy");
        assertEquals("off", config.getCommit().getAttributionPolicy().getAuthor());
    }

    @Test
    void loadWithOverride_baseValuesPreservedWhenNotOverridden() throws GestaltException, IOException {
        // An override that only touches one key must not wipe out other base defaults.
        Path override = writeYaml("""
                server:
                  port: 9999
                """);
        var config = FogwallConfigLoader.loadWithOverride(override);
        assertEquals(9999, config.getServer().getPort());
        // Base providers must survive the merge
        assertTrue(config.getProviders().containsKey("github"), "base providers must survive a partial override");
        // Base secret-scan default must survive
        assertTrue(
                config.getSecretScan().isEnabled(), "base secret-scan.enabled: true must survive a partial override");
    }

    @Test
    void loadWithOverride_trustForwardedHeaders_canBeDisabled() throws GestaltException, IOException {
        Path override = writeYaml("""
                server:
                  trust-forwarded-headers: false
                """);
        var config = FogwallConfigLoader.loadWithOverride(override);
        assertFalse(config.getServer().isTrustForwardedHeaders());
    }

    @Test
    void loadWithOverride_friendlyProviderName_inPermissions() throws GestaltException, IOException {
        // Verifies that friendly provider names in an externalized config are accepted.
        // This is the key regression test for #127 — if the key rename broke parsing,
        // provider references in ConfigMaps would silently fail validation at startup.
        Path override = writeYaml("""
                permissions:
                  - username: test-user
                    provider: github
                    match:
                      target: SLUG
                      value: /org/repo
                    grant: PUSH
                """);
        var config = FogwallConfigLoader.loadWithOverride(override);
        assertFalse(config.getPermissions().isEmpty(), "permissions from override should be loaded");
        assertEquals(
                "github",
                config.getPermissions().get(0).getProvider(),
                "friendly provider name should round-trip through the parser");
    }

    @Test
    void loadWithOverride_attestations_partialOverride_preservesBaseDefaults() throws GestaltException, IOException {
        // Attestations are a top-level list, so a partial override that only ships `attestations:`
        // replaces just that list without touching providers/commit/diff-scan/etc. This is the
        // shape operators should use to hot-reload review prompts.
        Path override = writeYaml("""
                attestations:
                  - id: override-only
                    type: checkbox
                    label: "I attest this change is authorized"
                    required: true
                """);
        var config = FogwallConfigLoader.loadWithOverride(override);

        assertEquals(1, config.getAttestations().size());
        assertEquals("override-only", config.getAttestations().get(0).getId());
        // Base providers / secret-scan defaults must survive untouched.
        assertTrue(config.getProviders().containsKey("github"));
        assertTrue(config.getProviders().get("github").isEnabled());
        assertTrue(config.getSecretScan().isEnabled());
    }

    // --- YAML anchors ---

    @Test
    void loadWithOverride_yamlAnchor_resolvesSharedBlockList() throws GestaltException, IOException {
        // A shared matcher list anchored once on diff-scan.block and aliased into commit.message.block and
        // scm-api.block. The Jackson YAMLMapper Gestalt's own YamlLoader builds internally must expand the alias
        // before the POJO binder sees it — an alias resolving to a list must not land as the literal string
        // "*shared-block".
        Path override = writeYaml("""
                diff-scan:
                  block: &shared-block
                    - { match: literal, value: "SHARED_SECRET" }
                commit:
                  message:
                    block: *shared-block
                scm-api:
                  block: *shared-block
                """);
        var config = FogwallConfigLoader.loadWithOverride(override);

        var diffScanBlock = config.getDiffScan().getBlock().getMatchers();
        var commitMessageBlock = config.getCommit().getMessage().getBlock().getMatchers();
        var scmApiBlock = config.getScmApi().getBlock().getMatchers();

        assertEquals(1, diffScanBlock.size());
        assertEquals("literal", diffScanBlock.get(0).getMatch());
        assertEquals("SHARED_SECRET", diffScanBlock.get(0).getValue());

        assertEquals(1, commitMessageBlock.size(), "aliased list should resolve the same as the anchored original");
        assertEquals("SHARED_SECRET", commitMessageBlock.get(0).getValue());

        assertEquals(1, scmApiBlock.size(), "aliased list should resolve the same as the anchored original");
        assertEquals("SHARED_SECRET", scmApiBlock.get(0).getValue());
    }

    // --- unknown-key validation ---

    @Test
    void loadWithOverride_unknownTopLevelKey_throws() throws IOException {
        Path override = writeYaml("typo-key: invalid\n");
        var ex = assertThrows(IllegalStateException.class, () -> FogwallConfigLoader.loadWithOverride(override));
        assertTrue(ex.getMessage().contains("typo-key"), ex.getMessage());
    }

    @Test
    void loadWithOverride_unknownNestedKey_throws() throws IOException {
        Path override = writeYaml("""
                commit:
                  diff:
                    block:
                      literals:
                        - "WIP"
                """);
        var ex = assertThrows(IllegalStateException.class, () -> FogwallConfigLoader.loadWithOverride(override));
        assertTrue(ex.getMessage().contains("diff"), ex.getMessage());
    }

    @Test
    void loadWithOverride_unknownProviderKey_throws() throws IOException {
        Path override = writeYaml("""
                providers:
                  github:
                    enabled: true
                    unknown-option: true
                """);
        var ex = assertThrows(IllegalStateException.class, () -> FogwallConfigLoader.loadWithOverride(override));
        assertTrue(ex.getMessage().contains("unknown-option"), ex.getMessage());
    }

    @Test
    void loadWithOverride_validKeys_noException() throws GestaltException, IOException {
        Path override = writeYaml("""
                server:
                  port: 9090
                secret-scan:
                  enabled: false
                diff-scan:
                  block:
                    literals:
                      - "BLOCKED"
                """);
        // must not throw
        var config = FogwallConfigLoader.loadWithOverride(override);
        assertEquals(9090, config.getServer().getPort());
        assertFalse(config.getSecretScan().isEnabled());
    }

    // --- envVarToConfigPath ---

    @Test
    void envVarToConfigPath_legacySimplePath() {
        assertEquals("server.port", FogwallConfigLoader.envVarToConfigPath("FOGWALL_SERVER_PORT"));
    }

    @Test
    void envVarToConfigPath_legacyThreeSegments() {
        assertEquals(
                "providers.github.enabled", FogwallConfigLoader.envVarToConfigPath("FOGWALL_PROVIDERS_GITHUB_ENABLED"));
    }

    @Test
    void envVarToConfigPath_doubleUnderscore_hyphenatedKey() {
        assertEquals(
                "providers.gitea-ssh.api-token",
                FogwallConfigLoader.envVarToConfigPath("FOGWALL_PROVIDERS__GITEA_SSH__API_TOKEN"));
    }

    @Test
    void envVarToConfigPath_doubleUnderscore_noHyphensInSegments() {
        assertEquals(
                "providers.github.enabled",
                FogwallConfigLoader.envVarToConfigPath("FOGWALL_PROVIDERS__GITHUB__ENABLED"));
    }

    @Test
    void envVarToConfigPath_doubleUnderscore_hyphenatedTopLevelKey() {
        // FOGWALL_SECRET_SCAN__ENABLED → secret-scan.enabled
        assertEquals("secret-scan.enabled", FogwallConfigLoader.envVarToConfigPath("FOGWALL_SECRET_SCAN__ENABLED"));
    }

    // --- profile resolution ---

    @Test
    void profileResources_profileNotOnClasspath_failsNamingTheFile() {
        GestaltException e =
                assertThrows(GestaltException.class, () -> FogwallConfigLoader.profileResources("nonexistent"));
        assertTrue(e.getMessage().contains("fogwall-nonexistent.yml"), e.getMessage());
    }

    @Test
    void profileResources_noProfileNameResolvesToTheBundledDefaults() {
        assertThrows(GestaltException.class, () -> FogwallConfigLoader.profileResources("defaults"));
    }

    @Test
    void profileResources_noProfiles_isEmpty() throws GestaltException {
        assertTrue(FogwallConfigLoader.profileResources(null).isEmpty());
        assertTrue(FogwallConfigLoader.profileResources("  ").isEmpty());
    }

    // --- layering ---

    @Test
    void loadLayers_defaultConfigFile_mergesOverBundledDefaultsAndUnderProfiles() throws GestaltException {
        // fogwall-order-a.yml stands in for the default config file fogwall.yml; fogwall-order-b.yml is a profile.
        assertEquals(
                9001,
                FogwallConfigLoader.loadLayers("fogwall-order-a.yml", null, List.of(), Map.of())
                        .getConfig()
                        .getServer()
                        .getPort());
        assertEquals(
                9002,
                FogwallConfigLoader.loadLayers("fogwall-order-a.yml", "order-b", List.of(), Map.of())
                        .getConfig()
                        .getServer()
                        .getPort());
    }

    @Test
    void loadLayers_noDefaultConfigFile_runsOnBundledDefaults() throws GestaltException {
        var config = FogwallConfigLoader.loadLayers("fogwall-absent.yml", null, List.of(), Map.of())
                .getConfig();
        assertEquals(8080, config.getServer().getPort());
        assertTrue(config.getProviders().get("github").isEnabled());
    }

    @Test
    void loadLayers_list_isReplacedByHigherLayerNotMergedByPosition() throws GestaltException, IOException {
        Path lower = writeYaml("""
                diff-scan:
                  block:
                    - 'LOWER_0'
                    - 'LOWER_1'
                """);
        Path higher = writeYaml("""
                diff-scan:
                  block:
                    - 'HIGHER_0'
                """);
        var matchers = FogwallConfigLoader.loadLayers(null, List.of(lower, higher), Map.of())
                .getConfig()
                .getDiffScan()
                .getBlock()
                .getMatchers();
        assertEquals(
                List.of("HIGHER_0"),
                matchers.stream().map(MatchRuleSettings::getValue).toList());
    }

    @Test
    void loadLayers_emptyList_clearsLowerLayer() throws GestaltException, IOException {
        Path lower = writeYaml("""
                diff-scan:
                  block:
                    - 'LOWER_0'
                """);
        Path higher = writeYaml("""
                diff-scan:
                  block: []
                """);
        var matchers = FogwallConfigLoader.loadLayers(null, List.of(lower, higher), Map.of())
                .getConfig()
                .getDiffScan()
                .getBlock()
                .getMatchers();
        assertTrue(matchers.isEmpty(), "an explicit empty list must clear the lower layer's entries");
    }

    @Test
    void loadLayers_listEntry_doesNotInheritFieldsFromLowerLayerEntry() throws GestaltException, IOException {
        // The bundled defaults already declare a users list; a higher layer's entries replace it rather than being
        // combined with the entry at the same position.
        Path users = writeYaml("""
                users:
                  - username: dev
                    emails:
                      - dev@example.com
                """);
        var loaded = FogwallConfigLoader.loadLayers(null, List.of(users), Map.of())
                .getConfig()
                .getUsers();
        assertEquals(1, loaded.size());
        assertEquals("dev", loaded.get(0).getUsername());
        assertTrue(loaded.get(0).getRoles().isEmpty(), "fields the entry doesn't set keep their own defaults");
        assertTrue(loaded.get(0).getPasswordHash().isEmpty(), "fields the entry doesn't set keep their own defaults");
    }

    @Test
    void loadLayers_mapping_mergesByKey() throws GestaltException, IOException {
        Path override = writeYaml("""
                providers:
                  gitlab:
                    enabled: false
                """);
        var providers = FogwallConfigLoader.loadLayers(null, List.of(override), Map.of())
                .getConfig()
                .getProviders();
        assertFalse(providers.get("gitlab").isEnabled());
        assertTrue(providers.get("github").isEnabled(), "a provider the higher layer doesn't mention is untouched");
    }

    @Test
    void loadLayers_nullValue_leavesLowerLayerValue() throws GestaltException, IOException {
        Path lower = writeYaml("""
                server:
                  service-url: https://lower.example.com
                """);
        Path higher = writeYaml("""
                server:
                  service-url:
                """);
        assertEquals(
                "https://lower.example.com",
                FogwallConfigLoader.loadLayers(null, List.of(lower, higher), Map.of())
                        .getConfig()
                        .getServer()
                        .getServiceUrl());
    }

    @Test
    void loadLayers_environmentOverride_winsOverFiles() throws GestaltException {
        var config = FogwallConfigLoader.loadLayers(null, List.of(), Map.of("FOGWALL_SERVER_PORT", "9999"))
                .getConfig();
        assertEquals(9999, config.getServer().getPort());
    }

    // --- hot reload composition ---

    @Test
    void composeReload_keyInsideSection_replacesOnlyThatKey() throws GestaltException, IOException {
        Path startupFile = writeYaml("""
                commit:
                  committer:
                    email:
                      matches:
                        - { action: block, field: local, match: literal, value: noreply }
                  message:
                    block:
                      - 'STARTUP'
                """);
        var startup = FogwallConfigLoader.loadLayers(null, List.of(startupFile), Map.of());
        var reload = FogwallConfigLoader.readReloadDocument(writeYaml("""
                commit:
                  message:
                    block:
                      - 'RELOADED'
                """));

        var commit = FogwallConfigLoader.composeReload(startup, reload).getCommit();

        assertEquals(
                List.of("RELOADED"),
                commit.getMessage().getBlock().getMatchers().stream()
                        .map(MatchRuleSettings::getValue)
                        .toList());
        assertEquals(
                1,
                commit.getCommitter().getEmail().getMatches().size(),
                "a key the reload document leaves out keeps its startup value");
    }

    @Test
    void composeReload_startsFromStartupNotFromPreviousReload() throws GestaltException, IOException {
        var startup = FogwallConfigLoader.loadLayers(null, List.of(), Map.of());
        FogwallConfigLoader.composeReload(
                startup, FogwallConfigLoader.readReloadDocument(writeYaml("secret-scan:\n  enabled: false\n")));

        var second = FogwallConfigLoader.composeReload(
                startup, FogwallConfigLoader.readReloadDocument(writeYaml("binary-blob:\n  enabled: true\n")));

        assertTrue(second.getSecretScan().isEnabled(), "an earlier reload's value must not carry into a later one");
        assertTrue(second.getBinaryBlob().isEnabled());
    }

    @Test
    void composeReload_winsOverEnvironmentForPathsItSets() throws GestaltException, IOException {
        var startup = FogwallConfigLoader.loadLayers(
                null,
                List.of(),
                Map.of(
                        "FOGWALL_SECRET_SCAN__ENABLED", "true",
                        "FOGWALL_SECRET_SCAN__TIMEOUT_SECONDS", "77"));
        var reload = FogwallConfigLoader.readReloadDocument(writeYaml("secret-scan:\n  enabled: false\n"));

        var secretScan = FogwallConfigLoader.composeReload(startup, reload).getSecretScan();

        assertFalse(secretScan.isEnabled(), "the reload document's value applies over the environment variable");
        assertEquals(77L, secretScan.getTimeoutSeconds(), "an environment override the reload doesn't touch applies");
    }

    @Test
    void load_profileOrder_rightmostProfileWins() throws GestaltException {
        // fogwall-order-a.yml sets server.port: 9001, fogwall-order-b.yml sets server.port: 9002 — same key, two
        // different values, so whichever profile is named last in FOGWALL_CONFIG_PROFILES determines the result.
        assertEquals(
                9002, FogwallConfigLoader.load("order-a,order-b").getServer().getPort());
        assertEquals(
                9001, FogwallConfigLoader.load("order-b,order-a").getServer().getPort());
    }

    @Test
    void profileResources_emptyNamesAreSkipped() throws GestaltException {
        assertEquals(
                List.of("fogwall-order-a.yml", "fogwall-order-b.yml"),
                FogwallConfigLoader.profileResources("order-a,, order-b,"));
    }

    // --- merge and setsPath ---

    @Test
    void merge_listOverMapping_replacesTheMapping() {
        var base = JsonNodeFactory.instance.objectNode();
        base.putObject("rules").putArray("allow").add("a");
        var overlay = JsonNodeFactory.instance.objectNode();
        overlay.putArray("rules");

        var merged = FogwallConfigLoader.merge(base, overlay);

        assertTrue(merged.get("rules").isArray());
        assertTrue(merged.get("rules").isEmpty());
        assertTrue(base.get("rules").isObject(), "merge must not modify its inputs");
    }

    @Test
    void setsPath_valueAtPath_isSet() throws GestaltException, IOException {
        var tree = FogwallConfigLoader.readReloadDocument(writeYaml("secret-scan:\n  enabled: false\n"));
        assertTrue(FogwallConfigLoader.setsPath(tree, "secret-scan.enabled"));
    }

    @Test
    void setsPath_nonMappingAtAncestor_isSet() throws GestaltException, IOException {
        // A list at diff-scan.block replaces everything beneath it, so an override below it is superseded too.
        var tree = FogwallConfigLoader.readReloadDocument(writeYaml("diff-scan:\n  block: []\n"));
        assertTrue(FogwallConfigLoader.setsPath(tree, "diff-scan.block.0.value"));
    }

    @Test
    void setsPath_absentOrNull_isNotSet() throws GestaltException, IOException {
        var tree = FogwallConfigLoader.readReloadDocument(writeYaml("server:\n  service-url:\n"));
        assertFalse(FogwallConfigLoader.setsPath(tree, "server.service-url"), "a null leaves the key unset");
        assertFalse(FogwallConfigLoader.setsPath(tree, "server.port"));
        assertFalse(FogwallConfigLoader.setsPath(tree, "binary-blob.enabled"));
    }

    // --- reading a reload document ---

    @Test
    void readReloadDocument_missingFile_throws() {
        assertThrows(
                GestaltException.class, () -> FogwallConfigLoader.readReloadDocument(tempDir.resolve("absent.yml")));
    }

    @Test
    void readReloadDocument_malformedYaml_throws() throws IOException {
        Path file = writeYaml("diff-scan:\n  block: [unclosed\n");
        assertThrows(RuntimeException.class, () -> FogwallConfigLoader.readReloadDocument(file));
    }

    @Test
    void readReloadDocument_topLevelNotAMapping_throws() throws IOException {
        Path file = writeYaml("- diff-scan\n- commit\n");
        assertThrows(RuntimeException.class, () -> FogwallConfigLoader.readReloadDocument(file));
    }

    @Test
    void readReloadDocument_emptyFile_throws() throws IOException {
        Path file = writeYaml("");
        assertThrows(RuntimeException.class, () -> FogwallConfigLoader.readReloadDocument(file));
    }

    private Path writeYaml(String yaml) throws IOException {
        Path f = Files.createTempFile(tempDir, "override-", ".yml");
        Files.writeString(f, yaml);
        return f;
    }
}
