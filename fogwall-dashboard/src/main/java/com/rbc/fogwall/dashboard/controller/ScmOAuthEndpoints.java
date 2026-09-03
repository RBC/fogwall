package com.rbc.fogwall.dashboard.controller;

import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.provider.ForgejoProvider;
import com.rbc.fogwall.provider.GitHubProvider;
import com.rbc.fogwall.provider.GitLabProvider;

/**
 * Resolves the authorize/token/user-API endpoints for SCM OAuth account linking (#40) from an already-configured
 * {@link FogwallProvider} instance — the OAuth host is never a separate config value, it is always this instance's own
 * {@code uri} (already correct per-instance, including the GitHub github.com / {@code *.ghe.com} data-residency /
 * self-managed GHES distinction {@link GitHubProvider#getApiUrl()} already handles for git push routing).
 */
final class ScmOAuthEndpoints {

    record Endpoints(String authorizeUrl, String tokenUrl, String userApiUrl) {}

    private ScmOAuthEndpoints() {}

    static Endpoints resolve(FogwallProvider provider) {
        return switch (provider.getType()) {
            case "github" -> {
                var github = (GitHubProvider) provider;
                yield new Endpoints(
                        github.getOAuthAuthorizeUrl(), github.getOAuthTokenUrl(), github.getApiUrl() + "/user");
            }
            case "gitlab" -> {
                var gitlab = (GitLabProvider) provider;
                yield new Endpoints(
                        gitlab.getOAuthUrl() + "/authorize",
                        gitlab.getOAuthUrl() + "/token",
                        gitlab.getApiUrl() + "/user");
            }
            case "forgejo" -> {
                var forgejo = (ForgejoProvider) provider;
                yield new Endpoints(
                        forgejo.getOAuthAuthorizeUrl(), forgejo.getOAuthTokenUrl(), forgejo.getApiUrl() + "/user");
            }
            default ->
                throw new IllegalArgumentException(
                        "SCM OAuth linking is not supported for provider type '" + provider.getType() + "'");
        };
    }
}
