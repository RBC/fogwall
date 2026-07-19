package com.rbc.fogwall.net;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FogwallHttpExecutorTest {

    @AfterEach
    void resetToUnconfigured() {
        FogwallHttpExecutor.configure(null);
    }

    @Test
    void configure_nullProxy_leavesUsableDefaultClient() {
        FogwallHttpExecutor.configure(null);
        assertNotNull(FogwallHttpExecutor.instance());
    }

    @Test
    void configure_unconfiguredProxy_leavesUsableDefaultClient() {
        FogwallHttpExecutor.configure(ResolvedOutboundProxy.NONE);
        assertNotNull(FogwallHttpExecutor.instance());
    }

    @Test
    void configure_basicAuthProxy_rebuildsClientWithoutError() {
        var proxy = new ResolvedOutboundProxy(
                null,
                0,
                "proxy.example.com",
                8080,
                Set.of(),
                ResolvedOutboundProxy.AuthType.BASIC,
                "user",
                "pass",
                null,
                null);

        assertDoesNotThrow(() -> FogwallHttpExecutor.configure(proxy));
        assertNotNull(FogwallHttpExecutor.instance());
    }

    @Test
    void configure_kerberosProxy_rebuildsClientWithoutError() {
        var proxy = new ResolvedOutboundProxy(
                null,
                0,
                "proxy.example.com",
                8080,
                Set.of(),
                ResolvedOutboundProxy.AuthType.KERBEROS,
                null,
                null,
                null,
                null);

        assertDoesNotThrow(() -> FogwallHttpExecutor.configure(proxy));
        assertNotNull(FogwallHttpExecutor.instance());
    }
}
