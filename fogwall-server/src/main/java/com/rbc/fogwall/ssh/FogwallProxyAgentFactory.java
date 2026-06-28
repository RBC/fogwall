package com.rbc.fogwall.ssh;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.sshd.agent.SshAgent;
import org.apache.sshd.agent.SshAgentFactory;
import org.apache.sshd.agent.SshAgentServer;
import org.apache.sshd.agent.local.AgentServerProxy;
import org.apache.sshd.agent.local.LocalAgentFactory;
import org.apache.sshd.common.FactoryManager;
import org.apache.sshd.common.channel.ChannelFactory;
import org.apache.sshd.common.session.ConnectionService;
import org.apache.sshd.common.session.Session;

/**
 * {@link SshAgentFactory} for server-side SSH agent forwarding. Mirrors {@code ProxyAgentFactory} but exposes
 * {@link #getForwardedAgent(String)} so that {@link SshGitReceiveCommand} can retrieve the inbound client's agent by
 * the proxy ID stored in the channel environment ({@code SSH_AUTH_SOCK}).
 */
class FogwallProxyAgentFactory implements SshAgentFactory {

    private final Map<String, AgentServerProxy> proxies = new ConcurrentHashMap<>();

    @Override
    public List<ChannelFactory> getChannelForwardingFactories(FactoryManager manager) {
        return LocalAgentFactory.DEFAULT_FORWARDING_CHANNELS;
    }

    @Override
    public SshAgentServer createServer(ConnectionService service) throws IOException {
        AgentServerProxy proxy = new AgentServerProxy(service);
        proxies.put(proxy.getId(), proxy);
        return new SshAgentServer() {
            private final AtomicBoolean open = new AtomicBoolean(true);

            @Override
            public String getId() {
                return proxy.getId();
            }

            @Override
            public boolean isOpen() {
                return open.get() && proxy.isOpen();
            }

            @Override
            public void close() throws IOException {
                if (open.getAndSet(false)) {
                    proxies.remove(proxy.getId());
                    proxy.close();
                }
            }
        };
    }

    /**
     * Returns an {@link SshAgent} backed by the forwarded agent channel for the given proxy ID. The ID is the value
     * stored under {@code SSH_AUTH_SOCK} in the channel's environment after the client's agent-forwarding channel
     * request is processed.
     *
     * @param proxyId the proxy ID from the channel environment
     * @return the agent, or {@code null} if no forwarded agent exists for this ID
     */
    SshAgent getForwardedAgent(String proxyId) throws IOException {
        if (proxyId == null || proxyId.isBlank()) {
            return null;
        }
        AgentServerProxy proxy = proxies.get(proxyId);
        return proxy != null ? proxy.createClient() : null;
    }

    @Override
    public SshAgent createClient(Session session, FactoryManager manager) throws IOException {
        throw new UnsupportedOperationException("Use getForwardedAgent(proxyId) for server-side agent access");
    }
}
