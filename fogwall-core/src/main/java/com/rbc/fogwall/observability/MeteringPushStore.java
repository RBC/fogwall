package com.rbc.fogwall.observability;

import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.model.Attestation;
import com.rbc.fogwall.db.model.PushQuery;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStatus;
import com.rbc.fogwall.db.model.PushSummary;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link PushStore} decorator that records push-decision and forward-outcome metrics on the terminal writes, then
 * delegates every call to the wrapped store. It is the single seam for outcome metrics: both proxy modes and both
 * server-mode transports (HTTP and SSH) already funnel their terminal status transitions through
 * {@link #updateForwardStatus} (FORWARDED/ERROR) and, for pushes that never reach forwarding, through {@link #save}
 * with a REJECTED/CANCELED status — so instrumenting here needs no changes to the hooks, servlet, or receive-pack
 * factory.
 *
 * <p>Wrapping is transparent when telemetry is disabled (the default): the recording branches are skipped and, in
 * particular, {@link #updateForwardStatus} does not incur the extra {@code findById} lookup used only to attach the
 * provider label, so the default push path pays nothing.
 *
 * <p>Every method delegates — including the interface default methods — so a wrapped store keeps its own optimized
 * overrides (e.g. the JDBC lean {@code findSummaries}) rather than falling back to the interface defaults computed
 * against this decorator.
 *
 * <p>Counting is by terminal status and is best-effort: a push's REJECTED/CANCELED row is written once, and its
 * FORWARDED/ERROR transition happens once, so no cross-call state is kept here. A rare duplicate terminal write would
 * over-count by one — acceptable for telemetry.
 */
public class MeteringPushStore implements PushStore {

    private final PushStore delegate;
    private final FogwallTelemetry telemetry;

    public MeteringPushStore(PushStore delegate, FogwallTelemetry telemetry) {
        this.delegate = delegate;
        this.telemetry = telemetry;
    }

    @Override
    public void save(PushRecord record) {
        delegate.save(record);
        // Terminal decisions that never pass through updateForwardStatus. FORWARDED/ERROR are recorded there
        // instead; PENDING/APPROVED are intermediate and are counted only once they reach a terminal state.
        if (telemetry.isEnabled() && record != null) {
            PushStatus status = record.getStatus();
            if (status == PushStatus.REJECTED || status == PushStatus.CANCELED) {
                telemetry.recordDecision(providerLabel(record.getProvider()), status.name());
            }
        }
    }

    @Override
    public void updateForwardStatus(String id, PushStatus status, String errorMessage) {
        delegate.updateForwardStatus(id, status, errorMessage);
        if (telemetry.isEnabled()) {
            String provider = providerLabel(
                    delegate.findById(id).map(PushRecord::getProvider).orElse(null));
            telemetry.recordDecision(provider, status.name());
            if (status == PushStatus.FORWARDED) {
                telemetry.recordForward(provider, true);
            } else if (status == PushStatus.ERROR) {
                telemetry.recordForward(provider, false);
            }
        }
    }

    private static String providerLabel(String provider) {
        return provider == null || provider.isBlank() ? "unknown" : provider;
    }

    // --- straight delegation below ---

    @Override
    public Optional<PushRecord> findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public List<PushRecord> find(PushQuery query) {
        return delegate.find(query);
    }

    @Override
    public List<PushSummary> findSummaries(PushQuery query) {
        return delegate.findSummaries(query);
    }

    @Override
    public void delete(String id) {
        delegate.delete(id);
    }

    @Override
    public PushRecord approve(String id, Attestation attestation) {
        return delegate.approve(id, attestation);
    }

    @Override
    public PushRecord reject(String id, Attestation attestation) {
        return delegate.reject(id, attestation);
    }

    @Override
    public PushRecord cancel(String id, Attestation attestation) {
        return delegate.cancel(id, attestation);
    }

    @Override
    public void initialize() {
        delegate.initialize();
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public List<RepoPushSummary> summarizeByRepo() {
        return delegate.summarizeByRepo();
    }

    @Override
    public Map<String, Long> countByStatus(PushQuery query) {
        return delegate.countByStatus(query);
    }

    @Override
    public Map<String, Map<String, Long>> countPushStatusByUser() {
        return delegate.countPushStatusByUser();
    }
}
