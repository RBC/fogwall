package com.rbc.fogwall.dashboard.controller;

import com.rbc.fogwall.config.FogwallConfig;
import com.rbc.fogwall.crypto.TokenCipherProvider;
import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.provider.ProviderRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves runtime configuration to the SPA. This endpoint is public (no auth required) so the frontend can fetch it
 * before the user logs in — e.g. to learn the API base URL when the frontend is served from a different origin.
 *
 * <p>This is the fogwall equivalent of the {@code runtime-config.json} file written by the Node.js fogwall
 * {@code docker-entrypoint.sh}. Serving it from Spring means it works in all environments (Gradle run, Docker, bare
 * JAR) without needing to inject files into the static asset directory at container startup.
 */
@Tag(name = "System", description = "Health check and API metadata")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RuntimeConfigController {

    private final FogwallConfig fogwallConfig;

    private final TokenCipherProvider tokenCipherProvider;

    private final ProviderRegistry providerRegistry;

    /**
     * One enabled-for-OAuth provider, as the frontend needs it to render a "Link via OAuth" button: {@code type} picks
     * the logo/brand (github, gitlab, forgejo), {@code hostname} is the actual host this instance talks to — needed
     * because a {@code github}/{@code gitlab}-type provider isn't always github.com/gitlab.com (GHE data residency,
     * self-managed GHES, self-hosted GitLab), and a {@code forgejo}-type provider covers everything from a generic
     * self-hosted instance to the specific hosted {@code codeberg.org}.
     */
    private record ScmOAuthProviderInfo(String id, String type, String hostname) {}

    @Operation(operationId = "getRuntimeConfig", summary = "Get frontend runtime configuration")
    @GetMapping("/runtime-config")
    public Map<String, Object> runtimeConfig() {
        List<String> allowedOrigins = fogwallConfig.getServer().getAllowedOrigins();
        String authProvider = fogwallConfig.getAuth().getProvider();
        boolean bulkReview = fogwallConfig.getDashboard().isBulkReview();

        List<ScmOAuthProviderInfo> scmOAuthProviders = fogwallConfig.getProviders().entrySet().stream()
                .filter(e -> e.getValue().getOauth().isEnabled()
                        && !e.getValue().getOauth().getClientId().isBlank())
                .map(e -> {
                    String id = e.getKey();
                    FogwallProvider provider = providerRegistry.getProvider(id).orElse(null);
                    String type = provider != null ? provider.getType() : "";
                    String hostname = provider != null ? provider.getUri().getHost() : "";
                    return new ScmOAuthProviderInfo(id, type, hostname);
                })
                .toList();
        boolean scmOAuthLinkAvailable = tokenCipherProvider.isAvailable();
        String scmIdentityMode = fogwallConfig.getScmOauth().getIdentityMode();

        return Map.of(
                "allowedOrigins", allowedOrigins,
                "authProvider", authProvider,
                "bulkReview", bulkReview,
                "scmOAuthProviders", scmOAuthProviders,
                "scmOAuthLinkAvailable", scmOAuthLinkAvailable,
                "scmIdentityMode", scmIdentityMode);
    }
}
