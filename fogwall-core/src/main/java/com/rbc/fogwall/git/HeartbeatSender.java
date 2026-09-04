package com.rbc.fogwall.git;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.transport.ReceivePack;

/**
 * Sends periodic keepalive dots on sideband-2 to prevent idle-timeout disconnects during long-running validation steps
 * (e.g. secret scanning, approval polling).
 *
 * <p>Every {@code interval} seconds a {@code "."} progress message is written. The dot is harmless whitespace and does
 * not affect validation output. If the interval is zero or negative the sender is a no-op.
 *
 * <p><b>Shared scheduler.</b> All senders schedule their ticks on one process-wide daemon scheduler
 * ({@link #SHARED_SCHEDULER}) rather than each spinning up its own thread. fogwall sits inline on every push at
 * enterprise scale, and a per-push scheduler thread meant a live thread per in-flight push held open for the whole
 * approval wait — hundreds of threads doing almost nothing. The tick itself is a sub-millisecond sideband write, so a
 * small shared pool services many concurrent pushes; dots are best-effort, so if a write blocks on a slow client the
 * only effect is a slightly delayed dot for other pushes, never a stalled push. {@link #close()} cancels this sender's
 * tick without touching the shared scheduler.
 *
 * <p>An optional {@code onDisconnect} callback is invoked once when a write fails, indicating the client has gone away.
 * The callback runs on a scheduler thread and must be short and non-blocking.
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
 * <p><b>Thread safety:</b> The heartbeat writes on a scheduler thread while hooks write on the request thread. JGit's
 * sideband stream is not thread-safe, so a very small race window exists. In practice this is benign because the
 * heartbeat is only needed during long silent gaps (subprocess waits, polling loops) when hooks are not actively
 * writing.
 */
@Slf4j
public class HeartbeatSender implements AutoCloseable {

    /**
     * One shared, daemon-threaded scheduler for every heartbeat in the process. Sized to a small fraction of the CPU
     * count: each tick is a quick sideband write, so a handful of threads keeps up with many concurrent pushes.
     * Cancelled ticks are removed from the queue eagerly ({@code removeOnCancelPolicy}) so a finished push's tick does
     * not linger until its next scheduled fire. Daemon threads, so it never blocks JVM shutdown.
     */
    private static final ScheduledExecutorService SHARED_SCHEDULER = createSharedScheduler();

    private static ScheduledExecutorService createSharedScheduler() {
        int poolSize = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        ScheduledThreadPoolExecutor exec = new ScheduledThreadPoolExecutor(poolSize, r -> {
            Thread t = new Thread(r, "fogwall-heartbeat");
            t.setDaemon(true);
            return t;
        });
        exec.setRemoveOnCancelPolicy(true);
        return exec;
    }

    private final ReceivePack rp;
    private final Duration interval;
    private final Runnable onDisconnect;
    private final ScheduledExecutorService scheduler;
    private volatile ScheduledFuture<?> task;
    private volatile boolean paused = false;

    public HeartbeatSender(ReceivePack rp, Duration interval) {
        this(rp, interval, null);
    }

    public HeartbeatSender(ReceivePack rp, Duration interval, Runnable onDisconnect) {
        this(rp, interval, onDisconnect, SHARED_SCHEDULER);
    }

    /** Package-private constructor letting tests supply a controllable scheduler instead of the shared one. */
    HeartbeatSender(ReceivePack rp, Duration interval, Runnable onDisconnect, ScheduledExecutorService scheduler) {
        this.rp = rp;
        this.interval = interval;
        this.onDisconnect = onDisconnect;
        this.scheduler = scheduler;
    }

    /** Starts the heartbeat. No-op if interval is zero or negative. */
    public void start() {
        if (interval.isZero() || interval.isNegative()) {
            return;
        }
        long seconds = interval.toSeconds();
        task = scheduler.scheduleAtFixedRate(this::sendDot, seconds, seconds, TimeUnit.SECONDS);
        log.debug("Heartbeat started (interval: {}s)", seconds);
    }

    /** Suppresses heartbeat dots without cancelling the tick. Call {@link #resume()} to re-enable. */
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
            // The write failed — the client is gone. Cancel our own tick (never the shared scheduler) and notify.
            cancelTask();
            if (onDisconnect != null) {
                try {
                    onDisconnect.run();
                } catch (Exception ex) {
                    log.warn("Disconnect callback threw", ex);
                }
            }
        }
    }

    private void cancelTask() {
        ScheduledFuture<?> t = task;
        if (t != null) {
            t.cancel(false);
        }
    }

    @Override
    public void close() {
        cancelTask();
        log.debug("Heartbeat stopped");
    }
}
