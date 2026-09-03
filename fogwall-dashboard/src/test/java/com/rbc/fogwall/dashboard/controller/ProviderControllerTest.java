package com.rbc.fogwall.dashboard.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.rbc.fogwall.config.FogwallConfig;
import com.rbc.fogwall.config.ServerConfig;
import com.rbc.fogwall.jetty.reload.ConfigHolder;
import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.provider.ForgejoProvider;
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
class ProviderControllerTest {

    @InjectMocks
    ProviderController controller;

    @Mock
    ProviderRegistry providers;

    @Mock
    ConfigHolder configHolder;

    @Mock
    FogwallConfig fogwallConfig;

    /** Provider entry that serves SSH (has an sshUri, #531). */
    private final FogwallProvider sshProvider = ForgejoProvider.builder()
            .name("gitea")
            .uri(URI.create("https://gitea.example.com"))
            .sshUri(URI.create("ssh://git@gitea.example.com"))
            .build();

    /** Provider entry with HTTP only — no sshUri. */
    private final FogwallProvider httpOnlyProvider = ForgejoProvider.builder()
            .name("gitlab-http")
            .uri(URI.create("https://gitlab.example.com"))
            .build();

    private ServerConfig serverConfig;

    @BeforeEach
    void setUp() {
        serverConfig = new ServerConfig();
        when(configHolder.getAttestations()).thenReturn(List.of());
        when(fogwallConfig.getServer()).thenReturn(serverConfig);
        when(providers.getProviders()).thenReturn(List.of(sshProvider, httpOnlyProvider));
    }

    @Test
    void sshEnabled_whenListenerOnAndProviderHasSshUri() {
        serverConfig.getSsh().setEnabled(true);
        serverConfig.getSsh().setPort(2222);

        var infos = controller.list();
        var gitea =
                infos.stream().filter(p -> p.name().equals("gitea")).findFirst().orElseThrow();

        assertTrue(gitea.sshEnabled(), "provider with an sshUri and the listener on must advertise SSH");
        assertEquals(2222, gitea.sshPort());
        assertEquals("/gitea.example.com", gitea.sshPath(), "SSH route path is the provider's servletPath");
    }

    @Test
    void sshDisabled_whenListenerOff_evenIfProviderHasSshUri() {
        serverConfig.getSsh().setEnabled(false);

        var gitea = controller.list().stream()
                .filter(p -> p.name().equals("gitea"))
                .findFirst()
                .orElseThrow();

        assertFalse(gitea.sshEnabled(), "no SSH transport is offered when the global listener is off");
    }

    @Test
    void sshDisabled_forHttpOnlyProvider_evenWhenListenerOn() {
        serverConfig.getSsh().setEnabled(true);

        var httpOnly = controller.list().stream()
                .filter(p -> p.name().equals("gitlab-http"))
                .findFirst()
                .orElseThrow();

        assertFalse(httpOnly.sshEnabled(), "a provider with no sshUri never advertises SSH");
    }
}
