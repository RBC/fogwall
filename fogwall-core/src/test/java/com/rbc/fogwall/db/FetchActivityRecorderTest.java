package com.rbc.fogwall.db;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.db.memory.InMemoryFetchStore;
import com.rbc.fogwall.db.model.FetchActivity;
import com.rbc.fogwall.db.model.FetchActivityQuery;
import com.rbc.fogwall.db.model.FetchRefusal;
import com.rbc.fogwall.git.ProxyMode;
import com.rbc.fogwall.observability.FogwallTelemetry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class FetchActivityRecorderTest {

    private static final Instant START = Instant.parse("2026-09-30T14:10:00Z");

    /** A clock the test moves by hand. */
    private static final class TestClock extends Clock {
        Instant now = START;

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private final InMemoryFetchStore store = new InMemoryFetchStore();
    private final TestClock clock = new TestClock();

    private FetchActivityRecorder recorder(FetchStore target, FogwallTelemetry telemetry, int maxKeys) {
        return new FetchActivityRecorder(
                target, telemetry, Duration.ofSeconds(60), Duration.ofDays(30), maxKeys, clock);
    }

    private FetchActivityRecorder recorder(int maxKeys) {
        return recorder(store, FogwallTelemetry.disabled(), maxKeys);
    }

    private static void allow(FetchActivityRecorder recorder, String repo) {
        recorder.record(
                "github",
                "acme",
                repo,
                FetchActivity.Transport.HTTP,
                ProxyMode.TRANSPARENT,
                FetchActivity.Result.ALLOWED,
                null,
                "rule-1");
    }

    private static void refuse(FetchActivityRecorder recorder, String repo) {
        recorder.record(
                "github",
                "acme",
                repo,
                FetchActivity.Transport.HTTP,
                ProxyMode.TRANSPARENT,
                FetchActivity.Result.BLOCKED,
                FetchRefusal.NOT_IN_ALLOW_LIST,
                null);
    }

    private List<FetchActivity> stored() {
        return store.find(FetchActivityQuery.builder().build());
    }

    @Test
    void record_writesNothingUntilAFlush() {
        FetchStore target = mock(FetchStore.class);
        var recorder = recorder(target, FogwallTelemetry.disabled(), 100);

        allow(recorder, "widgets");
        allow(recorder, "widgets");

        verifyNoInteractions(target);
    }

    @Test
    void flush_writesOneRowPerKey_countingEveryFetch_inItsHour() {
        var recorder = recorder(100);
        allow(recorder, "widgets");
        clock.now = START.plusSeconds(30);
        allow(recorder, "widgets");
        refuse(recorder, "widgets");

        recorder.flush();

        List<FetchActivity> rows = stored();
        assertEquals(2, rows.size());
        FetchActivity allowed = rows.stream()
                .filter(r -> r.getResult() == FetchActivity.Result.ALLOWED)
                .findFirst()
                .orElseThrow();
        assertEquals(2, allowed.getFetchCount());
        assertEquals(Instant.parse("2026-09-30T14:00:00Z"), allowed.getBucketStart());
        assertEquals(START.plusSeconds(30), allowed.getLastSeen());
        assertEquals("rule-1", allowed.getRuleId());
    }

    @Test
    void flush_startsAfresh_soCountsAreNeverAddedTwice() {
        var recorder = recorder(100);
        allow(recorder, "widgets");
        recorder.flush();
        recorder.flush();
        allow(recorder, "widgets");
        recorder.flush();

        assertEquals(2, stored().getFirst().getFetchCount());
    }

    @Test
    void record_acrossAnHourBoundary_countsEachHourSeparately() {
        var recorder = recorder(100);
        allow(recorder, "widgets");
        clock.now = Instant.parse("2026-09-30T15:00:01Z");
        allow(recorder, "widgets");

        recorder.flush();

        assertEquals(2, stored().size());
    }

    /** Owner and repository on a refusal are whatever the client asked for; past the cap they are not kept. */
    @Test
    void record_pastTheKeyCap_foldsNewKeysIntoOneRowWithoutARepository() {
        var recorder = recorder(2);
        refuse(recorder, "made-up-1");
        refuse(recorder, "made-up-2");
        for (int i = 3; i <= 50; i++) {
            refuse(recorder, "made-up-" + i);
        }
        refuse(recorder, "made-up-1");

        recorder.flush();

        List<FetchActivity> rows = stored();
        assertEquals(3, rows.size());
        FetchActivity overflow =
                rows.stream().filter(r -> r.getRepoName() == null).findFirst().orElseThrow();
        assertNull(overflow.getOwner());
        assertEquals(48, overflow.getFetchCount());
        assertEquals(FetchRefusal.NOT_IN_ALLOW_LIST, overflow.getRefusal());
        assertEquals(
                2,
                rows.stream()
                        .filter(r -> "made-up-1".equals(r.getRepoName()))
                        .findFirst()
                        .orElseThrow()
                        .getFetchCount());
    }

    @Test
    void flush_whenTheStoreFails_dropsTheCountsAndKeepsCounting() {
        FetchStore failing = mock(FetchStore.class);
        doThrow(new IllegalStateException("database down")).when(failing).add(any());
        var recorder = recorder(failing, FogwallTelemetry.disabled(), 100);
        allow(recorder, "widgets");

        assertDoesNotThrow(recorder::flush);

        allow(recorder, "widgets");
        recorder.flush();
        verify(failing, times(2))
                .add(argThat(rows -> rows.size() == 1 && rows.iterator().next().getFetchCount() == 1));
    }

    @Test
    void flush_prunesOnceAnHour() {
        FetchStore target = mock(FetchStore.class);
        var recorder = recorder(target, FogwallTelemetry.disabled(), 100);

        recorder.flush();
        clock.now = START.plusSeconds(1800);
        recorder.flush();
        clock.now = START.plusSeconds(3600);
        recorder.flush();

        verify(target).pruneBefore(START.minus(Duration.ofDays(30)));
        verify(target).pruneBefore(START.plusSeconds(3600).minus(Duration.ofDays(30)));
        verify(target, times(2)).pruneBefore(any());
    }

    @Test
    void record_countsTheDecisionInTelemetry() {
        FogwallTelemetry telemetry = mock(FogwallTelemetry.class);
        var recorder = recorder(store, telemetry, 100);

        refuse(recorder, "widgets");
        recorder.record(
                "github",
                "acme",
                "widgets",
                FetchActivity.Transport.SSH,
                ProxyMode.SERVER,
                FetchActivity.Result.ALLOWED,
                null,
                null);

        verify(telemetry).recordFetchDecision("github", "proxy", "BLOCKED", "NOT_IN_ALLOW_LIST");
        verify(telemetry).recordFetchDecision("github", "ssh", "ALLOWED", "none");
    }

    /** A flush swaps the counters out from under concurrent requests; every fetch lands in exactly one flush. */
    @Test
    void flush_duringConcurrentRecording_losesNoCount() throws Exception {
        var recorder = recorder(100);
        int threads = 8;
        int perThread = 5_000;
        var ready = new CountDownLatch(threads);
        var done = new AtomicBoolean();
        List<Thread> workers = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            workers.add(Thread.ofVirtual().start(() -> {
                ready.countDown();
                for (int i = 0; i < perThread; i++) {
                    allow(recorder, "widgets");
                }
            }));
        }
        ready.await();
        Thread flusher = Thread.ofVirtual().start(() -> {
            while (!done.get()) {
                recorder.flush();
            }
        });
        for (Thread worker : workers) {
            worker.join();
        }
        done.set(true);
        flusher.join();
        recorder.flush();

        assertEquals((long) threads * perThread, stored().getFirst().getFetchCount());
    }

    /** Shutdown must not drop what was counted since the last interval. */
    @Test
    void stop_writesWhatIsStillInMemory_evenIfNeverStarted() {
        var recorder = recorder(100);
        allow(recorder, "widgets");

        recorder.stop();

        assertEquals(1, stored().getFirst().getFetchCount());
    }

    @Test
    void startThenStop_writesWhatIsStillInMemory() {
        var recorder = new FetchActivityRecorder(
                store, FogwallTelemetry.disabled(), Duration.ofHours(1), Duration.ofDays(30), 100);
        recorder.start();
        recorder.record(
                "github",
                "acme",
                "widgets",
                FetchActivity.Transport.HTTP,
                ProxyMode.SERVER,
                FetchActivity.Result.ALLOWED,
                null,
                null);

        recorder.stop();

        assertEquals(1, stored().getFirst().getFetchCount());
    }

    @Test
    void flush_whenPruningFails_stillWritesTheCounts() {
        FetchStore pruneFails = mock(FetchStore.class);
        doThrow(new IllegalStateException("database down")).when(pruneFails).pruneBefore(any());
        var recorder = recorder(pruneFails, FogwallTelemetry.disabled(), 100);
        allow(recorder, "widgets");

        assertDoesNotThrow(recorder::flush);

        verify(pruneFails).add(any());
    }

    @Test
    void record_namesTheModeInTelemetry_andAnUnknownProvider() {
        FogwallTelemetry telemetry = mock(FogwallTelemetry.class);
        var recorder = recorder(store, telemetry, 100);

        recorder.record(
                null,
                "acme",
                "widgets",
                FetchActivity.Transport.HTTP,
                ProxyMode.SERVER,
                FetchActivity.Result.ALLOWED,
                null,
                null);

        verify(telemetry).recordFetchDecision("unknown", "server", "ALLOWED", "none");
    }
}
