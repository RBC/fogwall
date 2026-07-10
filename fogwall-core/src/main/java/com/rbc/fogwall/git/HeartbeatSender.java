package com.rbc.fogwall.git;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.transport.ReceivePack;

/**
 * Sends periodic keepalive dots on sideband-2 to prevent idle-timeout disconnects during long-running validation steps
 * (e.g. secret scanning, approval polling).
 *
 * <p>A single background daemon thread fires every {@code interval} seconds and writes a {@code "."} progress message.
 * The dot is harmless whitespace and does not affect validation output. If the interval is zero or negative the sender
 * is a no-op.
 *
 * <p>An optional {@code onDisconnect} callback is invoked once when a write fails, indicating the client has gone away.
 * The callback runs on the heartbeat thread and must be short and non-blocking.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * try (HeartbeatSender hb = new HeartbeatSender(rp, Duration.ofSeconds(10), this::handleDisconnect)) {
 *     hb.start();
 *     // ... long-running hook chain ...
 * }
 * }</pre>
 *
 * <p><b>Thread safety:</b> The heartbeat writes on a background thread while hooks write on the request thread. JGit's
 * sideband stream is not thread-safe, so a very small race window exists. In practice this is benign because the
 * heartbeat is only needed during long silent gaps (subprocess waits, polling loops) when hooks are not actively
 * writing.
 */
@Slf4j
public class HeartbeatSender implements AutoCloseable {

    private final ReceivePack rp;
    private final Duration interval;
    private final Runnable onDisconnect;
    private ScheduledExecutorService executor;
    private volatile boolean paused = false;

    public HeartbeatSender(ReceivePack rp, Duration interval) {
        this(rp, interval, null);
    }

    public HeartbeatSender(ReceivePack rp, Duration interval, Runnable onDisconnect) {
        this.rp = rp;
        this.interval = interval;
        this.onDisconnect = onDisconnect;
    }

    /** Starts the heartbeat background thread. No-op if interval is zero or negative. */
    public void start() {
        if (interval.isZero() || interval.isNegative()) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "fogwall-heartbeat");
            t.setDaemon(true);
            return t;
        });
        long seconds = interval.toSeconds();
        executor.scheduleAtFixedRate(this::sendDot, seconds, seconds, TimeUnit.SECONDS);
        log.debug("Heartbeat started (interval: {}s)", seconds);
    }

    /** Suppresses heartbeat dots without stopping the background thread. Call {@link #resume()} to re-enable. */
    public void pause() {
        paused = true;
    }

    /** Re-enables heartbeat dots after a {@link #pause()}. */
    public void resume() {
        paused = false;
    }

    private void sendDot() {
        if (paused) {
            return;
        }
        try {
            rp.sendMessage(".");
            rp.getMessageOutputStream().flush();
        } catch (Exception e) {
            if (executor != null) {
                executor.shutdownNow();
            }
            if (onDisconnect != null) {
                try {
                    onDisconnect.run();
                } catch (Exception ex) {
                    log.warn("Disconnect callback threw", ex);
                }
            }
        }
    }

    @Override
    public void close() {
        if (executor != null) {
            executor.shutdownNow();
            log.debug("Heartbeat stopped");
        }
    }
}
