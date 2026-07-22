package com.rbc.fogwall.ssh;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.rbc.fogwall.db.UrlRuleRegistry;
import com.rbc.fogwall.git.LocalRepositoryCache;
import com.rbc.fogwall.git.StoreAndForwardReceivePackFactory;
import com.rbc.fogwall.provider.FogwallProvider;
import java.io.IOException;
import java.util.Map;
import org.apache.sshd.server.channel.ChannelSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SshGitCommandFactoryTest {

    private SshGitCommandFactory factory;
    private ChannelSession channel;

    @BeforeEach
    void setUp() {
        FogwallProvider provider = mock(FogwallProvider.class);
        Map<String, SshProviderTarget> routes = Map.of(
                "/localhost:3022", new SshProviderTarget(provider, mock(StoreAndForwardReceivePackFactory.class)));
        factory = new SshGitCommandFactory(
                routes,
                mock(LocalRepositoryCache.class),
                mock(FogwallProxyAgentFactory.class),
                mock(UrlRuleRegistry.class),
                null,
                false);
        channel = mock(ChannelSession.class);
    }

    @Test
    void routesReceivePackCommand() throws IOException {
        var command = factory.createCommand(channel, "git-receive-pack '/localhost:3022/owner/repo.git'");
        assertInstanceOf(SshGitReceiveCommand.class, command);
    }

    @Test
    void routesUploadPackCommand() throws IOException {
        var command = factory.createCommand(channel, "git-upload-pack '/localhost:3022/owner/repo.git'");
        assertInstanceOf(SshGitUploadCommand.class, command);
    }

    @Test
    void routesReceivePackCommandWithoutQuotes() throws IOException {
        var command = factory.createCommand(channel, "git-receive-pack /localhost:3022/owner/repo.git");
        assertInstanceOf(SshGitReceiveCommand.class, command);
    }

    @Test
    void routesUploadPackCommandWithoutQuotes() throws IOException {
        var command = factory.createCommand(channel, "git-upload-pack /localhost:3022/owner/repo.git");
        assertInstanceOf(SshGitUploadCommand.class, command);
    }

    @Test
    void rejectsUnsupportedCommand() {
        assertThrows(IOException.class, () -> factory.createCommand(channel, "git-upload-archive '/owner/repo.git'"));
    }
}
