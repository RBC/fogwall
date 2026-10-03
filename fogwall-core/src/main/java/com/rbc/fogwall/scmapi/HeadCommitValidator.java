package com.rbc.fogwall.scmapi;

import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.model.PushQuery;
import com.rbc.fogwall.db.model.PushStatus;
import java.util.List;

/**
 * Checks whether a commit SHA is one fogwall let through — the {@code providers.<name>.scm-api.require-validated-head}
 * enforcement point, run against a pull/merge request's head commit at create time.
 *
 * <p>Only a push record fogwall approved or forwarded counts. A push that was blocked, rejected, canceled, failed, or
 * is still awaiting review leaves a record too, but its commit never passed fogwall, so it does not validate a head.
 *
 * <p>The lookup is keyed on the commit SHA alone, never the repository: a fork pull/merge request pushes to the fork
 * and targets the upstream, so a repo-scoped lookup would never match, while a SHA is content-addressed and matches
 * wherever it was pushed. {@code push_records.commit_to} already carries an index with this column leading, so the
 * lookup costs no new schema on the JDBC side.
 */
public class HeadCommitValidator {

    /** The push statuses that mean fogwall let the commit through. */
    static final List<PushStatus> PASSED = List.of(PushStatus.APPROVED, PushStatus.FORWARDED);

    private final PushStore pushStore;

    public HeadCommitValidator(PushStore pushStore) {
        this.pushStore = pushStore;
    }

    /**
     * Returns {@code true} when some push record fogwall approved or forwarded has {@code commitTo} equal to
     * {@code sha}.
     */
    public boolean isValidated(String sha) {
        return PASSED.stream()
                .anyMatch(status -> !pushStore
                        .find(PushQuery.builder().commitTo(sha).status(status).build())
                        .isEmpty());
    }
}
