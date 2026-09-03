package com.rbc.fogwall.ssh;

import com.rbc.fogwall.config.JettyConfigurationBuilder;
import com.rbc.fogwall.config.SshConfig;
import com.rbc.fogwall.jetty.FogwallContext;
import com.rbc.fogwall.jetty.FogwallServletRegistrar;
import com.rbc.fogwall.provider.FogwallProvider;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Starts and stops the MINA SSHD server. Symmetric to {@link FogwallServletRegistrar} for the HTTP path.
 *
 * <p>A provider serves SSH when its {@code ssh:} config sub-block resolves an SSH endpoint — see
 * {@link FogwallProvider#getSshUri()}. The same provider entry may also serve HTTP (via its {@code uri}); the two
 * transports share one config entry (fogwall#531). Every SSH-enabled provider is routed by its
 * {@link FogwallProvider#servletPath()} (the same host/port/path-suffix key the HTTP path uses), so a single SSH server
 * instance can serve multiple upstream providers on one port — the client selects which one with the path segment in
 * the SSH command, e.g. {@code ssh://fogwall:2222/github.com/owner/repo.git}.
 */
@Slf4j
public class SshServerRegistrar {

    private SshServerRegistrar() {}

    /**
     * Starts the SSH git server if SSH is enabled and at least one SSH provider is configured. Returns the running
     * {@link SshGitServer}, or {@code null} if SSH is disabled or no SSH providers are present.
     */
    public static SshGitServer startIfEnabled(
            SshConfig sshConfig,
            List<FogwallProvider> allProviders,
            FogwallContext ctx,
            JettyConfigurationBuilder configBuilder)
            throws IOException {

        if (!sshConfig.isEnabled()) {
            return null;
        }

        List<FogwallProvider> sshProviders =
                allProviders.stream().filter(p -> p.getSshUri().isPresent()).toList();

        if (sshProviders.isEmpty()) {
            log.warn("SSH transport enabled but no SSH providers configured — SSH server will not start");
            return null;
        }

        Map<String, SshProviderTarget> routes = buildRoutes(sshProviders, ctx, configBuilder);

        SshGitServer server = SshGitServer.create(
                sshConfig,
                routes,
                ctx.storeForwardCache(),
                ctx.userStore(),
                ctx.urlRuleRegistry(),
                configBuilder.buildUpstreamKnownHosts());
        server.start();
        return server;
    }

    private static Map<String, SshProviderTarget> buildRoutes(
            List<FogwallProvider> sshProviders, FogwallContext ctx, JettyConfigurationBuilder configBuilder) {
        Map<String, SshProviderTarget> routes = new LinkedHashMap<>();
        for (FogwallProvider provider : sshProviders) {
            String key = provider.servletPath();
            SshProviderTarget existing = routes.get(key);
            if (existing != null) {
                throw new IllegalStateException("Duplicate SSH provider path '" + key + "' for providers '"
                        + existing.provider().getName() + "' and '" + provider.getName()
                        + "' — configure distinct hosts or pathSuffix values");
            }
            var factory = FogwallServletRegistrar.buildReceivePackFactory(ctx, configBuilder, provider);
            routes.put(key, new SshProviderTarget(provider, factory));
            log.info("Routing SSH path '{}' -> provider '{}'", key, provider.getName());
        }
        return routes;
    }
}
