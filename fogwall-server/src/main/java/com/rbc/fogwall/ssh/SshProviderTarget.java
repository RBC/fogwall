package com.rbc.fogwall.ssh;

import com.rbc.fogwall.git.StoreAndForwardReceivePackFactory;
import com.rbc.fogwall.provider.FogwallProvider;

/**
 * Pairs an SSH-routed provider with its pre-built {@link StoreAndForwardReceivePackFactory}. {@link SshServerRegistrar}
 * builds one of these per configured SSH provider at startup (mirroring how the HTTP path builds one factory per
 * provider), keyed by {@link FogwallProvider#servletPath()} so {@link SshGitCommandFactory} can route each incoming
 * command to the right provider without rebuilding anything per-request.
 */
public record SshProviderTarget(FogwallProvider provider, StoreAndForwardReceivePackFactory receivePackFactory) {}
