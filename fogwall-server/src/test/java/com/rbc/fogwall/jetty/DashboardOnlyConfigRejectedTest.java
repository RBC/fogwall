package com.rbc.fogwall.jetty;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rbc.fogwall.config.FogwallConfig;
import com.rbc.fogwall.config.JettyConfigurationBuilder;
import org.junit.jupiter.api.Test;

/**
 * The standalone server serves git traffic and records its decisions; a push's lifecycle here is automated checks and
 * nothing else. Settings that only a dashboard can complete are refused at startup rather than accepted and then
 * discovered one refused or hung push at a time.
 */
class DashboardOnlyConfigRejectedTest {

    /** Runs the guard alone — it executes before anything is constructed, so Jetty need not be booted. */
    private static void validate(FogwallConfig config) {
        FogwallJettyApplication.rejectDashboardOnlyConfig(new JettyConfigurationBuilder(config));
    }

    @Test
    void defaults_areAccepted() {
        assertDoesNotThrow(() -> validate(new FogwallConfig()));
    }

    @Test
    void strictIdentityMode_isRefused() {
        var config = new FogwallConfig();
        config.getScmOauth().setIdentityMode("strict");

        var e = assertThrows(IllegalStateException.class, () -> validate(config));
        assertTrue(e.getMessage().contains("identity-mode: strict"));
        assertTrue(e.getMessage().contains("dashboard"));
    }

    @Test
    void permissiveIdentityMode_isAccepted() {
        var config = new FogwallConfig();
        config.getScmOauth().setIdentityMode("permissive");

        assertDoesNotThrow(() -> validate(config));
    }

    @Test
    void uiApprovalMode_isRefused() {
        // Nothing in this distribution can approve: no REST API, no review UI. The push would wait out the
        // approval timeout and fail.
        var config = new FogwallConfig();
        config.getServer().setApprovalMode("ui");

        var e = assertThrows(IllegalStateException.class, () -> validate(config));
        assertTrue(e.getMessage().contains("approval-mode: ui"));
    }

    @Test
    void autoApprovalMode_isAccepted() {
        var config = new FogwallConfig();
        config.getServer().setApprovalMode("auto");

        assertDoesNotThrow(() -> validate(config));
    }
}
