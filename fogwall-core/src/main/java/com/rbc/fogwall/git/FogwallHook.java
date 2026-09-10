package com.rbc.fogwall.git;

import java.util.Optional;
import org.eclipse.jgit.transport.PreReceiveHook;

/**
 * A {@link PreReceiveHook} with an associated order value, used to sort hooks in the server mode receive chain.
 *
 * <p>Hooks are executed in ascending order of their {@link #getOrder()} value. The following ranges mirror the filter
 * order scheme and are reserved:
 *
 * <ul>
 *   <li><b>Negative (&lt;= -1):</b> System lifecycle hooks that must run first or last (e.g., persistence hooks).
 *       Custom hooks must not use negative values.
 *   <li><b>0-199 authorization range:</b> URL rule and user/repo/provider permission checks.
 *   <li><b>200-399 content filtering range:</b> Commit content validation (emails, messages, diffs, signatures,
 *       secrets). Built-in hooks use multiples of 10 within this range to leave room for custom hooks between them.
 *   <li><b>400-499 post-validation range:</b> Reserved for future use (e.g., outbound commit decoration).
 *   <li><b>500+ extended range:</b> Custom bespoke hooks.
 * </ul>
 *
 * <p>Lifecycle hooks ({@code PushStorePersistenceHook}, {@code ApprovalPreReceiveHook}) do not implement this interface
 * and are always pinned at fixed positions in the chain by {@link ServerReceivePackFactory}.
 */
public interface FogwallHook extends PreReceiveHook {

    /** Returns the order value that determines this hook's position in the chain. */
    int getOrder();

    /** Returns a human-readable name for this hook, used in logging and diagnostics. */
    String getName();

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
