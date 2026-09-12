package com.rbc.fogwall.db;

import com.rbc.fogwall.db.model.ScmApiEntityRecord;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Registry of SCM API entities (pull/merge requests and issues) created or touched through the proxy. See
 * {@link ScmApiEntityRecord}.
 */
public interface ScmApiEntityStore {

    void save(ScmApiEntityRecord record);

    /** Replaces every mutable field of the row with {@code record}'s id. */
    void update(ScmApiEntityRecord record);

    Optional<ScmApiEntityRecord> findById(String id);

    Optional<ScmApiEntityRecord> findByTarget(
            String provider, String repoOwner, String repoName, ScmApiEntityRecord.Kind kind, int number);

    /** GitHub addresses a mutation's target by node ID alone, so a row must be reachable that way too. */
    Optional<ScmApiEntityRecord> findByNodeId(String provider, String nodeId);

    List<ScmApiEntityRecord> findByIds(Collection<String> ids);

    void initialize();

    default void close() {}
}
