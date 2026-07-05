package com.rbc.fogwall.provider;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProviderTest {

    // --- GitHubProvider ---

    @Test
    void gitHub_defaultUri_servletPath() {
        var p = new GitHubProvider(null);
        assertEquals("/github.com", p.servletPath());
        assertEquals("/github.com/*", p.servletMapping());
    }

    @Test
    void gitHub_pathSuffix_overridesHostname() {
        var p = new GitHubProvider("/mypath");
        assertEquals("/mypath", p.servletPath());
        assertEquals("/mypath/*", p.servletMapping());
    }

    @Test
    void gitHub_defaultUri_apiUrls() {
        var p = new GitHubProvider(null);
        assertEquals("https://api.github.com", p.getApiUrl());
        assertEquals("https://api.github.com/graphql", p.getGraphqlUrl());
    }

    @Test
    void gitHub_enterpriseUri_apiUrls() {
        var p = GitHubProvider.builder()
                .uri(URI.create("https://github.example.com"))
                .pathSuffix("/proxy")
                .build();
        assertEquals("https://github.example.com/api/v3", p.getApiUrl());
        assertEquals("https://github.example.com/api/graphql", p.getGraphqlUrl());
    }

    @Test
    void gitHub_getName() {
        assertEquals("github", new GitHubProvider("/proxy").getName());
    }

    // --- GitLabProvider ---

    @Test
    void gitLab_defaultUri_servletPath() {
        var p = new GitLabProvider(null);
        assertEquals("/gitlab.com", p.servletPath());
        assertEquals("/gitlab.com/*", p.servletMapping());
    }

    @Test
    void gitLab_apiUrl() {
        var p = new GitLabProvider("/proxy");
        assertEquals("https://gitlab.com/api/v4", p.getApiUrl());
    }

    @Test
    void gitLab_graphqlUrl() {
        var p = new GitLabProvider("/proxy");
        assertEquals("https://gitlab.com/api/graphql", p.getGraphqlUrl());
    }

    @Test
    void gitLab_oauthUrl() {
        var p = new GitLabProvider("/proxy");
        assertEquals("https://gitlab.com/oauth", p.getOAuthUrl());
    }

    @Test
    void gitLab_getName() {
        assertEquals("gitlab", new GitLabProvider("/proxy").getName());
    }

    // --- BitbucketProvider ---

    @Test
    void bitbucket_defaultUri_servletPath() {
        var p = new BitbucketProvider(null);
        assertEquals("/bitbucket.org", p.servletPath());
    }

    @Test
    void bitbucket_defaultUri_apiUrl() {
        var p = new BitbucketProvider("/proxy");
        assertEquals("https://api.bitbucket.org/2.0", p.getApiUrl());
    }

    @Test
    void bitbucket_hostedUri_apiUrl() {
        var p = BitbucketProvider.builder()
                .uri(URI.create("https://bitbucket.example.com"))
                .pathSuffix("/proxy")
                .build();
        assertEquals("https://bitbucket.example.com/rest/api/1.0", p.getApiUrl());
    }

    @Test
    void bitbucket_oauthUrl() {
        var p = new BitbucketProvider("/proxy");
        assertEquals("https://bitbucket.org/site/oauth2", p.getOAuthUrl());
    }

    @Test
    void bitbucket_getName() {
        assertEquals("bitbucket", new BitbucketProvider("/proxy").getName());
    }

    // --- GenericProxyProvider ---

    @Test
    void generic_servletPath_usesHostname() {
        var p = GenericProxyProvider.builder()
                .name("my-git")
                .uri(URI.create("https://git.corp.com"))
                .build();
        assertEquals("/git.corp.com", p.servletPath());
    }

    @Test
    void generic_pathSuffix_overridesHostname() {
        var p = GenericProxyProvider.builder()
                .name("my-git")
                .uri(URI.create("https://git.corp.com"))
                .pathSuffix("/custom")
                .build();
        assertEquals("/custom", p.servletPath());
    }

    // --- InMemoryProviderRegistry ---

    @Test
    void registry_fromMap_getByFriendlyName() {
        var github = new GitHubProvider("/proxy");
        var registry = new InMemoryProviderRegistry(Map.of("github", github));
        assertSame(github, registry.getProvider("github").orElseThrow());
    }

    @Test
    void registry_fromMap_unknownName_returnsEmpty() {
        var registry = new InMemoryProviderRegistry(Map.of());
        assertTrue(registry.getProvider("unknown").isEmpty());
    }

    @Test
    void registry_fromList_getByName() {
        var github = new GitHubProvider("/proxy");
        var gitlab = new GitLabProvider("/proxy");
        var registry = new InMemoryProviderRegistry(List.of(github, gitlab));
        assertSame(github, registry.getProvider("github").orElseThrow());
        assertSame(gitlab, registry.getProvider("gitlab").orElseThrow());
    }

    @Test
    void registry_getProviders_returnsAll() {
        var github = new GitHubProvider("/proxy");
        var gitlab = new GitLabProvider("/proxy");
        var registry = new InMemoryProviderRegistry(Map.of("github", github, "gitlab", gitlab));
        assertEquals(2, registry.getProviders().size());
        assertTrue(registry.getProviders().containsAll(List.of(github, gitlab)));
    }

    @Test
    void registry_getProviders_empty() {
        var registry = new InMemoryProviderRegistry(Map.of());
        assertTrue(registry.getProviders().isEmpty());
    }

    @Test
    void registry_resolveProvider_byName() {
        var github = new GitHubProvider("/proxy");
        var registry = new InMemoryProviderRegistry(Map.of("github", github));
        assertSame(github, registry.resolveProvider("github").orElseThrow());
    }

    @Test
    void registry_resolveProvider_unknown_returnsEmpty() {
        var registry = new InMemoryProviderRegistry(Map.of());
        assertTrue(registry.resolveProvider("nonexistent").isEmpty());
    }

    @Test
    void registry_resolveProvider_null_returnsEmpty() {
        var registry = new InMemoryProviderRegistry(Map.of());
        assertTrue(registry.resolveProvider(null).isEmpty());
    }
}
