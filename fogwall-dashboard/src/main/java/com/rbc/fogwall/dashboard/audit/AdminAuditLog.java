package com.rbc.fogwall.dashboard.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Emits one structured log line per administrative mutation, regardless of which controller made it, so a log pipeline
 * can filter on {@code action}/{@code outcome} without per-endpoint parsing.
 *
 * <p>Not a database sink — push and proposal records are evidence about proxy traffic and belong in the store; operator
 * actions are operational history and belong with the rest of the application's structured logs.
 */
@Slf4j
@Component
public class AdminAuditLog {

    /** Records a successful mutation. */
    public void success(String action, String target) {
        record(action, target, Outcome.SUCCESS, null);
    }

    /** Records a successful mutation, with a detail string appended (never a secret value). */
    public void success(String action, String target, String detail) {
        record(action, target, Outcome.SUCCESS, detail);
    }

    /** Records a mutation refused by policy (config-locked resource, conflict, last-admin guard, ...). */
    public void denied(String action, String target, String reason) {
        record(action, target, Outcome.DENIED, reason);
    }

    private void record(String action, String target, Outcome outcome, String detail) {
        log.info(format(actor(), action, target, outcome, detail));
    }

    /** Renders one structured log line. Package-private so the format can be asserted without a logging backend. */
    static String format(String actor, String action, String target, Outcome outcome, String detail) {
        String line = "admin_action actor=" + actor + " action=" + action + " target=" + target + " outcome=" + outcome;
        return (detail == null || detail.isBlank()) ? line : line + " detail=" + detail;
    }

    private static String actor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "unknown";
    }

    public enum Outcome {
        SUCCESS,
        DENIED
    }
}
