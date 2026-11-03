package com.rbc.fogwall.observability;

/** How one pipeline stage ended, as recorded on its span and metrics. */
public enum StageOutcome {
    /** The stage ran and let the push continue, warnings included. */
    PASS("pass"),
    /** The stage refused the push: a validation issue, or a rejected or timed-out review. */
    BLOCK("block"),
    /**
     * The stage could not complete: it threw, its check could not run, or the upstream forward failed or was refused.
     */
    ERROR("error");

    private final String label;

    StageOutcome(String label) {
        this.label = label;
    }

    /** The attribute value: {@code pass}, {@code block} or {@code error}. */
    public String label() {
        return label;
    }
}
