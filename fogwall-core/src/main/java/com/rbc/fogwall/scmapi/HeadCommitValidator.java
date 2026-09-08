package com.rbc.fogwall.scmapi;

import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.model.PushQuery;

/**
 * Checks whether a commit SHA is one fogwall has a push record for — the {@code providers.<name>.proposals
 * .require-validated-head} enforcement point, run against a proposal's head commit at create time.
 *
 * <p>The lookup is keyed on the commit SHA alone, never the repository: a fork proposal pushes to the fork and targets
 * the upstream, so a repo-scoped lookup would never match, while a SHA is content-addressed and matches wherever it was
 * pushed. {@code push_records.commit_to} already carries an index with this column leading, so the lookup costs no new
 * schema on the JDBC side.
 */
public class HeadCommitValidator {

    private final PushStore pushStore;

    public HeadCommitValidator(PushStore pushStore) {
        this.pushStore = pushStore;
    }

    /** Returns {@code true} when some push record's {@code commitTo} equals {@code sha}. */
    public boolean isValidated(String sha) {
        return !pushStore.find(PushQuery.builder().commitTo(sha).build()).isEmpty();
    }
}
