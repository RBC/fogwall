package com.rbc.fogwall.jetty.config;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.approval.AutoApprovalGateway;
import com.rbc.fogwall.approval.UiApprovalGateway;
import com.rbc.fogwall.config.*;
import com.rbc.fogwall.db.PushStoreFactory;
import com.rbc.fogwall.db.model.AccessRule;
import com.rbc.fogwall.db.model.MatchType;
import com.rbc.fogwall.permission.RepoPermission;
import com.rbc.fogwall.provider.BitbucketProvider;
import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.provider.ForgejoProvider;
import com.rbc.fogwall.provider.GitHubProvider;
import com.rbc.fogwall.provider.GitLabProvider;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JettyConfigurationBuilderTest {

    // ---- buildApprovalGateway ----

    @Test
    void buildApprovalGateway_defaultConfig_returnsAutoApprovalGateway() {
        var builder = new JettyConfigurationBuilder(configWithApprovalMode("auto"));
        var gateway = builder.buildApprovalGateway(PushStoreFactory.inMemory());
        assertInstanceOf(AutoApprovalGateway.class, gateway);
    }

    @Test
    void buildApprovalGateway_uiMode_returnsUiApprovalGateway() {
        var builder = new JettyConfigurationBuilder(configWithApprovalMode("ui"));
        var gateway = builder.buildApprovalGateway(PushStoreFactory.inMemory());
        assertInstanceOf(UiApprovalGateway.class, gateway);
    }

    @Test
    void buildApprovalGateway_unknownMode_fallsBackToAuto() {
        var builder = new JettyConfigurationBuilder(configWithApprovalMode("bogus"));
        var gateway = builder.buildApprovalGateway(PushStoreFactory.inMemory());
        assertInstanceOf(AutoApprovalGateway.class, gateway);
    }

    // ---- validateProviderReferences ----

    @Test
    void validateProviderReferences_validConfig_doesNotThrow() {
        var config = configWithGithub();
        config.setPermissions(List.of(slugPerm("alice", "github", "/org/repo")));

        assertDoesNotThrow(() -> new JettyConfigurationBuilder(config).validateProviderReferences());
    }

    @Test
    void validateProviderReferences_unknownPermissionProvider_throws() {
        var config = configWithGithub();
        config.setPermissions(List.of(slugPerm("alice", "not-a-provider", "/org/repo")));

        var builder = new JettyConfigurationBuilder(config);
        var ex = assertThrows(IllegalStateException.class, builder::validateProviderReferences);
        assertTrue(ex.getMessage().contains("not-a-provider"));
        assertTrue(ex.getMessage().contains("github"));
    }

    @Test
    void validateProviderReferences_unknownRuleProvider_throws() {
        var config = configWithGithub();
        config.getRules().setAllow(List.of(slugRule("typo-provider", "/org/repo", 1100)));

        var builder = new JettyConfigurationBuilder(config);
        assertThrows(IllegalStateException.class, builder::validateProviderReferences);
    }

    @Test
    void validateProviderReferences_emptyConfig_doesNotThrow() {
        assertDoesNotThrow(() -> new JettyConfigurationBuilder(new FogwallConfig()).validateProviderReferences());
    }

    // ---- buildProviderRegistry (#127) ----

    @Test
    void buildProviderRegistry_keyedByFriendlyName() {
        var builder = new JettyConfigurationBuilder(configWithGithub());
        var registry = builder.buildProviderRegistry();

        assertTrue(registry.getProvider("github").isPresent());
        assertInstanceOf(GitHubProvider.class, registry.getProvider("github").orElseThrow());
        assertTrue(registry.resolveProvider("github").isPresent());
        assertSame(
                registry.getProvider("github").orElseThrow(),
                registry.resolveProvider("github").orElseThrow());
        assertEquals(
                registry.resolveProvider("github").orElseThrow().getProviderId(),
                registry.resolveProvider("github").orElseThrow().getProviderId());
    }

    // ---- buildConfigPermissions — provider name resolution ----

    @Test
    void buildConfigPermissions_name_stored_on_permission() {
        var config = configWithGithub();
        config.setPermissions(List.of(slugPerm("alice", "github", "/org/repo")));

        List<RepoPermission> perms = new JettyConfigurationBuilder(config).buildConfigPermissions(config);

        assertEquals(1, perms.size());
        assertEquals("github", perms.get(0).getProvider());
        assertEquals("alice", perms.get(0).getUsername());
    }

    @Test
    void buildConfigPermissions_unknownProvider_throwsWithHelpfulMessage() {
        var config = configWithGithub();
        config.setPermissions(List.of(slugPerm("carol", "nonexistent", "/org/repo")));

        var builder = new JettyConfigurationBuilder(config);
        var ex = assertThrows(IllegalStateException.class, () -> builder.buildConfigPermissions(config));
        assertTrue(ex.getMessage().contains("nonexistent"), "error should name the unknown provider");
        assertTrue(ex.getMessage().contains("github"), "error should list configured providers");
    }

    // ---- buildConfigRules — provider name resolution ----

    @Test
    void buildConfigRules_name_stored_on_rule() {
        var config = configWithGithub();
        config.getRules().setAllow(List.of(slugRule("github", "/org/repo", 1100)));

        List<AccessRule> rules = new JettyConfigurationBuilder(config).buildConfigRules(config);

        assertFalse(rules.isEmpty());
        assertEquals("github", rules.get(0).getProvider());
    }

    @Test
    void buildConfigRules_noProviderFilter_storesNullProvider() {
        var config = configWithGithub();
        config.getRules().setAllow(List.of(slugRule("", "/org/repo", 1100)));

        List<AccessRule> rules = new JettyConfigurationBuilder(config).buildConfigRules(config);

        assertFalse(rules.isEmpty());
        assertNull(rules.get(0).getProvider());
    }

    // ---- buildConfigRules — provider name scoping ----

    @Test
    void buildConfigRules_name_scopes_rule_to_provider() {
        var config = configWithGithub();
        config.getRules().setAllow(List.of(slugRule("github", "/org/repo", 110)));

        var builder = new JettyConfigurationBuilder(config);
        var githubProviderId = builder.buildProviderRegistry()
                .getProvider("github")
                .orElseThrow()
                .getProviderId();
        var rules = builder.buildConfigRules();

        assertFalse(rules.isEmpty(), "rule scoped to 'github' should produce at least one AccessRule");
        assertTrue(
                rules.stream().anyMatch(r -> githubProviderId.equals(r.getProvider())),
                "rule should be scoped to the GitHub provider ID");
    }

    @Test
    void buildConfigRules_name_excludes_other_provider() {
        var config = configWithGithubAndGitlab();
        config.getRules().setAllow(List.of(slugRule("github", "/org/repo", 110)));

        var builder = new JettyConfigurationBuilder(config);
        var gitlabProviderId = builder.buildProviderRegistry()
                .getProvider("gitlab")
                .orElseThrow()
                .getProviderId();
        var rules = builder.buildConfigRules();

        assertTrue(
                rules.stream().noneMatch(r -> gitlabProviderId.equals(r.getProvider())),
                "rule scoped to 'github' should not produce a rule for the GitLab provider");
    }

    // ---- multi-provider: same type, different hostnames ----

    @Test
    void twoProvidersOfSameType_differentNames_haveDistinctProviderIds() {
        var config = configWithTwoGitHubProviders();
        var builder = new JettyConfigurationBuilder(config);
        var providers = builder.buildProviders();

        assertEquals(2, providers.size());
        var ids = providers.stream().map(FogwallProvider::getProviderId).toList();
        assertTrue(ids.contains("github"), "public GitHub should have id 'github'");
        assertTrue(ids.contains("internal-github"), "internal GHES should have id 'internal-github'");
        assertNotEquals(
                providers.get(0).getProviderId(),
                providers.get(1).getProviderId(),
                "two providers of the same type must have distinct IDs");
    }

    @Test
    void twoProvidersOfSameType_permissionsAreKeptSeparate() {
        var config = configWithTwoGitHubProviders();
        config.setPermissions(List.of(
                slugPerm("alice", "github", "/org/public-repo"),
                slugPerm("bob", "internal-github", "/corp/internal-repo")));

        var perms = new JettyConfigurationBuilder(config).buildConfigPermissions(config);

        assertEquals(2, perms.size());
        var alicePerm = perms.stream()
                .filter(p -> "alice".equals(p.getUsername()))
                .findFirst()
                .orElseThrow();
        var bobPerm = perms.stream()
                .filter(p -> "bob".equals(p.getUsername()))
                .findFirst()
                .orElseThrow();
        assertEquals("github", alicePerm.getProvider());
        assertEquals("internal-github", bobPerm.getProvider());
    }

    // ---- MatchConfig.type defaults ----

    @Test
    void buildConfigPermissions_nullType_defaultsToGlob() {
        var config = configWithGithub();
        var perm = slugPerm("alice", "github", "/org/repo");
        perm.getMatch().setType(null);
        config.setPermissions(List.of(perm));

        List<RepoPermission> perms = new JettyConfigurationBuilder(config).buildConfigPermissions(config);

        assertEquals(MatchType.GLOB, perms.get(0).getMatchType());
    }

    @Test
    void buildConfigRules_nullType_defaultsToGlob() {
        var config = configWithGithub();
        var rule = slugRule("github", "/org/repo", 1100);
        rule.getMatch().setType(null);
        config.getRules().setAllow(List.of(rule));

        List<AccessRule> rules = new JettyConfigurationBuilder(config).buildConfigRules(config);

        assertEquals(MatchType.GLOB, rules.get(0).getMatchType());
    }

    // ---- provider type inference from config key ----

    @Test
    void createProvider_githubKey_inferredAsGitHubProvider() {
        var providers = new JettyConfigurationBuilder(configWithSingleProvider("github", new ProviderConfig()))
                .buildProviders();
        assertEquals(1, providers.size());
        assertInstanceOf(GitHubProvider.class, providers.get(0));
        assertEquals("github", providers.get(0).getName());
        assertEquals(GitHubProvider.DEFAULT_URI, providers.get(0).getUri());
    }

    @Test
    void createProvider_gitlabKey_inferredAsGitLabProvider() {
        var providers = new JettyConfigurationBuilder(configWithSingleProvider("gitlab", new ProviderConfig()))
                .buildProviders();
        assertEquals(1, providers.size());
        assertInstanceOf(GitLabProvider.class, providers.get(0));
        assertEquals(GitLabProvider.DEFAULT_URI, providers.get(0).getUri());
    }

    @Test
    void createProvider_bitbucketKey_inferredAsBitbucketProvider() {
        var providers = new JettyConfigurationBuilder(configWithSingleProvider("bitbucket", new ProviderConfig()))
                .buildProviders();
        assertEquals(1, providers.size());
        assertInstanceOf(BitbucketProvider.class, providers.get(0));
        assertEquals(BitbucketProvider.DEFAULT_URI, providers.get(0).getUri());
    }

    @Test
    void createProvider_codebergKey_inferredAsForgejoProviderWithCodebergUri() {
        var providers = new JettyConfigurationBuilder(configWithSingleProvider("codeberg", new ProviderConfig()))
                .buildProviders();
        assertEquals(1, providers.size());
        assertInstanceOf(ForgejoProvider.class, providers.get(0));
        assertEquals(ForgejoProvider.CODEBERG, providers.get(0).getUri());
    }

    @Test
    void createProvider_giteaKey_noUri_defaultsToGiteaCom() {
        var providers =
                new JettyConfigurationBuilder(configWithSingleProvider("gitea", new ProviderConfig())).buildProviders();
        assertEquals(1, providers.size());
        assertInstanceOf(ForgejoProvider.class, providers.get(0));
        assertEquals(ForgejoProvider.GITEA, providers.get(0).getUri());
    }

    @Test
    void createProvider_giteaKey_withUri_usesCustomUri() {
        var pc = new ProviderConfig();
        pc.setUri("https://gitea.corp.com");
        var providers = new JettyConfigurationBuilder(configWithSingleProvider("gitea", pc)).buildProviders();
        assertEquals(1, providers.size());
        assertInstanceOf(ForgejoProvider.class, providers.get(0));
        assertEquals(URI.create("https://gitea.corp.com"), providers.get(0).getUri());
    }

    @Test
    void createProvider_forgejoKey_withUri_usesCustomUri() {
        var pc = new ProviderConfig();
        pc.setUri("https://forge.example.com");
        var providers = new JettyConfigurationBuilder(configWithSingleProvider("my-forge", pc)).buildProviders();
        // no known type and no known key — falls back to generic
        assertEquals(1, providers.size());
        assertEquals(URI.create("https://forge.example.com"), providers.get(0).getUri());
    }

    @Test
    void createProvider_forgejoType_withUri_isForgejoProvider() {
        var pc = new ProviderConfig();
        pc.setType("forgejo");
        pc.setUri("https://forge.example.com");
        var providers = new JettyConfigurationBuilder(configWithSingleProvider("my-forge", pc)).buildProviders();
        assertEquals(1, providers.size());
        assertInstanceOf(ForgejoProvider.class, providers.get(0));
        assertEquals(URI.create("https://forge.example.com"), providers.get(0).getUri());
    }

    @Test
    void createProvider_forgejoType_noUri_skipped() {
        var pc = new ProviderConfig();
        pc.setType("forgejo");
        var providers = new JettyConfigurationBuilder(configWithSingleProvider("my-forge", pc)).buildProviders();
        assertTrue(providers.isEmpty(), "forgejo provider without URI must be skipped");
    }

    @Test
    void createProvider_customNameWithGithubType_isGitHubProvider() {
        var pc = new ProviderConfig();
        pc.setType("github");
        pc.setUri("https://github.internal.example.com");
        var providers = new JettyConfigurationBuilder(configWithSingleProvider("internal-github", pc)).buildProviders();
        assertEquals(1, providers.size());
        assertInstanceOf(GitHubProvider.class, providers.get(0));
        assertEquals("internal-github", providers.get(0).getName());
        assertEquals(
                URI.create("https://github.internal.example.com"),
                providers.get(0).getUri());
    }

    @Test
    void createProvider_customNameWithGiteaType_isForgejoProvider() {
        var pc = new ProviderConfig();
        pc.setType("gitea");
        pc.setUri("https://gitea.corp.com");
        var providers = new JettyConfigurationBuilder(configWithSingleProvider("corp-gitea", pc)).buildProviders();
        assertEquals(1, providers.size());
        assertInstanceOf(ForgejoProvider.class, providers.get(0));
        assertEquals("corp-gitea", providers.get(0).getName());
    }

    // ---- helpers ----

    private static PermissionConfig slugPerm(String username, String provider, String value) {
        var perm = new PermissionConfig();
        perm.setUsername(username);
        perm.setProvider(provider);
        var m = new MatchConfig();
        m.setTarget("SLUG");
        m.setValue(value);
        m.setType("LITERAL");
        perm.setMatch(m);
        return perm;
    }

    private static RuleConfig slugRule(String provider, String value, int order) {
        var rule = new RuleConfig();
        rule.setProvider(provider);
        rule.setOrder(order);
        var m = new MatchConfig();
        m.setTarget("SLUG");
        m.setValue(value);
        m.setType("LITERAL");
        rule.setMatch(m);
        return rule;
    }

    private static FogwallConfig configWithApprovalMode(String mode) {
        var config = new FogwallConfig();
        config.getServer().setApprovalMode(mode);
        return config;
    }

    private static FogwallConfig configWithSingleProvider(String key, ProviderConfig pc) {
        var config = new FogwallConfig();
        pc.setEnabled(true);
        config.setProviders(Map.of(key, pc));
        return config;
    }

    private static FogwallConfig configWithGithub() {
        var config = new FogwallConfig();
        var providerConfig = new ProviderConfig();
        providerConfig.setEnabled(true);
        config.setProviders(Map.of("github", providerConfig));
        return config;
    }

    private static FogwallConfig configWithGithubAndGitlab() {
        var config = new FogwallConfig();
        var github = new ProviderConfig();
        github.setEnabled(true);
        var gitlab = new ProviderConfig();
        gitlab.setEnabled(true);
        config.setProviders(Map.of("github", github, "gitlab", gitlab));
        return config;
    }

    private static FogwallConfig configWithTwoGitHubProviders() {
        var config = new FogwallConfig();
        var publicGitHub = new ProviderConfig();
        publicGitHub.setEnabled(true);
        var internalGitHub = new ProviderConfig();
        internalGitHub.setEnabled(true);
        internalGitHub.setType("github");
        internalGitHub.setUri("https://github.internal.example.com");
        config.setProviders(Map.of("github", publicGitHub, "internal-github", internalGitHub));
        return config;
    }
}
