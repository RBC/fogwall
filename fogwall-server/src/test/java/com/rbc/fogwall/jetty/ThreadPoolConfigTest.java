package com.rbc.fogwall.jetty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rbc.fogwall.config.ServerConfig;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link FogwallJettyApplication#configureThreadPool} ({@code server.threads.*}). */
class ThreadPoolConfigTest {

    private static ServerConfig.ThreadsConfig threads(int min, int max, int idleMs) {
        var t = new ServerConfig.ThreadsConfig();
        t.setMin(min);
        t.setMax(max);
        t.setIdleTimeoutMs(idleMs);
        return t;
    }

    @Test
    void defaults_matchQueuedThreadPoolDefaults() {
        var pool = new QueuedThreadPool();
        FogwallJettyApplication.configureThreadPool(pool, new ServerConfig.ThreadsConfig());

        assertEquals(8, pool.getMinThreads());
        assertEquals(200, pool.getMaxThreads());
        assertEquals(60_000, pool.getIdleTimeout());
    }

    @Test
    void raisingPoolAboveDefaults_applies() {
        var pool = new QueuedThreadPool();
        FogwallJettyApplication.configureThreadPool(pool, threads(300, 500, 30_000));

        assertEquals(300, pool.getMinThreads());
        assertEquals(500, pool.getMaxThreads());
        assertEquals(30_000, pool.getIdleTimeout());
    }

    @Test
    void loweringMaxBelowDefaultMin_appliesWithoutTransientViolation() {
        // Default min is 8; a max of 4 would violate min<=max if max were set first.
        var pool = new QueuedThreadPool();
        FogwallJettyApplication.configureThreadPool(pool, threads(2, 4, 10_000));

        assertEquals(2, pool.getMinThreads());
        assertEquals(4, pool.getMaxThreads());
    }

    @Test
    void invertedConfig_minGreaterThanMax_failsLoudly() {
        var pool = new QueuedThreadPool();
        assertThrows(
                IllegalArgumentException.class,
                () -> FogwallJettyApplication.configureThreadPool(pool, threads(10, 4, 5_000)));
    }
}
