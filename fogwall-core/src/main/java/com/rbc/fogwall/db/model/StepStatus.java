package com.rbc.fogwall.db.model;

/** Result status of a single validation step. */
public enum StepStatus {
    PASS,
    FAIL,
    BLOCKED,
    SKIPPED
}
