package com.rbc.fogwall.ssh;

import com.rbc.fogwall.git.LocalRepositoryCache;
import com.rbc.fogwall.git.StoreAndForwardReceivePackFactory;
import com.rbc.fogwall.provider.FogwallProvider;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.command.CommandFactory;

/**
 * MINA SSHD {@link CommandFactory} that maps {@code git-receive-pack} SSH commands to {@link SshGitReceiveCommand}.
 * Only store-and-forward push is supported in this initial implementation; {@code git-upload-pack} (clone/fetch) is not
 * yet implemented.
 */
@Slf4j
@RequiredArgsConstructor
public class SshGitCommandFactory implements CommandFactory {

    private final FogwallProvider provider;
    private final LocalRepositoryCache cache;
    private final StoreAndForwardReceivePackFactory receivePackFactory;
    private final FogwallProxyAgentFactory agentFactory;

    @Override
    public Command createCommand(ChannelSession channel, String command) throws IOException {
        log.debug("SSH command received: {}", command);

        if (command.startsWith("git-receive-pack ")) {
            String rawPath = command.substring("git-receive-pack ".length()).trim();
            // Strip surrounding single or double quotes added by the git client
            String repoPath = rawPath.replaceAll("^['\"]|['\"]$", "");
            return new SshGitReceiveCommand(repoPath, provider, cache, receivePackFactory, agentFactory);
        }

        // git-upload-pack (clone/fetch) not yet implemented
        log.warn("Unsupported SSH git command: {}", command);
        throw new IOException(
                "Unsupported command: " + command + " (only git-receive-pack is currently supported over SSH)");
    }
}
