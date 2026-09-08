package com.rbc.fogwall.db;

import com.rbc.fogwall.db.model.ScmApiProposalRecord;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Map-backed {@link ScmApiProposalStore} for tests that exercise the registrar or a forwarder without a database. */
public class InMemoryScmApiProposalStore implements ScmApiProposalStore {

    private final Map<String, ScmApiProposalRecord> rows = new ConcurrentHashMap<>();

    @Override
    public void save(ScmApiProposalRecord record) {
        rows.put(record.getId(), record);
    }

    @Override
    public void update(ScmApiProposalRecord record) {
        rows.put(record.getId(), record);
    }

    @Override
    public Optional<ScmApiProposalRecord> findById(String id) {
        return Optional.ofNullable(rows.get(id));
    }

    @Override
    public Optional<ScmApiProposalRecord> findByTarget(
            String provider, String repoOwner, String repoName, ScmApiProposalRecord.Kind kind, int number) {
        return rows.values().stream()
                .filter(r -> r.getProvider().equals(provider)
                        && r.getRepoOwner().equals(repoOwner)
                        && r.getRepoName().equals(repoName)
                        && r.getKind() == kind
                        && r.getNumber() == number)
                .findFirst();
    }

    @Override
    public Optional<ScmApiProposalRecord> findByNodeId(String provider, String nodeId) {
        return rows.values().stream()
                .filter(r -> r.getProvider().equals(provider) && nodeId.equals(r.getNodeId()))
                .findFirst();
    }

    @Override
    public List<ScmApiProposalRecord> findByIds(Collection<String> ids) {
        return ids.stream().map(rows::get).filter(r -> r != null).toList();
    }

    public List<ScmApiProposalRecord> all() {
        return List.copyOf(rows.values());
    }

    @Override
    public void initialize() {}
}
