package com.rbc.fogwall.ssh;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
     * @param knownHostsFile the assembled {@code known_hosts} file to verify upstream host keys against (see
     *     {@link UpstreamKnownHosts}); {@code null} falls back to the proxy user's {@code ~/.ssh/known_hosts}
     * @param trustOnFirstUse when {@code true}, an upstream host key not in {@code knownHostsFile} is pinned on first
     *     use (and logged) rather than rejected; a later change to that host's key is still rejected. See
     *     {@code server.ssh.trust-on-first-use}.
     */
    static TransportConfigCallback forwardedAgent(SshAgent agent, Path knownHostsFile, boolean trustOnFirstUse) {
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
                File effectiveSshDir =
                        knownHostsFile != null ? knownHostsFile.getParent().toFile() : sshDir;
                return new KnownHostsServerKeyDatabase(
                        super.getServerKeyDatabase(homeDir, effectiveSshDir), trustOnFirstUse);
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
     * Verifies the upstream SCM's SSH host key against the assembled {@code known_hosts} (via the delegate
     * {@link ServerKeyDatabase}) without ever blocking on an interactive TTY prompt.
     *
     * <p>A key already present in known_hosts is accepted. Otherwise, when {@code trustOnFirstUse} is enabled, the key
     * is pinned for the process lifetime and accepted (logged loudly with its fingerprint); a later change to that
     * host's key is then rejected — standard trust-on-first-use. When {@code trustOnFirstUse} is disabled (default), an
     * unknown key is <b>rejected</b> — the MITM protection for the agent-forwarded upstream leg.
     */
    private static final class KnownHostsServerKeyDatabase implements ServerKeyDatabase {

        /**
         * Host keys pinned via trust-on-first-use, shared across all per-connection instances so a key seen on one
         * connection is verified (and a mismatch rejected) on the next. Persists for the JVM lifetime; a restart
         * re-establishes trust on first use.
         */
        private static final Map<String, PublicKey> TOFU_PINNED = new ConcurrentHashMap<>();

        private final ServerKeyDatabase delegate;
        private final boolean trustOnFirstUse;

        KnownHostsServerKeyDatabase(ServerKeyDatabase delegate, boolean trustOnFirstUse) {
            this.delegate = delegate;
            this.trustOnFirstUse = trustOnFirstUse;
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
            for (PublicKey k : delegate.lookup(connectAddress, remoteAddress, config)) {
                if (KeyUtils.compareKeys(k, serverKey)) {
                    return true;
                }
            }

            PublicKey pinned = TOFU_PINNED.get(connectAddress);
            if (pinned != null) {
                if (KeyUtils.compareKeys(pinned, serverKey)) {
                    return true;
                }
                log.error(
                        "SSH upstream: REJECTING host key for {} — it CHANGED since it was trusted on first use "
                                + "(possible MITM, or the upstream rotated its key: pin the new key to accept it)",
                        connectAddress);
                return false;
            }

            if (trustOnFirstUse) {
                TOFU_PINNED.put(connectAddress, serverKey);
                log.warn(
                        "SSH upstream: trust-on-first-use — pinning host key for {} (fingerprint {}). Verify this "
                                + "against the provider's published fingerprint; pin it to remove this warning.",
                        connectAddress,
                        KeyUtils.getFingerPrint(serverKey));
                return true;
            }

            log.error(
                    "SSH upstream: REJECTING unknown host key for {} — pin it via server.ssh.extra-known-hosts or "
                            + "server.ssh.known-hosts-path, or enable server.ssh.trust-on-first-use",
                    connectAddress);
            return false;
        }
    }
}
