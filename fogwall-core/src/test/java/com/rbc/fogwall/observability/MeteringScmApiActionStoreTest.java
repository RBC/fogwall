package com.rbc.fogwall.observability;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rbc.fogwall.db.ScmApiActionStore;
import com.rbc.fogwall.db.model.ScmApiActionRecord;
import com.rbc.fogwall.db.model.ScmApiActionStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MeteringScmApiActionStoreTest {

    private ScmApiActionRecord record(String provider, String mutationField, ScmApiActionStatus status) {
        return ScmApiActionRecord.builder()
                .provider(provider)
                .mutationField(mutationField)
                .status(status)
                .build();
    }

    @Test
    void save_forwardedMutation_recordsAction_andDelegates() {
        ScmApiActionStore delegate = mock(ScmApiActionStore.class);
        FogwallTelemetry telemetry = mock(FogwallTelemetry.class);
        when(telemetry.isEnabled()).thenReturn(true);
        MeteringScmApiActionStore store = new MeteringScmApiActionStore(delegate, telemetry);

        ScmApiActionRecord rec = record("github", "createIssue", ScmApiActionStatus.FORWARDED);
        store.save(rec);

        verify(delegate).save(rec);
        verify(telemetry).recordScmApiAction("github", "issue.create", "FORWARDED");
    }

    @Test
    void save_refusalWithNoOperation_recordsUnknown() {
        ScmApiActionStore delegate = mock(ScmApiActionStore.class);
        FogwallTelemetry telemetry = mock(FogwallTelemetry.class);
        when(telemetry.isEnabled()).thenReturn(true);
        MeteringScmApiActionStore store = new MeteringScmApiActionStore(delegate, telemetry);

        store.save(record("gitlab", null, ScmApiActionStatus.DENIED));

        verify(telemetry).recordScmApiAction("gitlab", "unknown", "DENIED");
    }

    @Test
    void disabled_recordsNothing() {
        ScmApiActionStore delegate = mock(ScmApiActionStore.class);
        FogwallTelemetry telemetry = mock(FogwallTelemetry.class);
        when(telemetry.isEnabled()).thenReturn(false);
        MeteringScmApiActionStore store = new MeteringScmApiActionStore(delegate, telemetry);

        store.save(record("github", "createIssue", ScmApiActionStatus.FORWARDED));

        verify(delegate).save(any());
        verify(telemetry, never()).recordScmApiAction(anyString(), anyString(), anyString());
    }

    @Test
    void delegation_passthrough_returnsDelegateResult() {
        ScmApiActionStore delegate = mock(ScmApiActionStore.class);
        FogwallTelemetry telemetry = mock(FogwallTelemetry.class);
        ScmApiActionRecord rec = record("github", "createIssue", ScmApiActionStatus.FORWARDED);
        when(delegate.findById("x")).thenReturn(Optional.of(rec));
        MeteringScmApiActionStore store = new MeteringScmApiActionStore(delegate, telemetry);

        assertSame(rec, store.findById("x").orElseThrow());
        // Read paths never touch telemetry.
        verifyNoInteractions(telemetry);
    }
}
