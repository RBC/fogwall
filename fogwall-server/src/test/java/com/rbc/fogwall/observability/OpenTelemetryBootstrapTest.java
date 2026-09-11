package com.rbc.fogwall.observability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rbc.fogwall.config.OtelConfig;
import io.opentelemetry.api.GlobalOpenTelemetry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OpenTelemetryBootstrapTest {

    @AfterEach
    void resetGlobal() {
        // build() registers a JVM-global SDK when enabled; clear it so tests stay isolated.
        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    void disabledConfig_yieldsDisabledTelemetry() {
        FogwallTelemetry telemetry = OpenTelemetryBootstrap.build(new OtelConfig(), "9.9.9");
        assertNotNull(telemetry);
        assertFalse(telemetry.isEnabled());
    }

    @Test
    void enabledConfig_assemblesSdkAndExporters() {
        // Exercises the real SDK + OTLP exporter builders against the pinned versions. No collector is needed —
        // exporters connect lazily and fail-soft in the background — so this only asserts assembly succeeds.
        OtelConfig cfg = new OtelConfig();
        cfg.setEnabled(true);
        FogwallTelemetry telemetry = OpenTelemetryBootstrap.build(cfg, "1.2.3");
        assertTrue(telemetry.isEnabled());
        assertNotNull(telemetry.tracer());
    }

    @Test
    void enabledConfig_httpProtocol_assembles() {
        OtelConfig cfg = new OtelConfig();
        cfg.setEnabled(true);
        cfg.setProtocol("http");
        cfg.setEndpoint("http://localhost:4318");
        FogwallTelemetry telemetry = OpenTelemetryBootstrap.build(cfg, "1.2.3");
        assertTrue(telemetry.isEnabled());
    }
}
