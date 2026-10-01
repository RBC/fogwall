package com.rbc.fogwall.db;

import com.rbc.fogwall.db.model.FetchActivity;
import com.rbc.fogwall.db.model.FetchRefusal;
import com.rbc.fogwall.git.ProxyMode;
import com.rbc.fogwall.observability.FogwallTelemetry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import lombok.extern.slf4j.Slf4j;

/**
 * Counts fogwall's decisions on clones and fetches in memory and writes the counts to the {@link FetchStore} on an
 * interval, so a fetch never waits on the database. Each decision increments a counter keyed by the hour and the
 * dimensions of {@link FetchActivity.Key}; a flush hands every counter to the store as an increment and starts afresh.
 * Rows older than the retention window are pruned once an hour, from the same thread.
 *
 * <p>The number of distinct keys held between flushes is capped. Owner and repository on a refused request are whatever
 * the client asked for, so without a cap a stream of made-up paths would grow the map without bound. Past the cap, a
 * new key is folded into one row per provider, transport, mode and outcome that names no repository.
 *
 * <p>Counts are lost if the store refuses a flush, or if the process dies between flushes: at most one interval's
 * worth. That is the trade this makes for keeping the clone path free of database writes.
 */
@Slf4j
public class FetchActivityRecorder {

    private static final Duration PRUNE_INTERVAL = Duration.ofHours(1);

    private final FetchStore store;
    private final FogwallTelemetry telemetry;
    private final Duration flushInterval;
    private final Duration retention;
    private final int maxKeys;
    private final Clock clock;

    /** Held shared by {@link #record}, exclusively by {@link #flush} to swap in a fresh map. */
    private final ReadWriteLock swapLock = new ReentrantReadWriteLock();

    private Map<FetchActivity.Key, Tally> counts = new ConcurrentHashMap<>();
    private final AtomicBoolean overflowed = new AtomicBoolean();
    private Instant lastPrune = Instant.EPOCH;
    private ScheduledExecutorService scheduler;

    public FetchActivityRecorder(
            FetchStore store, FogwallTelemetry telemetry, Duration flushInterval, Duration retention, int maxKeys) {
        this(store, telemetry, flushInterval, retention, maxKeys, Clock.systemUTC());
    }

    FetchActivityRecorder(
            FetchStore store,
            FogwallTelemetry telemetry,
            Duration flushInterval,
            Duration retention,
            int maxKeys,
            Clock clock) {
        this.store = store;
        this.telemetry = telemetry;
        this.flushInterval = flushInterval;
        this.retention = retention;
        this.maxKeys = maxKeys;
        this.clock = clock;
    }

    /**
     * Counts one decision. Never touches the database.
     *
     * @param refusal why a {@link FetchActivity.Result#BLOCKED} fetch was refused; null when it was allowed
     * @param ruleId the URL rule that matched, if one did
     */
    public void record(
            String provider,
            String owner,
            String repoName,
            FetchActivity.Transport transport,
            ProxyMode mode,
            FetchActivity.Result result,
            FetchRefusal refusal,
            String ruleId) {
        Instant now = clock.instant();
        var key = new FetchActivity.Key(
                now.truncatedTo(ChronoUnit.HOURS), provider, owner, repoName, transport, mode, result, refusal, ruleId);
        swapLock.readLock().lock();
        try {
            Tally tally = counts.get(key);
            if (tally == null) {
                if (counts.size() >= maxKeys) {
                    overflowed.set(true);
                    key = key.overflow();
                }
                tally = counts.computeIfAbsent(key, k -> new Tally());
            }
            tally.add(now);
        } finally {
            swapLock.readLock().unlock();
        }
        telemetry.recordFetchDecision(
                provider == null ? "unknown" : provider,
                telemetryMode(transport, mode),
                result.name(),
                refusal == null ? "none" : refusal.name());
    }

    /** {@link FogwallTelemetry#MODE}'s values: SSH is its own, HTTP is named by proxy mode. */
    private static String telemetryMode(FetchActivity.Transport transport, ProxyMode mode) {
        if (transport == FetchActivity.Transport.SSH) {
            return "ssh";
        }
        return mode == ProxyMode.TRANSPARENT ? "proxy" : "server";
    }

    /** Starts flushing on the configured interval. */
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("fetch-activity-flush").factory());
        long seconds = flushInterval.toSeconds();
        scheduler.scheduleWithFixedDelay(this::flush, seconds, seconds, TimeUnit.SECONDS);
        log.info(
                "Fetch activity recording started: flush every {}, retention {}, at most {} keys between flushes",
                flushInterval,
                retention,
                maxKeys);
    }

    /** Stops the interval and writes what has been counted since the last flush. */
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        flush();
    }

    /** Writes every counter to the store and prunes expired rows when an hour has passed since the last prune. */
    public void flush() {
        Map<FetchActivity.Key, Tally> flushed;
        swapLock.writeLock().lock();
        try {
            flushed = counts;
            counts = new ConcurrentHashMap<>();
        } finally {
            swapLock.writeLock().unlock();
        }
        if (overflowed.getAndSet(false)) {
            log.warn(
                    "Fetch activity passed {} distinct keys within one flush; the excess was counted without its"
                            + " repository",
                    maxKeys);
        }
        if (!flushed.isEmpty()) {
            List<FetchActivity> rows = flushed.entrySet().stream()
                    .map(e -> e.getKey()
                            .toActivity(e.getValue().count.sum(), e.getValue().lastSeen()))
                    .toList();
            try {
                store.add(rows);
            } catch (Exception e) {
                long lost =
                        rows.stream().mapToLong(FetchActivity::getFetchCount).sum();
                log.warn(
                        "Failed to write fetch activity; {} fetch(es) across {} row(s) not recorded",
                        lost,
                        rows.size(),
                        e);
            }
        }
        Instant now = clock.instant();
        if (Duration.between(lastPrune, now).compareTo(PRUNE_INTERVAL) >= 0) {
            lastPrune = now;
            try {
                store.pruneBefore(now.minus(retention));
            } catch (Exception e) {
                log.warn("Failed to prune fetch activity", e);
            }
        }
    }

    private static final class Tally {
        final LongAdder count = new LongAdder();
        final AtomicLong lastSeenMillis = new AtomicLong();

        void add(Instant at) {
            count.increment();
            lastSeenMillis.accumulateAndGet(at.toEpochMilli(), Math::max);
        }

        Instant lastSeen() {
            return Instant.ofEpochMilli(lastSeenMillis.get());
        }
    }
}
