package com.rbc.fogwall.observability;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MeteringPushStoreTest {

    private PushRecord record(String provider, PushStatus status) {
        return PushRecord.builder().provider(provider).status(status).build();
    }

    @Test
    void save_terminalRejected_recordsDecision_andDelegates() {
        PushStore delegate = mock(PushStore.class);
        FogwallTelemetry telemetry = mock(FogwallTelemetry.class);
        when(telemetry.isEnabled()).thenReturn(true);
        MeteringPushStore store = new MeteringPushStore(delegate, telemetry);

        PushRecord rec = record("github", PushStatus.REJECTED);
        store.save(rec);

        verify(delegate).save(rec);
        verify(telemetry).recordDecision("github", "REJECTED");
    }

    @Test
    void save_nonTerminal_recordsNothing() {
        PushStore delegate = mock(PushStore.class);
        FogwallTelemetry telemetry = mock(FogwallTelemetry.class);
        when(telemetry.isEnabled()).thenReturn(true);
        MeteringPushStore store = new MeteringPushStore(delegate, telemetry);

        store.save(record("github", PushStatus.PENDING));

        verify(telemetry, never()).recordDecision(anyString(), anyString());
    }

    @Test
    void updateForwardStatus_forwarded_recordsDecisionAndForwardSuccess() {
        PushStore delegate = mock(PushStore.class);
        when(delegate.findById("id1")).thenReturn(Optional.of(record("gitlab", PushStatus.FORWARDED)));
        FogwallTelemetry telemetry = mock(FogwallTelemetry.class);
        when(telemetry.isEnabled()).thenReturn(true);
        MeteringPushStore store = new MeteringPushStore(delegate, telemetry);

        store.updateForwardStatus("id1", PushStatus.FORWARDED, null);

        verify(delegate).updateForwardStatus("id1", PushStatus.FORWARDED, null);
        verify(telemetry).recordDecision("gitlab", "FORWARDED");
        verify(telemetry).recordForward("gitlab", true);
    }

    @Test
    void updateForwardStatus_error_recordsForwardFailure() {
        PushStore delegate = mock(PushStore.class);
        when(delegate.findById("id2")).thenReturn(Optional.of(record("github", PushStatus.ERROR)));
        FogwallTelemetry telemetry = mock(FogwallTelemetry.class);
        when(telemetry.isEnabled()).thenReturn(true);
        MeteringPushStore store = new MeteringPushStore(delegate, telemetry);

        store.updateForwardStatus("id2", PushStatus.ERROR, "boom");

        verify(telemetry).recordDecision("github", "ERROR");
        verify(telemetry).recordForward("github", false);
    }

    @Test
    void disabled_recordsNothing_andSkipsLookup() {
        PushStore delegate = mock(PushStore.class);
        FogwallTelemetry telemetry = mock(FogwallTelemetry.class);
        when(telemetry.isEnabled()).thenReturn(false);
        MeteringPushStore store = new MeteringPushStore(delegate, telemetry);

        store.save(record("github", PushStatus.REJECTED));
        store.updateForwardStatus("id3", PushStatus.FORWARDED, null);

        verify(delegate).save(any());
        verify(delegate).updateForwardStatus("id3", PushStatus.FORWARDED, null);
        // No provider lookup and no recording when disabled.
        verify(delegate, never()).findById(anyString());
        verify(telemetry, never()).recordDecision(anyString(), anyString());
        verify(telemetry, never()).recordForward(anyString(), anyBoolean());
    }

    @Test
    void delegation_passthrough_returnsDelegateResult() {
        PushStore delegate = mock(PushStore.class);
        FogwallTelemetry telemetry = mock(FogwallTelemetry.class);
        PushRecord rec = record("github", PushStatus.PENDING);
        when(delegate.findById("x")).thenReturn(Optional.of(rec));
        MeteringPushStore store = new MeteringPushStore(delegate, telemetry);

        assertSame(rec, store.findById("x").orElseThrow());
        // Read paths never touch telemetry.
        verifyNoInteractions(telemetry);
    }
}
