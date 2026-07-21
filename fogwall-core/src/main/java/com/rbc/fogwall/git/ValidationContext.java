package com.rbc.fogwall.git;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared context for collecting validation issues across multiple pre-receive hooks. Each validation hook writes issues
 * to this context without rejecting commands directly. A verifier hook at the end of the chain reads all collected
 * issues, reports them via sideband, and rejects if any are present.
 *
 * <p>This enables all validators to run on every push so the user sees all problems at once, rather than
 * fix-one-resubmit-fix-another.
 */
public class ValidationContext {

    private final List<ValidationIssue> issues = new ArrayList<>();

    /** Record a policy violation found by a validation hook (the check ran and the push breaks a rule). */
    public void addIssue(String hookName, String summary, String detail) {
        issues.add(new ValidationIssue(hookName, summary, detail, false));
    }

    /**
     * Record that a validation check could not complete (an internal error, not a policy decision). Like a violation,
     * this blocks the push — a security control that cannot run must fail closed, not silently pass — but it is flagged
     * as an error so the audit trail and operators can tell "the check found a problem" apart from "the check itself
     * failed and needs intervention".
     */
    public void addError(String hookName, String summary, String detail) {
        issues.add(new ValidationIssue(hookName, summary, detail, true));
    }

    /** Whether any validation hook reported an issue (a violation or an error). */
    public boolean hasIssues() {
        return !issues.isEmpty();
    }

    /** All collected issues, in the order they were reported. */
    public List<ValidationIssue> getIssues() {
        return Collections.unmodifiableList(issues);
    }

    /**
     * A single issue reported by a hook. {@code error} distinguishes a check that could not complete ({@code true})
     * from a policy violation ({@code false}); both block the push.
     */
    public record ValidationIssue(String hookName, String summary, String detail, boolean error) {}
}
