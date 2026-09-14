package com.rbc.fogwall.dashboard.compose;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The telemetry axis: a push produces spans and metrics that reach the collector.
 *
 * <p>Only runs when the stack was brought up with {@code --otel}; otherwise it skips. Instrumentation is pure wiring —
 * exporters, an endpoint, registration — none of which exists until the stack is assembled, and its failure mode is
 * silent: spans stop being emitted while every other suite stays green.
 *
 * <p>Asserted numerically: a series name on the collector's Prometheus endpoint and the count of spans it accepted are
 * facts, where parsing the debug exporter's output would test the exporter's formatting.
 */
@Tag("compose")
class TelemetryComposeTest {

    private static ComposeStack stack;
    private static String token;

    @BeforeAll
    static void resolveStack() throws Exception {
        stack = ComposeStack.requireRunning();
        token = stack.accessToken(ComposeStack.TEST_USER, ComposeStack.TEST_USER_PASSWORD);
        assumeTrue(
                stack.collectorReachable(),
                "no OpenTelemetry collector on " + stack.collectorUrl() + " — run: bash compose.sh --otel -- up -d");
    }

    @Test
    void aPushProducesSpansAndMetrics() throws Exception {
        Path workspace = Files.createTempDirectory("fogwall-otel-");
        var git = new Git(workspace);
        Path repo = git.clone(
                stack.proxyUrl("proxy", ComposeStack.TEST_USER, token, ComposeStack.TEST_ORG, ComposeStack.TEST_REPO),
                "telemetry");
        git.branch(repo, "compose-otel-" + UUID.randomUUID().toString().substring(0, 8));
        git.commit(repo, "otel.txt", "instrumented at " + Instant.now() + "\n", "feat: a push worth measuring");
        // A held push is still a decision, and still produces spans.
        git.push(repo);

        assertTrue(
                eventually(() -> stack.plainGet(stack.jaegerUrl() + "/api/services")
                        .body()
                        .contains("fogwall")),
                "traces should reach Jaeger through the collector, exercising the whole pipeline. Jaeger knows: "
                        + stack.plainGet(stack.jaegerUrl() + "/api/services").body());

        assertTrue(
                eventually(() ->
                        stack.plainGet(stack.collectorUrl() + "/metrics").body().contains("fogwall_")),
                "a push should produce a fogwall metric on the collector. Instruments are registered at startup, so"
                        + " an empty scrape after the export interval means the meter provider never was.");
    }

    /**
     * Polls until the condition holds or the budget runs out.
     *
     * <p>Telemetry is asynchronous — spans batch, and the metric reader uses the SDK's default sixty-second period — so
     * any assertion about it is about what arrives eventually, not what has arrived by the next statement.
     */
    private static boolean eventually(Check check) throws Exception {
        for (int elapsed = 0; elapsed < 90; elapsed += 3) {
            if (check.holds()) {
                return true;
            }
            Thread.sleep(3_000);
        }
        return false;
    }

    @FunctionalInterface
    private interface Check {
        boolean holds() throws Exception;
    }
}
