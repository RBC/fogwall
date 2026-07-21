package com.rbc.fogwall.ssh;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.junit.jupiter.api.Test;

/**
 * Verifies the SSH server's forwarding policy: agent forwarding is permitted (required to relay the client's agent for
 * upstream authentication), while TCP/IP port forwarding and X11 forwarding are denied so an authenticated client
 * cannot tunnel arbitrary connections through the proxy host into the internal network.
 */
class AgentOnlyForwardingFilterTest {

    private final SshGitServer.AgentOnlyForwardingFilter filter = new SshGitServer.AgentOnlyForwardingFilter();

    @Test
    void permitsAgentForwarding() {
        assertTrue(
                filter.canForwardAgent(null, "auth-agent-req@openssh.com"),
                "agent forwarding must be allowed — upstream auth relies on it");
    }

    @Test
    void deniesX11Forwarding() {
        assertFalse(filter.canForwardX11(null, "x11-req"), "X11 forwarding must be denied");
    }

    @Test
    void deniesRemotePortForwarding() {
        assertFalse(
                filter.canListen(new SshdSocketAddress("0.0.0.0", 8080), null),
                "remote TCP port forwarding (ssh -R) must be denied — no pivot through the proxy");
    }
}
