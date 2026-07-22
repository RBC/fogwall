package com.rbc.fogwall.ssh;

import com.rbc.fogwall.db.UrlRuleRegistry;
import com.rbc.fogwall.git.LocalRepositoryCache;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.command.CommandFactory;

/**
 * MINA SSHD {@link CommandFactory} that maps {@code git-receive-pack} SSH commands to {@link SshGitReceiveCommand} and
 * {@code git-upload-pack} commands to {@link SshGitUploadCommand}. The provider a given command routes to isn't known
 * until the command's repo path is parsed, so {@code routes} (built once at startup by
 * {@link com.rbc.fogwall.jetty.SshServerRegistrar}) is handed to the command itself, which resolves the matching
 * provider from the path segment when it runs — see {@link SshGitReceiveCommand#resolveRoute}.
 */
@Slf4j
@RequiredArgsConstructor
public class SshGitCommandFactory implements CommandFactory {

    private final Map<String, SshProviderTarget> routes;
    private final LocalRepositoryCache cache;
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
            return new SshGitReceiveCommand(repoPath, routes, cache, agentFactory, knownHostsFile, trustOnFirstUse);
        }

        if (command.startsWith("git-upload-pack ")) {
            String repoPath =
                    stripQuotes(command.substring("git-upload-pack ".length()).trim());
            return new SshGitUploadCommand(
                    repoPath, routes, cache, agentFactory, urlRuleRegistry, knownHostsFile, trustOnFirstUse);
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
