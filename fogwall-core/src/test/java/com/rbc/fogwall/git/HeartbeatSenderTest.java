package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.OutputStream;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.transport.ReceivePack;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HeartbeatSenderTest {

    private ReceivePack mockReceivePack() {
        ReceivePack rp = mock(ReceivePack.class);
        when(rp.getMessageOutputStream()).thenReturn(mock(OutputStream.class));
        return rp;
    }

    @Test
    void start_positiveInterval_schedulesOnTheProvidedScheduler() {
        ReceivePack rp = mockReceivePack();
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        HeartbeatSender sender = new HeartbeatSender(rp, Duration.ofSeconds(10), null, scheduler);

        sender.start();

        verify(scheduler).scheduleAtFixedRate(any(Runnable.class), eq(10L), eq(10L), eq(TimeUnit.SECONDS));
    }

    @Test
    void start_zeroInterval_isNoOp() {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        HeartbeatSender sender = new HeartbeatSender(mockReceivePack(), Duration.ZERO, null, scheduler);

        sender.start();

        verifyNoInteractions(scheduler);
    }

    @Test
    void close_cancelsTheTask_butNotTheSharedScheduler() {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(scheduler.scheduleAtFixedRate(any(), anyLongLong(), anyLongLong(), any()))
                .thenAnswer(inv -> future);
        HeartbeatSender sender = new HeartbeatSender(mockReceivePack(), Duration.ofSeconds(5), null, scheduler);
        sender.start();

        sender.close();

        verify(future).cancel(false);
        // The shared scheduler must never be shut down by an individual sender.
        verify(scheduler, never()).shutdown();
        verify(scheduler, never()).shutdownNow();
    }

    @Test
    void writeFailure_cancelsTaskAndInvokesDisconnectOnce() {
        ReceivePack rp = mockReceivePack();
        doThrow(new RuntimeException("client gone")).when(rp).sendMessage(".");
        AtomicInteger disconnects = new AtomicInteger();

        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        ArgumentCaptor<Runnable> tick = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.scheduleAtFixedRate(tick.capture(), anyLongLong(), anyLongLong(), any()))
                .thenAnswer(inv -> future);

        HeartbeatSender sender =
                new HeartbeatSender(rp, Duration.ofSeconds(1), disconnects::incrementAndGet, scheduler);
        sender.start();

        // Fire the scheduled tick manually: the write throws, so the sender should self-cancel and notify.
        tick.getValue().run();

        assertTrue(disconnects.get() == 1, "onDisconnect should fire exactly once on write failure");
        verify(future).cancel(false);
    }

    @Test
    void functional_realScheduler_sendsDotsThenStopsOnClose() throws Exception {
        ReceivePack rp = mockReceivePack();
        AtomicInteger dots = new AtomicInteger();
        // Count each dot written.
        org.mockito.Mockito.doAnswer(inv -> {
                    dots.incrementAndGet();
                    return null;
                })
                .when(rp)
                .sendMessage(".");

        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
        scheduler.setRemoveOnCancelPolicy(true);
        try {
            HeartbeatSender sender = new HeartbeatSender(rp, Duration.ofSeconds(1), null, scheduler);
            sender.start();
            // Wait up to ~3s for at least one dot.
            long deadline = System.currentTimeMillis() + 3_000;
            while (dots.get() == 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            assertTrue(dots.get() >= 1, "at least one heartbeat dot should have been sent");

            sender.close();
            int after = dots.get();
            Thread.sleep(1_200);
            assertTrue(dots.get() == after, "no dots should be sent after close()");
        } finally {
            scheduler.shutdownNow();
        }
    }

    // Mockito's anyLong() matcher, aliased so the long positional args read clearly above.
    private static long anyLongLong() {
        return org.mockito.ArgumentMatchers.anyLong();
    }
}
