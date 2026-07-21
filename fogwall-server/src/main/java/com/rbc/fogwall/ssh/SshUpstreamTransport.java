package com.rbc.fogwall.ssh;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.agent.SshAgent;
import org.apache.sshd.agent.SshAgentConstants;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.apache.sshd.common.util.buffer.keys.BufferPublicKeyParser;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.sshd.DefaultProxyDataFactory;
import org.eclipse.jgit.transport.sshd.JGitKeyCache;
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.transport.sshd.agent.Connector;
import org.eclipse.jgit.transport.sshd.agent.ConnectorFactory;
import org.eclipse.jgit.transport.sshd.agent.ConnectorFactory.ConnectorDescriptor;

/**
 * Builds a JGit {@link TransportConfigCallback} that routes outbound SSH transport (to the upstream SCM) through a
 * MINA-forwarded {@link SshAgent} instead of loading identity files from disk. Shared by {@link SshGitReceiveCommand}
 * and {@link SshGitUploadCommand} - each push/fetch gets its own isolated {@link SshdSessionFactory} scoped to the
 * connecting client's forwarded agent, avoiding global mutation.
 */
@Slf4j
final class SshUpstreamTransport {

    private SshUpstreamTransport() {}

    /**
     * Builds a per-connection {@link TransportConfigCallback} backed by the given forwarded agent.
     *
     * @param agent the client's forwarded SSH agent used to authenticate to upstream
     * @param allowUnknownHostKey when {@code false} (production default), an upstream host key not present in the proxy
     *     user's {@code known_hosts} aborts the connection; when {@code true} (test/dev only), an unknown key is
     *     accepted with a warning. See {@code server.ssh.insecure-upstream-host-key}.
     */
    static TransportConfigCallback forwardedAgent(SshAgent agent, boolean allowUnknownHostKey) {
        SshdSessionFactory factory = new SshdSessionFactory(new JGitKeyCache(), new DefaultProxyDataFactory()) {
            @Override
            protected ConnectorFactory getConnectorFactory() {
                return new ConnectorFactory() {
                    @Override
                    public Connector create(String identityAgent, File homeDir) {
                        return new SshAgentConnector(agent);
                    }

                    @Override
                    public boolean isSupported() {
                        return true;
                    }

                    @Override
                    public String getName() {
                        return "forwarded-agent";
                    }

                    @Override
                    public java.util.Collection<ConnectorDescriptor> getSupportedConnectors() {
                        return List.of(getDefaultConnector());
                    }

                    @Override
                    public ConnectorDescriptor getDefaultConnector() {
                        return new ConnectorDescriptor() {
                            @Override
                            public String getIdentityAgent() {
                                return "SSH_AUTH_SOCK";
                            }

                            @Override
                            public String getDisplayName() {
                                return "Forwarded SSH Agent";
                            }
                        };
                    }
                };
            }

            @Override
            protected ServerKeyDatabase getServerKeyDatabase(File homeDir, File sshDir) {
                return new KnownHostsServerKeyDatabase(
                        super.getServerKeyDatabase(homeDir, sshDir), allowUnknownHostKey);
            }

            @Override
            protected String getDefaultPreferredAuthentications() {
                return "publickey";
            }

            @Override
            protected List<Path> getDefaultIdentities(File sshDir) {
                // Never load identity files from ~/.ssh — agent forwarding is the only auth path.
                return List.of();
            }
        };

        return transport -> {
            if (transport instanceof SshTransport sshTransport) {
                sshTransport.setSshSessionFactory(factory);
            }
        };
    }

    /**
     * Bridges MINA SSHD's in-process forwarded {@link SshAgent} into JGit's {@link Connector} interface. JGit uses
     * {@link Connector} as a raw SSH-agent wire-protocol transport; we implement the two operations fogwall needs —
     * {@code REQUEST_IDENTITIES} and {@code SIGN_REQUEST} — by delegating directly to the {@link SshAgent} methods and
     * re-encoding the responses in the expected wire format.
     */
    private static final class SshAgentConnector implements Connector {

        private final SshAgent agent;

        SshAgentConnector(SshAgent agent) {
            this.agent = agent;
        }

        @Override
        public boolean connect() throws IOException {
            return agent != null && agent.isOpen();
        }

        @Override
        public byte[] rpc(byte command, byte[] message) throws IOException {
            if (command == SshAgentConstants.SSH2_AGENTC_REQUEST_IDENTITIES) {
                return encodeIdentities(agent.getIdentities());
            }
            if (command == SshAgentConstants.SSH2_AGENTC_SIGN_REQUEST) {
                return sign(message);
            }
            throw new IOException("Unsupported SSH agent command: " + SshAgentConstants.getCommandMessageName(command));
        }

