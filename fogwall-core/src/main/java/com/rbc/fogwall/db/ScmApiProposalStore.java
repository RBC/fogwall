package com.rbc.fogwall.db;

import com.rbc.fogwall.db.model.ScmApiProposalRecord;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Registry of proposals created or touched through the SCM API proxy. See {@link ScmApiProposalRecord}. */
public interface ScmApiProposalStore {

    void save(ScmApiProposalRecord record);

    /** Replaces every mutable field of the row with {@code record}'s id. */
    void update(ScmApiProposalRecord record);

    Optional<ScmApiProposalRecord> findById(String id);

    Optional<ScmApiProposalRecord> findByTarget(
            String provider, String repoOwner, String repoName, ScmApiProposalRecord.Kind kind, int number);

    /** GitHub addresses a mutation's target by node ID alone, so a row must be reachable that way too. */
    Optional<ScmApiProposalRecord> findByNodeId(String provider, String nodeId);

    List<ScmApiProposalRecord> findByIds(Collection<String> ids);

    void initialize();

    default void close() {}
}
