package com.rbc.fogwall.git;

/**
 * The stage a filter or hook occupies in the lifecycle of a request moving through fogwall. Stages replace the former
 * numeric {@code order()} scheme: a step declares which stage it belongs to, and the chain runner derives execution
 * order from (stage, then the step's fixed position within its stage's core-owned list). The five stages, in order of
 * precedence:
 *
 * <ol>
 *   <li>{@link #MANDATORY_PRE} — parsing, enrichment, identity resolution.
 *   <li>{@link #CUSTOM_PRE} — open extension slot (empty until the plugin SPI lands).
 *   <li>{@link #MANDATORY_PROCESSING} — built-in policy checks and the approval gate.
 *   <li>{@link #CUSTOM_POST} — open extension slot (empty until the plugin SPI lands).
 *   <li>{@link #MANDATORY_POST} — finalizers: summary, audit record, forwarding.
 * </ol>
 *
 * <p>The {@code MANDATORY_*} stages are the static core of fogwall — the code that must run on every request, closed to
 * external extension. That closure is enforced by the type system: the mandatory stage types
 * ({@code MandatoryFogwallFilter} / {@code MandatoryFogwallHook}) are {@code sealed} to {@code fogwall-core}, so
 * external code can only ever contribute to the {@code CUSTOM_*} stages.
 *
 * <p>A stage may legally be empty in a given mode or operation — a fetch has no content scans, and server-mode hooks
 * have no pre stage because JGit parses before any hook runs. Emptiness is not a special case; it is the composition
 * declining to place a step there.
 *
 * <p>The ordinal is the ordering key. Do not reorder the constants.
 */
public enum LifecycleStage {
    MANDATORY_PRE,
    CUSTOM_PRE,
    MANDATORY_PROCESSING,
    CUSTOM_POST,
    MANDATORY_POST
}