        private static byte[] encodeIdentities(Iterable<? extends Map.Entry<PublicKey, String>> identities)
                throws IOException {
            List<Map.Entry<PublicKey, String>> list = new ArrayList<>();
            for (var entry : identities) list.add(entry);

            ByteArrayBuffer buf = new ByteArrayBuffer();
            buf.putByte(SshAgentConstants.SSH2_AGENT_IDENTITIES_ANSWER);
            buf.putInt(list.size());
            for (var entry : list) {
                buf.putPublicKey(entry.getKey());
                buf.putString(entry.getValue());
            }
            return buf.getCompactData();
        }

        private byte[] sign(byte[] message) throws IOException {
            // Wire layout from SshAgentClient: [4-byte-length][command=13][pubkey-blob][data-blob][flags:int]
            // putPublicKey writes [4-byte-outer-len][keytype-len][keytype][key-material],
            // so use getPublicKey (not getRawPublicKey) to consume the outer length first.
            Buffer buf = new ByteArrayBuffer(message);
            buf.rpos(5); // skip 4-byte length prefix + 1-byte command byte
            PublicKey key = buf.getPublicKey(BufferPublicKeyParser.DEFAULT);
            byte[] data = buf.getBytes();
            int flags = buf.getInt();

            String keyType = KeyUtils.getKeyType(key);
            String algorithm =
                    switch (flags) {
                        case 4 -> KeyUtils.RSA_SHA512_KEY_TYPE_ALIAS;
                        case 2 -> KeyUtils.RSA_SHA256_KEY_TYPE_ALIAS;
                        default -> keyType;
                    };

            Map.Entry<String, byte[]> sig = agent.sign(null, key, algorithm, data);

            // Encode inner blob: [algo:string][sig-bytes:bytes]
            ByteArrayBuffer sigContent = new ByteArrayBuffer();
            sigContent.putString(sig.getKey());
            sigContent.putBytes(sig.getValue());

            // Response: [SSH2_AGENT_SIGN_RESPONSE][outer-length][inner-blob]
            ByteArrayBuffer reply = new ByteArrayBuffer();
            reply.putByte(SshAgentConstants.SSH2_AGENT_SIGN_RESPONSE);
            reply.putBytes(sigContent.getCompactData());
            return reply.getCompactData();
        }

        @Override
        public void close() {
            // Don't close — agent lifetime is tied to the inbound SSH session
        }
    }

    /**
     * Verifies the upstream SCM's SSH host key against the proxy user's {@code known_hosts} (via the delegate
     * {@link ServerKeyDatabase}) without ever blocking on an interactive TTY prompt.
     *
     * <p>A matching known key is accepted. An unknown or mismatched key is <b>rejected</b> by default — this is the
     * MITM protection for the agent-forwarded upstream leg. When {@code allowUnknown} is {@code true} (test/dev only,
     * via {@code server.ssh.insecure-upstream-host-key}), an unknown key is accepted with a warning so that ephemeral
     * upstreams which regenerate their host key don't break local testing.
     */
    private static final class KnownHostsServerKeyDatabase implements ServerKeyDatabase {

        private final ServerKeyDatabase delegate;
        private final boolean allowUnknown;

        KnownHostsServerKeyDatabase(ServerKeyDatabase delegate, boolean allowUnknown) {
            this.delegate = delegate;
            this.allowUnknown = allowUnknown;
        }

        @Override
        public List<PublicKey> lookup(
                String connectAddress, InetSocketAddress remoteAddress, ServerKeyDatabase.Configuration config) {
            return delegate.lookup(connectAddress, remoteAddress, config);
        }

        @Override
        public boolean accept(
                String connectAddress,
                InetSocketAddress remoteAddress,
                PublicKey serverKey,
                ServerKeyDatabase.Configuration config,
                CredentialsProvider provider) {
            List<PublicKey> known = delegate.lookup(connectAddress, remoteAddress, config);
            for (PublicKey k : known) {
                if (KeyUtils.compareKeys(k, serverKey)) {
                    return true;
                }
            }
            if (allowUnknown) {
                log.warn(
                        "SSH upstream: accepting UNKNOWN host key for {} — insecure-upstream-host-key is enabled "
                                + "(test/dev only; MUST NOT be used in production)",
                        connectAddress);
                return true;
            }
            log.error(
                    "SSH upstream: REJECTING unknown or mismatched host key for {} — add the upstream host key to the "
                            + "proxy user's known_hosts, or set server.ssh.insecure-upstream-host-key=true for testing",
                    connectAddress);
            return false;
        }
    }
}
