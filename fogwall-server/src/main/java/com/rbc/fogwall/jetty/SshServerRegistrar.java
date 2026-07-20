package com.rbc.fogwall.jetty;

import com.rbc.fogwall.config.JettyConfigurationBuilder;
import com.rbc.fogwall.config.SshConfig;
import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.ssh.SshGitServer;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Starts and stops the MINA SSHD server. Symmetric to {@link FogwallServletRegistrar} for the HTTP path.
 *
 * <p>SSH providers are identified by an {@code ssh://} URI scheme. The current MVP supports only a single SSH provider;
 * if more than one is configured a warning is logged and the first is used.
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

        List<FogwallProvider> sshProviders = allProviders.stream()
                .filter(p -> !FogwallServletRegistrar.isHttpProvider(p))
                .toList();

        if (sshProviders.isEmpty()) {
            log.warn("SSH transport enabled but no SSH providers configured — SSH server will not start");
            return null;
        }

        if (sshProviders.size() > 1) {
            log.warn(
                    "SSH transport MVP supports only a single provider; using '{}' (others ignored)",
                    sshProviders.get(0).getName());
        }

        FogwallProvider sshProvider = sshProviders.get(0);
        var factory = FogwallServletRegistrar.buildReceivePackFactory(ctx, configBuilder, sshProvider);
        SshGitServer server = SshGitServer.create(
                sshConfig, sshProvider, ctx.storeForwardCache(), factory, ctx.userStore(), ctx.urlRuleRegistry());
        server.start();
        return server;
    }
}
