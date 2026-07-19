package com.rbc.fogwall.ssh;

import com.rbc.fogwall.approval.ClientLivenessCheck;
import com.rbc.fogwall.git.LocalRepositoryCache;
import com.rbc.fogwall.git.PushTransport;
import com.rbc.fogwall.git.StoreAndForwardReceivePackFactory;
import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.user.UserEntry;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.agent.SshAgent;
import org.apache.sshd.agent.SshAgentConstants;
import org.apache.sshd.common.Closeable;
import org.apache.sshd.common.channel.exception.SshChannelClosedException;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.apache.sshd.common.util.buffer.keys.BufferPublicKeyParser;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.sshd.DefaultProxyDataFactory;
import org.eclipse.jgit.transport.sshd.JGitKeyCache;
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.transport.sshd.agent.Connector;
import org.eclipse.jgit.transport.sshd.agent.ConnectorFactory;
import org.eclipse.jgit.transport.sshd.agent.ConnectorFactory.ConnectorDescriptor;

/**
 * MINA SSHD {@link Command} that handles {@code git-receive-pack} over SSH. Delegates to the same
 * {@link StoreAndForwardReceivePackFactory} hook chain used by the HTTP push path.
 *
 * <p>Upstream authentication uses the client's forwarded SSH agent (requires {@code ssh -A}). The agent is retrieved
 * from the inbound MINA SSHD session and exposed to JGit's outbound SSH transport via a {@link Connector} bridge that
 * translates JGit's wire-protocol RPC calls into {@link SshAgent} method invocations.
 */
@Slf4j
public class SshGitReceiveCommand implements Command {

    private final String repoPath;
    private final FogwallProvider provider;
    private final LocalRepositoryCache cache;
    private final StoreAndForwardReceivePackFactory receivePackFactory;
    private final FogwallProxyAgentFactory agentFactory;

    private InputStream in;
    private OutputStream out;
    private OutputStream err;
    private ExitCallback exitCallback;

    SshGitReceiveCommand(
            String repoPath,
            FogwallProvider provider,
            LocalRepositoryCache cache,
            StoreAndForwardReceivePackFactory receivePackFactory,
            FogwallProxyAgentFactory agentFactory) {
        this.repoPath = repoPath;
        this.provider = provider;
        this.cache = cache;
        this.receivePackFactory = receivePackFactory;
        this.agentFactory = agentFactory;
    }

    @Override
    public void setInputStream(InputStream in) {
        this.in = in;
    }

    @Override
    public void setOutputStream(OutputStream out) {
        this.out = out;
    }

    @Override
    public void setErrorStream(OutputStream err) {
        this.err = err;
    }

    @Override
    public void setExitCallback(ExitCallback callback) {
        this.exitCallback = callback;
    }

