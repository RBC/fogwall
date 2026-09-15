package com.rbc.fogwall.git;

/**
 * How far a validation step surfaces toward the developer. A step declares this once (on its {@link PushStepKind}) and
 * the client-output and dashboard layers read the intent rather than inferring it from step ordering.
 *
 * <p>The levels are cumulative: {@link #SUMMARY} implies {@link #DETAIL}, and both are user-facing (so
 * {@code displayable}), while {@link #INTERNAL} is not.
 */
public enum StepVisibility {
    /** Plumbing with no developer-facing meaning — recorded for audit only (diff generation, request parsing, …). */
    INTERNAL,
    /** Shown in the full push-record detail, but not in the terse git-client summary. */
    DETAIL,
    /** Shown in the terse git-client push summary (and therefore also in the detail view). */
    SUMMARY;

    /** Whether a step at this visibility is user-facing at all (i.e. not pure internal plumbing). */
    public boolean displayable() {
        return this != INTERNAL;
    }

    /** Whether a step at this visibility appears in the terse git-client push summary. */
    public boolean summarizable() {
        return this == SUMMARY;
    }
}
