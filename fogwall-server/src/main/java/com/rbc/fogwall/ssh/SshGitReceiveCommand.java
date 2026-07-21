package com.rbc.fogwall.ssh;

import com.rbc.fogwall.approval.ClientLivenessCheck;
import com.rbc.fogwall.git.LocalRepositoryCache;
import com.rbc.fogwall.git.PushTransport;
import com.rbc.fogwall.git.StoreAndForwardReceivePackFactory;
import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.user.UserEntry;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.agent.SshAgent;
import org.apache.sshd.common.Closeable;
import org.apache.sshd.common.channel.exception.SshChannelClosedException;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceivePack;

/**
 * MINA SSHD {@link Command} that handles {@code git-receive-pack} over SSH. Delegates to the same
 * {@link StoreAndForwardReceivePackFactory} hook chain used by the HTTP push path.
 *
 * <p>Upstream authentication uses the client's forwarded SSH agent (requires {@code ssh -A}) via
 * {@link SshUpstreamTransport}, shared with {@link SshGitUploadCommand}.
 */
@Slf4j
public class SshGitReceiveCommand implements Command {

    private final String repoPath;
    private final FogwallProvider provider;
    private final LocalRepositoryCache cache;
    private final StoreAndForwardReceivePackFactory receivePackFactory;
    private final FogwallProxyAgentFactory agentFactory;
    private final Path knownHostsFile;
    private final boolean trustOnFirstUse;

    private InputStream in;
    private OutputStream out;
    private OutputStream err;
    private ExitCallback exitCallback;

    SshGitReceiveCommand(
            String repoPath,
            FogwallProvider provider,
            LocalRepositoryCache cache,
            StoreAndForwardReceivePackFactory receivePackFactory,
            FogwallProxyAgentFactory agentFactory,
            Path knownHostsFile,
            boolean trustOnFirstUse) {
        this.repoPath = repoPath;
        this.provider = provider;
        this.cache = cache;
        this.receivePackFactory = receivePackFactory;
        this.agentFactory = agentFactory;
        this.knownHostsFile = knownHostsFile;
        this.trustOnFirstUse = trustOnFirstUse;
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

            TransportConfigCallback transportConfig =
                    SshUpstreamTransport.forwardedAgent(agent, knownHostsFile, trustOnFirstUse);
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
}
