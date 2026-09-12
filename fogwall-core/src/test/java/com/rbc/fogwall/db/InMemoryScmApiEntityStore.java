package com.rbc.fogwall.db;

import com.rbc.fogwall.db.model.ScmApiEntityRecord;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Map-backed {@link ScmApiEntityStore} for tests that exercise the registrar or a forwarder without a database. */
public class InMemoryScmApiEntityStore implements ScmApiEntityStore {

    private final Map<String, ScmApiEntityRecord> rows = new ConcurrentHashMap<>();

    @Override
    public void save(ScmApiEntityRecord record) {
        rows.put(record.getId(), record);
    }

    @Override
    public void update(ScmApiEntityRecord record) {
        rows.put(record.getId(), record);
    }

    @Override
    public Optional<ScmApiEntityRecord> findById(String id) {
        return Optional.ofNullable(rows.get(id));
    }

    @Override
    public Optional<ScmApiEntityRecord> findByTarget(
            String provider, String repoOwner, String repoName, ScmApiEntityRecord.Kind kind, int number) {
        return rows.values().stream()
                .filter(r -> r.getProvider().equals(provider)
                        && r.getRepoOwner().equals(repoOwner)
                        && r.getRepoName().equals(repoName)
                        && r.getKind() == kind
                        && r.getNumber() == number)
                .findFirst();
    }

    @Override
    public Optional<ScmApiEntityRecord> findByNodeId(String provider, String nodeId) {
        return rows.values().stream()
                .filter(r -> r.getProvider().equals(provider) && nodeId.equals(r.getNodeId()))
                .findFirst();
    }

    @Override
    public List<ScmApiEntityRecord> findByIds(Collection<String> ids) {
        return ids.stream().map(rows::get).filter(r -> r != null).toList();
    }

    public List<ScmApiEntityRecord> all() {
        return List.copyOf(rows.values());
    }

    @Override
    public void initialize() {}
}
