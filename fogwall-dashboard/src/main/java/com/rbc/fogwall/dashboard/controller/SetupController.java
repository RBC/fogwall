package com.rbc.fogwall.dashboard.controller;

import com.rbc.fogwall.config.FogwallConfig;
import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.provider.ProviderRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import java.net.URI;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Serves the in-app developer setup guide (fogwall#475): per-provider, copy-pasteable {@code ~/.gitconfig} snippets
 * that route git traffic through this deployment via URL rewriting, generated from the running configuration so they
 * cannot drift from what fogwall actually serves.
 *
 * <p>Public (no auth) — a developer who cannot yet log in is exactly who needs setup instructions, and fogwall is
 * deployed in environments where the GitHub-hosted docs are blocked. It exposes only routing information already
 * implied by the provider list and the service URL; no secrets.
 */
@Tag(name = "Setup", description = "Developer onboarding: generated git configuration")
@RestController
public class SetupController {

    @Resource(name = "providers")
    private ProviderRegistry providers;

    @Resource(name = "fogwallConfig")
    private FogwallConfig fogwallConfig;

    @Operation(operationId = "getSetup", summary = "Generated git setup configuration for this deployment")
    @GetMapping("/api/setup")
    public SetupInfo setup() {
        String configured = fogwallConfig.getServer().getServiceUrl();
        boolean serviceUrlConfigured = configured != null && !configured.isBlank();
        // service-url is authoritative (it is what fogwall is externally reachable at, correct behind a reverse
        // proxy). When unset, fall back to the URL the browser used to reach this endpoint so the page still works;
        // the frontend flags this so an operator knows to set service-url for deployments behind a proxy.
        String base = serviceUrlConfigured
                ? stripTrailingSlash(configured.trim())
                : ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        String sshHost = URI.create(base).getHost();

        boolean sshServerEnabled = fogwallConfig.getServer().getSsh().isEnabled();
        int sshPort = fogwallConfig.getServer().getSsh().getPort();

        List<SetupProvider> setupProviders = providers.getProviders().stream()
                .map(p -> toSetupProvider(p, base, sshHost, sshServerEnabled, sshPort))
                .toList();

        return new SetupInfo(base, serviceUrlConfigured, setupProviders);
    }

    private SetupProvider toSetupProvider(
            FogwallProvider p, String base, String sshHost, boolean sshServerEnabled, int sshPort) {
        boolean sshEnabled = sshServerEnabled && p.getSshUri().isPresent();
        return new SetupProvider(
                p.getName(),
                p.getProviderId(),
                p.getType(),
                p.getUri().getHost(),
                stripTrailingSlash(p.getUri().toString()),
                GitSetupConfigGenerator.serverUrl(base, p),
                GitSetupConfigGenerator.httpPush(base, p),
                GitSetupConfigGenerator.httpRead(base, p),
                GitSetupConfigGenerator.httpPerRepo(base, p),
                sshEnabled,
                sshEnabled ? GitSetupConfigGenerator.sshPush(sshHost, sshPort, p) : null,
                sshEnabled ? GitSetupConfigGenerator.sshPerRepo(sshHost, sshPort, p) : null);
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    /**
     * @param serviceUrl the base URL the generated config routes to (service-url if set, otherwise derived from the
     *     request)
     * @param serviceUrlConfigured whether {@code server.service-url} was set — when false the base URL was derived from
     *     the request and may be wrong behind a reverse proxy
     */
    public record SetupInfo(String serviceUrl, boolean serviceUrlConfigured, List<SetupProvider> providers) {}

    /**
     * Generated git configuration for one provider (fogwall#475). Push-only by default:
     * {@code httpPush}/{@code sshPush} reroute only pushes; {@code httpRead} is the opt-in to also route fetches; the
     * {@code perRepo} variants are the explicit per-repository alternative to the global {@code ~/.gitconfig} blocks.
     *
     * @param serverUrl the {@code /server} clone/push URL prefix through fogwall (e.g.
     *     {@code https://fw/server/github.com/}), used by the quick-start clone command
     * @param httpPush global push-only {@code ~/.gitconfig} block (HTTPS) — reroutes pushes, leaves fetches direct
     * @param httpRead opt-in global block (HTTPS) to also route fetches through fogwall's proxy
     * @param httpPerRepo per-repository HTTPS commands (clone + {@code git remote set-url --push})
     * @param sshPush global push-only SSH block, or {@code null} when the provider does not serve SSH
     * @param sshPerRepo per-repository SSH commands, or {@code null} when the provider does not serve SSH
     */
    public record SetupProvider(
            String name,
            String id,
            String type,
            String host,
            String upstreamUrl,
            String serverUrl,
            String httpPush,
            String httpRead,
            String httpPerRepo,
            boolean sshEnabled,
            String sshPush,
            String sshPerRepo) {}
}
