package com.rbc.fogwall.ssh;

import com.rbc.fogwall.db.UrlRuleRegistry;
import com.rbc.fogwall.git.LocalRepositoryCache;
import com.rbc.fogwall.git.StoreAndForwardReceivePackFactory;
import com.rbc.fogwall.provider.FogwallProvider;
import java.io.IOException;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.command.CommandFactory;

/**
 * MINA SSHD {@link CommandFactory} that maps {@code git-receive-pack} SSH commands to {@link SshGitReceiveCommand} and
 * {@code git-upload-pack} commands to {@link SshGitUploadCommand}.
 */
@Slf4j
@RequiredArgsConstructor
public class SshGitCommandFactory implements CommandFactory {

    private final FogwallProvider provider;
    private final LocalRepositoryCache cache;
    private final StoreAndForwardReceivePackFactory receivePackFactory;
    private final FogwallProxyAgentFactory agentFactory;
    private final UrlRuleRegistry urlRuleRegistry;

    /** Assembled upstream known_hosts file (or {@code null} to use the default) — see {@link UpstreamKnownHosts}. */
    private final Path knownHostsFile;

    /** See {@link com.rbc.fogwall.config.SshConfig#isTrustOnFirstUse()}. */
    private final boolean trustOnFirstUse;

    @Override
    public Command createCommand(ChannelSession channel, String command) throws IOException {
        log.debug("SSH command received: {}", command);

        if (command.startsWith("git-receive-pack ")) {
            String repoPath =
                    stripQuotes(command.substring("git-receive-pack ".length()).trim());
            return new SshGitReceiveCommand(
                    repoPath, provider, cache, receivePackFactory, agentFactory, knownHostsFile, trustOnFirstUse);
        }

        if (command.startsWith("git-upload-pack ")) {
            String repoPath =
                    stripQuotes(command.substring("git-upload-pack ".length()).trim());
            return new SshGitUploadCommand(
                    repoPath, provider, cache, agentFactory, urlRuleRegistry, knownHostsFile, trustOnFirstUse);
        }

        log.warn("Unsupported SSH git command: {}", command);
        throw new IOException("Unsupported command: " + command
                + " (only git-receive-pack and git-upload-pack are supported over SSH)");
    }

    /** Strips surrounding single or double quotes added by the git client. */
    private static String stripQuotes(String rawPath) {
        return rawPath.replaceAll("^['\"]|['\"]$", "");
    }
}
