package com.rbc.fogwall.ssh;

import com.rbc.fogwall.config.SshConfig;
import com.rbc.fogwall.git.LocalRepositoryCache;
import com.rbc.fogwall.git.StoreAndForwardReceivePackFactory;
import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.user.ReadOnlyUserStore;
import com.rbc.fogwall.user.UserEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;

/**
 * Wraps an Apache MINA SSHD server that accepts {@code git push} connections over SSH. On each push,
 * {@link SshGitCommandFactory} routes the {@code git-receive-pack} command to {@link SshGitReceiveCommand}, which runs
 * the same validation hook chain as the HTTP store-and-forward path.
 *
 * <p>Upstream authentication uses SSH agent forwarding. Clients must connect with agent forwarding enabled ({@code ssh
 * -A}, or {@code ForwardAgent yes} in {@code ~/.ssh/config}). Fogwall relays the forwarded agent to authenticate with
 * the upstream provider — no fogwall-held credentials are required.
 *
 * <p>Clients authenticate by registering their SSH public key on their fogwall profile page. The key fingerprint is
 * looked up in the user store at connection time to resolve their {@link UserEntry}, which is then stored on the MINA
 * session for use by {@link SshGitReceiveCommand}.
 *
 * <p>MVP constraints:
 *
 * <ul>
 *   <li>Only a single provider is supported per server instance.
 *   <li>{@code git-upload-pack} (clone/fetch over SSH) is not yet implemented.
 * </ul>
 */
@Slf4j
public class SshGitServer {

    /** Session attribute key under which the resolved {@link UserEntry} is stored after public-key auth. */
    static final String SESSION_USER_ATTR = "fogwall.resolvedUser";

    private final SshServer sshd;

    private SshGitServer(SshServer sshd) {
        this.sshd = sshd;
    }

    public static SshGitServer create(
            SshConfig config,
            FogwallProvider provider,
            LocalRepositoryCache cache,
            StoreAndForwardReceivePackFactory receivePackFactory,
            ReadOnlyUserStore userStore)
            throws IOException {

        SshServer sshd = SshServer.setUpDefaultServer();
        sshd.setPort(config.getPort());

        Path hostKeyPath = Path.of(config.getHostKeyPath());
        Files.createDirectories(hostKeyPath.getParent());
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(hostKeyPath));
        log.info("SSH host key: {}", hostKeyPath.toAbsolutePath());

        sshd.setPublickeyAuthenticator(buildAuthenticator(userStore));

        // Enable inbound SSH agent forwarding — clients must connect with ssh -A.
        FogwallProxyAgentFactory agentFactory = new FogwallProxyAgentFactory();
        sshd.setAgentFactory(agentFactory);
        sshd.setForwardingFilter(AcceptAllForwardingFilter.INSTANCE);

        sshd.setCommandFactory(new SshGitCommandFactory(provider, cache, receivePackFactory, agentFactory));

        return new SshGitServer(sshd);
    }

    public void start() throws IOException {
        sshd.start();
        log.info("SSH git server listening on port {}", sshd.getPort());
    }

    public void stop() {
        try {
            sshd.stop(true);
        } catch (IOException e) {
            log.warn("Error stopping SSH server", e);
        }
    }

    private static PublickeyAuthenticator buildAuthenticator(ReadOnlyUserStore userStore) {
        return (sshUsername, key, session) -> {
            String fingerprint;
            try {
                fingerprint = SshKeyUtils.fingerprint(key);
            } catch (Exception e) {
                log.warn("Could not fingerprint SSH key during auth: {}", e.getMessage());
                return false;
            }

            Optional<UserEntry> user = userStore.findBySshFingerprint(fingerprint);
            if (user.isEmpty()) {
                log.warn("SSH auth rejected — no user registered for key fingerprint {}", fingerprint);
                return false;
            }

            log.debug(
                    "SSH auth accepted: fingerprint {} -> user '{}'",
                    fingerprint,
                    user.get().getUsername());
            storeResolvedUser(session, user.get());
            return true;
        };
    }

    static void storeResolvedUser(ServerSession session, UserEntry user) {
        session.getProperties().put(SESSION_USER_ATTR, user);
    }

    static Optional<UserEntry> getResolvedUser(ServerSession session) {
        Object val = session.getProperties().get(SESSION_USER_ATTR);
        return val instanceof UserEntry u ? Optional.of(u) : Optional.empty();
    }
}
