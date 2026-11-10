package com.rbc.fogwall.db;

import com.rbc.fogwall.db.model.ActivityQuery;
import com.rbc.fogwall.db.model.ProposalOutcomeCount;
import com.rbc.fogwall.db.model.ScmApiActionQuery;
import com.rbc.fogwall.db.model.ScmApiActionRecord;
import com.rbc.fogwall.db.model.ScmApiActivity;
import com.rbc.fogwall.db.model.ScmApiActivityCount;
import java.util.List;
import java.util.Optional;

/**
 * Storage abstraction for SCM API proxy audit records. Implementations exist for JDBC and MongoDB, mirroring
 * {@link PushStore}'s dual-backend pattern.
 *
 * <p>Unlike {@link PushStore}, records here are write-once: a mutation's allowlist/permission decision and forward
 * outcome are all known synchronously in one pass, so there is no pending-review lifecycle to update.
 */
public interface ScmApiActionStore {

    /** Persist a new action record. */
    void save(ScmApiActionRecord record);

    /** Find an action record by its ID. */
    Optional<ScmApiActionRecord> findById(String id);

    /** Find action records matching the given query, most recent first by default. */
    List<ScmApiActionRecord> find(ScmApiActionQuery query);

    /**
     * Action counts in {@code query}'s window, grouped by its dimensions, by outcome and by {@link ScmApiActivity}. The
     * user dimension is the fogwall user the action resolved to.
     */
    List<ScmApiActivityCount> summarizeActivity(ActivityQuery query);

    /**
     * Pull/merge requests and issues opened through fogwall in {@code query}'s window — forwarded creates — grouped by
     * its dimensions and by the state the entity registry last saw each in. Opened, not merely touched: a pull request
     * opened outside fogwall and later edited through it is not counted here.
     */
    List<ProposalOutcomeCount> summarizeOutcomes(ActivityQuery query);

    /** Initialize the store (create tables/indexes). Called once at startup. */
    void initialize();

    /** Close resources. Called on shutdown. */
    default void close() {}
}
