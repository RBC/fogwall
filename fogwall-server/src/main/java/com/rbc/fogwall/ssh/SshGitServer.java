package com.rbc.fogwall.ssh;

import com.rbc.fogwall.config.SshConfig;
import com.rbc.fogwall.git.LocalRepositoryCache;
import com.rbc.fogwall.git.StoreAndForwardReceivePackFactory;
import com.rbc.fogwall.provider.FogwallProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;

/**
 * Wraps an Apache MINA SSHD server that accepts {@code git push} connections over SSH. On each push,
 * {@link SshGitCommandFactory} routes the {@code git-receive-pack} command to {@link SshGitReceiveCommand}, which runs
 * the same validation hook chain as the HTTP store-and-forward path.
 *
 * <p>Upstream authentication uses SSH agent forwarding. Clients must connect with agent forwarding enabled ({@code ssh
 * -A}, or {@code ForwardAgent yes} in {@code ~/.ssh/config}). Fogwall relays the forwarded agent to authenticate with
 * the upstream provider — no fogwall-held credentials are required.
 *
 * <p>MVP constraints:
 *
 * <ul>
 *   <li>Only a single provider is supported per server instance.
 *   <li>Authorized public keys are loaded once at startup from {@code server.ssh.authorized-keys}.
 *   <li>{@code git-upload-pack} (clone/fetch over SSH) is not yet implemented.
 * </ul>
 */
@Slf4j
public class SshGitServer {

    private final SshServer sshd;

    private SshGitServer(SshServer sshd) {
        this.sshd = sshd;
    }

    public static SshGitServer create(
            SshConfig config,
            FogwallProvider provider,
            LocalRepositoryCache cache,
            StoreAndForwardReceivePackFactory receivePackFactory)
            throws IOException {

        SshServer sshd = SshServer.setUpDefaultServer();
        sshd.setPort(config.getPort());

        Path hostKeyPath = Path.of(config.getHostKeyPath());
        Files.createDirectories(hostKeyPath.getParent());
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(hostKeyPath));
        log.info("SSH host key: {}", hostKeyPath.toAbsolutePath());

        List<PublicKey> authorizedKeys = parseAuthorizedKeys(config.getAuthorizedKeys());
        sshd.setPublickeyAuthenticator(buildAuthenticator(authorizedKeys));
        log.info("SSH authorized keys loaded: {} entries", authorizedKeys.size());

        // Enable inbound SSH agent forwarding — clients must connect with ssh -A.
        // ForwardingFilter defaults to null which silently rejects; must be set explicitly.
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

    private static List<PublicKey> parseAuthorizedKeys(List<String> keyLines) {
        List<PublicKey> keys = new ArrayList<>();
        for (String line : keyLines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            try {
                // Strip optional comment token — MINA SSHD's parser misidentifies the key type as a
                // hostname pattern when a "user@host" comment is present.
                String[] parts = trimmed.split("\\s+", 3);
                String keyLine = parts.length >= 2 ? parts[0] + " " + parts[1] : trimmed;
                AuthorizedKeyEntry entry = AuthorizedKeyEntry.parseAuthorizedKeyEntry(keyLine);
                PublicKey key = entry.resolvePublicKey(null, PublicKeyEntryResolver.IGNORING);
                if (key != null) {
                    keys.add(key);
                } else {
                    log.warn("Could not resolve public key (unsupported type?): {}", trimmed);
                }
            } catch (Exception e) {
                log.warn("Failed to parse authorized key entry: {}", trimmed, e);
            }
        }
        return keys;
    }

    private static PublickeyAuthenticator buildAuthenticator(List<PublicKey> authorizedKeys) {
        return (username, key, session) -> {
            for (PublicKey authorized : authorizedKeys) {
                if (KeyUtils.compareKeys(authorized, key)) {
                    log.debug("SSH public key accepted for user '{}'", username);
                    return true;
                }
            }
            log.warn("SSH public key rejected for user '{}'", username);
            return false;
        };
    }
}
