package com.rbc.fogwall.observability;

import com.rbc.fogwall.db.ScmApiActionStore;
import com.rbc.fogwall.db.model.ScmApiActionQuery;
import com.rbc.fogwall.db.model.ScmApiActionRecord;
import java.util.List;
import java.util.Optional;

/**
 * Thin metering decorator over a {@link ScmApiActionStore}: records the {@code fogwall.scmapi.actions} counter as each
 * audit record is written, then delegates. The action store already sees exactly the population to count — one write
 * per proxied mutation or refusal, never per read — so the single {@link #save} seam is the right place to count,
 * mirroring {@link MeteringPushStore} over the push store.
 *
 * <p>Wrapped only when observability is on ({@code JettyConfigurationBuilder.buildScmApiActionStore}), so the default
 * proxy path pays nothing. Everything but {@code save} is straight delegation.
 */
public class MeteringScmApiActionStore implements ScmApiActionStore {

    private final ScmApiActionStore delegate;
    private final FogwallTelemetry telemetry;

    public MeteringScmApiActionStore(ScmApiActionStore delegate, FogwallTelemetry telemetry) {
        this.delegate = delegate;
        this.telemetry = telemetry;
    }

    @Override
    public void save(ScmApiActionRecord record) {
        delegate.save(record);
        if (telemetry.isEnabled() && record != null) {
            // A refusal of an unrecognized endpoint carries no operation; count it as "unknown" so a CLI upgrade
            // calling something outside the allowlist still shows up rather than being dropped.
            String operation = record.getMutationField() != null
                    ? ScmApiOperations.normalize(record.getMutationField())
                    : "unknown";
            String outcome = record.getStatus() != null ? record.getStatus().name() : "ERROR";
            telemetry.recordScmApiAction(providerLabel(record.getProvider()), operation, outcome);
        }
    }

    @Override
    public Optional<ScmApiActionRecord> findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public List<ScmApiActionRecord> find(ScmApiActionQuery query) {
        return delegate.find(query);
    }

    @Override
    public void initialize() {
        delegate.initialize();
    }

    @Override
    public void close() {
        delegate.close();
    }

    private static String providerLabel(String provider) {
        return provider == null || provider.isBlank() ? "unknown" : provider;
    }
}