    @Override
    public void start(ChannelSession channel, Environment env) {
        String sshUser = channel.getSession().getUsername();
        // Resolved during public-key auth — may be absent if the store returned empty (shouldn't happen
        // since auth would have been rejected, but guard defensively).
        var serverSession = (ServerSession) channel.getSession();
        UserEntry resolvedUser = SshGitServer.getResolvedUser(serverSession).orElse(null);
        String connectingFingerprint =
                SshGitServer.getConnectingFingerprint(serverSession).orElse(null);
        // SSH_AUTH_SOCK is set on the channel env by MINA SSHD when the client's ssh -A forwarding
        // channel is established. Capture it here before handing off to the worker thread.
        String authSocket = env.getEnv().get(SshAgent.SSH_AUTHSOCKET_ENV_NAME);
        // The session's own close state is maintained by MINA's read loop independent of this worker
        // thread, so it reflects a client disconnect even while the worker sits in the approval-wait poll.
        ClientLivenessCheck liveness = serverSession instanceof Closeable closeable
                ? () -> !closeable.isClosing()
                : ClientLivenessCheck.alwaysConnected();
        Thread worker = new Thread(
                () -> runReceivePack(sshUser, resolvedUser, connectingFingerprint, authSocket, liveness),
                "ssh-git-receive-" + repoPath);
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Parses an SSH git path and validates it against the configured provider.
     *
     * <p>Path format: {@code /{host}:{port}/{owner}/{repo}.git} — mirrors the HTTP
     * {@code /push/{host}:{port}/{owner}/{repo}.git} convention.
     *
     * @return a {@link RepoRoute} if the path is valid and matches the provider
     * @throws IllegalArgumentException if the path is malformed or doesn't match the provider
     */
    static RepoRoute resolveRoute(String repoPath, java.net.URI providerUri) {
        String normalised = repoPath.startsWith("/") ? repoPath : "/" + repoPath;
        String[] segments = normalised.replaceAll("\\.git$", "").split("/", 4);
        if (segments.length < 4) {
            throw new IllegalArgumentException(
                    "Invalid repository path: " + repoPath + " (expected /{host}:{port}/{owner}/{repo}.git)");
        }
        String providerSegment = segments[1];
        String owner = segments[2];
        String repo = segments[3];

        int uriPort = providerUri.getPort();
        String expectedSegment = uriPort > 0 ? providerUri.getHost() + ":" + uriPort : providerUri.getHost();
        if (!providerSegment.equals(expectedSegment)) {
            throw new IllegalArgumentException("Unknown provider host '" + providerSegment + "' in path: " + repoPath
                    + " (expected " + expectedSegment + ")");
        }
        return new RepoRoute(owner, repo, providerUri + "/" + owner + "/" + repo + ".git", "/" + owner + "/" + repo);
    }

    record RepoRoute(String owner, String repo, String upstreamUrl, String repoSlug) {}

    private void runReceivePack(
            String sshUser,
            UserEntry resolvedUser,
            String connectingFingerprint,
            String authSocket,
            ClientLivenessCheck liveness) {
        int exitCode = 0;
        SshAgent agent = null;
        try {
            RepoRoute route;
            try {
                route = resolveRoute(repoPath, provider.getUri());
            } catch (IllegalArgumentException e) {
                writeError(e.getMessage());
                exitCode = 128;
                return;
            }
            String owner = route.owner();
            String repo = route.repo();
            String upstreamUrl = route.upstreamUrl();
            String repoSlug = route.repoSlug();

            // Look up the forwarded SSH agent by the proxy ID that MINA SSHD stored in the
            // channel env as SSH_AUTH_SOCK when the client's ssh -A request was processed.
            agent = agentFactory.getForwardedAgent(authSocket);
            if (agent == null || !agent.isOpen()) {
                writeError(
                        "SSH agent forwarding required — connect with 'ssh -A' or set 'ForwardAgent yes' in ~/.ssh/config");
                exitCode = 128;
                return;
            }

            log.info("SSH git-receive-pack: user='{}' path={} -> {}", sshUser, repoPath, upstreamUrl);

            SshAgent capturedAgent = agent;
            SshdSessionFactory perPushFactory =
                    new SshdSessionFactory(new JGitKeyCache(), new DefaultProxyDataFactory()) {
                        @Override
                        protected ConnectorFactory getConnectorFactory() {
                            return new ConnectorFactory() {
                                @Override
                                public Connector create(String identityAgent, File homeDir) {
                                    return new SshAgentConnector(capturedAgent);
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
                            return new AcceptAllKnownServerKeyDatabase(super.getServerKeyDatabase(homeDir, sshDir));
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

            // Inject the per-push factory via TransportConfigCallback — each push gets its own
            // isolated SSH session factory scoped to the forwarded agent, avoiding global mutation.
            TransportConfigCallback transportConfig = transport -> {
                if (transport instanceof SshTransport sshTransport) {
                    sshTransport.setSshSessionFactory(perPushFactory);
                }
            };

            var pushTransport = new PushTransport.Ssh(resolvedUser, connectingFingerprint, transportConfig, liveness);

            Repository localRepo = cache.getOrClone(upstreamUrl, null, transportConfig);
            localRepo.getConfig().setString("fogwall", null, "upstreamUrl", upstreamUrl);
            localRepo.getConfig().save();

            ReceivePack rp = receivePackFactory.createForSsh(localRepo, sshUser, repoSlug, pushTransport);
            rp.setBiDirectionalPipe(true); // factory defaults to false for HTTP; SSH is bidirectional
            rp.receive(in, out, err);

        } catch (SshChannelClosedException e) {
            // Expected when the client disconnects mid-wait: ReceivePack.close() tries to flush final sideband
            // data after we've already detected the disconnect and canceled the push. Nothing left to report to.
            log.debug("SSH channel already closed for {} (client disconnected)", repoPath);
            exitCode = 1;
        } catch (Exception e) {
            log.error("SSH git-receive-pack failed for {}", repoPath, e);
            writeError("Internal error: " + e.getMessage());
            exitCode = 1;
        } finally {
            if (agent != null) {
                try {
                    agent.close();
                } catch (IOException ignored) {
                }
            }
            if (exitCallback != null) {
                exitCallback.onExit(exitCode);
            }
        }
    }

    private void writeError(String message) {
        try {
            err.write(("error: " + message + "\n").getBytes());
            err.flush();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void destroy(ChannelSession channel) {}

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
     * Wraps a delegate {@link ServerKeyDatabase} but never blocks waiting for user interaction. If the upstream host
     * key is not in known_hosts, it is accepted with a warning rather than hanging on a TTY prompt.
     */
    private static final class AcceptAllKnownServerKeyDatabase implements ServerKeyDatabase {

        private final ServerKeyDatabase delegate;

        AcceptAllKnownServerKeyDatabase(ServerKeyDatabase delegate) {
            this.delegate = delegate;
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
            log.warn(
                    "SSH upstream: accepting UNKNOWN host key for {} — add to ~/.ssh/known_hosts to suppress",
                    connectAddress);
            return true;
        }
    }
}
