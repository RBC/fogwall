package com.rbc.fogwall.dashboard.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.rbc.fogwall.config.FogwallConfig;
import com.rbc.fogwall.config.ServerConfig;
import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.provider.ForgejoProvider;
import com.rbc.fogwall.provider.GitHubProvider;
import com.rbc.fogwall.provider.ProviderRegistry;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SetupControllerTest {

    @InjectMocks
    SetupController controller;

    @Mock
    ProviderRegistry providers;

    @Mock
    FogwallConfig fogwallConfig;

    private ServerConfig serverConfig;

    /** github.com, HTTP-only. */
    private final FogwallProvider github = GitHubProvider.builder()
            .name("github")
            .uri(URI.create("https://github.com"))
            .build();

    /** Self-hosted gitea serving both HTTP and SSH (#531). */
    private final FogwallProvider gitea = ForgejoProvider.builder()
            .name("gitea")
            .uri(URI.create("https://gitea.example.com"))
            .sshUri(URI.create("ssh://git@gitea.example.com"))
            .build();

    @BeforeEach
    void setUp() {
        serverConfig = new ServerConfig();
        when(fogwallConfig.getServer()).thenReturn(serverConfig);
        when(providers.getProviders()).thenReturn(List.of(github, gitea));
    }

    @Test
    void usesServiceUrlWhenConfigured() {
        serverConfig.setServiceUrl("https://fogwall.example.internal/");
        serverConfig.getSsh().setEnabled(false);

        var info = controller.setup();

        assertTrue(info.serviceUrlConfigured());
        assertEquals("https://fogwall.example.internal", info.serviceUrl(), "trailing slash stripped");
        var gh = info.providers().stream()
                .filter(p -> p.name().equals("github"))
                .findFirst()
                .orElseThrow();
        assertEquals("github.com", gh.host());
        assertEquals("https://github.com", gh.upstreamUrl());
        assertEquals(
                "https://fogwall.example.internal/server/github.com/",
                gh.serverUrl(),
                "serverUrl is the /server clone/push prefix for the quick start");
        // Default is push-only: pushes route to /server, reads are left direct.
        assertTrue(
                gh.httpPush().contains("https://fogwall.example.internal/server/github.com/"),
                "push config routes through the configured service URL:\n" + gh.httpPush());
        assertTrue(
                gh.httpRead().contains("https://fogwall.example.internal/proxy/github.com/"),
                "opt-in read config routes through the proxy path:\n" + gh.httpRead());
    }

    @Test
    void sshConfigPresentOnlyWhenListenerOnAndProviderServesSsh() {
        serverConfig.setServiceUrl("https://fogwall.example.internal");
        serverConfig.getSsh().setEnabled(true);
        serverConfig.getSsh().setPort(2222);

        var info = controller.setup();
        var gh = byName(info, "github");
        var gt = byName(info, "gitea");

        assertFalse(gh.sshEnabled(), "github is HTTP-only");
        assertNull(gh.sshPush(), "no SSH block for an HTTP-only provider");
        assertNull(gh.sshPerRepo(), "no per-repo SSH commands for an HTTP-only provider");

        assertTrue(gt.sshEnabled(), "gitea serves SSH and the listener is on");
        assertTrue(
                gt.sshPush().contains("ssh://fogwall.example.internal:2222/gitea.example.com/"),
                "SSH push block uses the deployment host and listener port:\n" + gt.sshPush());
    }

    @Test
    void sshBlockAbsentWhenListenerOff_evenIfProviderServesSsh() {
        serverConfig.setServiceUrl("https://fogwall.example.internal");
        serverConfig.getSsh().setEnabled(false);

        var gt = byName(controller.setup(), "gitea");
        assertFalse(gt.sshEnabled(), "listener off → no SSH transport advertised");
        assertNull(gt.sshPush());
        assertNull(gt.sshPerRepo());
    }

    private static SetupController.SetupProvider byName(SetupController.SetupInfo info, String name) {
        return info.providers().stream()
                .filter(p -> p.name().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
