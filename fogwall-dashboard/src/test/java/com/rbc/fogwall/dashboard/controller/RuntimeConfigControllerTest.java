package com.rbc.fogwall.dashboard.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.rbc.fogwall.config.FogwallConfig;
import com.rbc.fogwall.config.ProviderConfig;
import com.rbc.fogwall.crypto.TokenCipherProvider;
import com.rbc.fogwall.provider.InMemoryProviderRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuntimeConfigControllerTest {

    private static ProviderConfig provider(boolean enabled, boolean oauth, boolean brokeredPush) {
        var config = new ProviderConfig();
        config.setEnabled(enabled);
        config.getOauth().setEnabled(oauth);
        config.getOauth().setBrokeredPush(brokeredPush);
        return config;
    }

    @Test
    void brokeredPushProviders_listsOnlyEnabledProvidersThatBroker() {
        Map<String, ProviderConfig> providers = new LinkedHashMap<>();
        providers.put("gitlab", provider(true, true, true));
        providers.put("github", provider(true, true, true));
        providers.put("codeberg", provider(true, true, false));
        providers.put("gitea", provider(true, false, true));
        providers.put("bitbucket", provider(false, true, true));
        var config = new FogwallConfig();
        config.setProviders(providers);

        Map<String, Object> runtime = new RuntimeConfigController(
                        config, TokenCipherProvider.unavailable(), new InMemoryProviderRegistry(List.of()))
                .runtimeConfig();

        assertEquals(List.of("github", "gitlab"), runtime.get("brokeredPushProviders"));
    }
}
