package com.rbc.fogwall.git;

import java.util.Optional;
import org.eclipse.jgit.transport.PreReceiveHook;

/**
 * A {@link PreReceiveHook} that runs in a {@link LifecycleStage} of the server-mode receive chain. Hooks execute in
 * stage order; within a stage, order is the fixed position in the core-owned roster
 * {@link ServerReceivePackFactory#buildValidationHooks} assembles. A hook also declares, via
 * {@link #terminatesChainOnFailure()}, whether recording an issue ends the chain for this push or merely accumulates a
 * finding the pinned verifier reports at the end.
 *
 * <p>The mandatory subtype {@link MandatoryFogwallHook} is {@code sealed} to {@code fogwall-core}, so external code can
 * only ever contribute to a custom stage via {@link CustomFogwallHook}. Lifecycle hooks
 * ({@code PushStorePersistenceHook}, {@code ApprovalPreReceiveHook}) do not implement this interface and are pinned at
 * fixed positions by {@link ServerReceivePackFactory}.
 */
public sealed interface FogwallHook extends PreReceiveHook permits MandatoryFogwallHook, CustomFogwallHook {

    /** The lifecycle stage this hook runs in; see {@link LifecycleStage}. */
    LifecycleStage stage();

    /**
     * Whether recording an issue in this hook ends the chain for this push. Structural guards that make continuing
     * meaningless (empty branch, hidden commits) return {@code true}: the chain runner rejects the commands and stops.
     * Validation hooks return {@code false} (the default) so their findings accumulate and the pusher sees every
     * problem at once.
     */
    default boolean terminatesChainOnFailure() {
        return false;
    }

    /** Returns a human-readable name for this hook, used in logging and diagnostics. */
    String getName();

    /**
     * The persisted {@code step_order} for this hook's audit step — a stable display-ordering value, distinct from
     * chain execution order. Derived from {@link #stepKind()} so a step sorts identically in both proxy modes.
     */
    default int displayOrder() {
        return stepKind().map(PushStepKind::displayOrder).orElse(0);
    }

    /**
     * The canonical identity of the step this hook implements, or empty for lifecycle hooks that are not audited
     * pipeline steps. Hooks that record a {@code PushStep} override this so their step name is the shared,
     * mode-independent {@link PushStepKind#key()} — the same key the equivalent transparent-proxy filter reports.
     */
    default Optional<PushStepKind> stepKind() {
        return Optional.empty();
    }

    /**
     * The name recorded on this hook's {@code PushStep} — the shared {@link PushStepKind#key()} when the hook declares
     * a {@link #stepKind()}, falling back to {@link #getName()} otherwise. Use this in place of a hand-written string
     * so the server-mode step name matches the transparent-proxy filter's.
     */
    default String getStepName() {
        return stepKind().map(PushStepKind::key).orElseGet(this::getName);
    }
}
