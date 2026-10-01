package com.rbc.fogwall.ssh;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.db.FetchActivityRecorder;
import com.rbc.fogwall.db.memory.InMemoryUrlRuleRegistry;
import com.rbc.fogwall.db.model.AccessRule;
import com.rbc.fogwall.db.model.FetchActivity;
import com.rbc.fogwall.db.model.FetchRefusal;
import com.rbc.fogwall.db.model.MatchTarget;
import com.rbc.fogwall.db.model.MatchType;
import com.rbc.fogwall.git.DisabledFetchUploadPackFactory;
import com.rbc.fogwall.git.LocalRepositoryCache;
import com.rbc.fogwall.git.ProxyMode;
import com.rbc.fogwall.git.ServerReceivePackFactory;
import com.rbc.fogwall.provider.FogwallProvider;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.session.ServerSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Each refusal the SSH fetch path makes is counted with its reason, before any upstream contact. The allowed path needs
 * a forwarded agent and an upstream, and is covered by {@code SshE2ETest}.
 */
class SshGitUploadCommandTest {

    private static final String ROUTE = "/upstream.example";
    private static final String PROVIDER_ID = "gitea/upstream.example";

    private final FogwallProvider provider = mock(FogwallProvider.class);
    private final InMemoryUrlRuleRegistry rules = new InMemoryUrlRuleRegistry();
    private final FogwallProxyAgentFactory agents = mock(FogwallProxyAgentFactory.class);
    private final LocalRepositoryCache cache = mock(LocalRepositoryCache.class);
    private final FetchActivityRecorder recorder = mock(FetchActivityRecorder.class);
    private final ExitCallback exit = mock(ExitCallback.class);
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @BeforeEach
    void setUp() {
        when(provider.getProviderId()).thenReturn(PROVIDER_ID);
        when(provider.getName()).thenReturn("gitea");
        when(provider.getSshUri()).thenReturn(Optional.of(URI.create("ssh://git@upstream.example:22")));
        when(provider.isServeFetch()).thenReturn(true);
    }

    private void allow(AccessRule.Access access) {
        rules.save(AccessRule.builder()
                .ruleOrder(1)
                .access(access)
                .operation(AccessRule.Operation.BOTH)
                .target(MatchTarget.SLUG)
                .value("/acme/widgets")
                .matchType(MatchType.LITERAL)
                .build());
    }

    /** Starts the command for {@code acme/widgets} and waits for it to exit with 128. */
    private void run() throws Exception {
        var command = new SshGitUploadCommand(
                ROUTE + "/acme/widgets.git",
                Map.of(ROUTE, new SshProviderTarget(provider, mock(ServerReceivePackFactory.class))),
                cache,
                agents,
                rules,
                recorder,
                null,
                false);
        command.setInputStream(new ByteArrayInputStream(new byte[0]));
        command.setOutputStream(new ByteArrayOutputStream());
        command.setErrorStream(err);
        command.setExitCallback(exit);

        ServerSession session = mock(ServerSession.class);
        when(session.getUsername()).thenReturn("git");
        when(session.getProperties()).thenReturn(new HashMap<>());
        ChannelSession channel = mock(ChannelSession.class);
        when(channel.getSession()).thenReturn(session);
        Environment env = mock(Environment.class);
        when(env.getEnv()).thenReturn(Map.of());

        command.start(channel, env);

        verify(exit, timeout(5_000)).onExit(128);
        verifyNoInteractions(cache);
    }

    private void verifyCounted(FetchRefusal refusal, String ruleId) {
        verify(recorder)
                .record(
                        PROVIDER_ID,
                        "acme",
                        "widgets",
                        FetchActivity.Transport.SSH,
                        ProxyMode.SERVER,
                        FetchActivity.Result.BLOCKED,
                        refusal,
                        ruleId);
    }

    @Test
    void fetchServingDisabled_isCountedAndRefused() throws Exception {
        when(provider.isServeFetch()).thenReturn(false);

        run();

        verifyCounted(FetchRefusal.FETCH_DISABLED, null);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains(DisabledFetchUploadPackFactory.MESSAGE));
    }

    @Test
    void noAllowRule_isCountedAndRefused() throws Exception {
        run();

        verifyCounted(FetchRefusal.NOT_IN_ALLOW_LIST, null);
    }

    @Test
    void denyRule_isCountedWithTheRule() throws Exception {
        allow(AccessRule.Access.DENY);
        String ruleId = rules.findAll().getFirst().getId();

        run();

        verifyCounted(FetchRefusal.DENY_RULE, ruleId);
    }

    @Test
    void noForwardedAgent_isCountedAndRefused() throws Exception {
        allow(AccessRule.Access.ALLOW);

        run();

        verifyCounted(FetchRefusal.SSH_AGENT_MISSING, null);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("agent forwarding"));
    }

    @Test
    void unknownProviderPath_isRefusedWithoutCounting() throws Exception {
        var command = new SshGitUploadCommand(
                "/nowhere/acme/widgets.git",
                Map.of(ROUTE, new SshProviderTarget(provider, mock(ServerReceivePackFactory.class))),
                cache,
                agents,
                rules,
                recorder,
                null,
                false);
        command.setErrorStream(err);
        command.setExitCallback(exit);
        ServerSession session = mock(ServerSession.class);
        when(session.getProperties()).thenReturn(new HashMap<>());
        ChannelSession channel = mock(ChannelSession.class);
        when(channel.getSession()).thenReturn(session);
        Environment env = mock(Environment.class);
        when(env.getEnv()).thenReturn(Map.of());

        command.start(channel, env);

        verify(exit, timeout(5_000)).onExit(128);
        verifyNoInteractions(recorder);
    }
}
