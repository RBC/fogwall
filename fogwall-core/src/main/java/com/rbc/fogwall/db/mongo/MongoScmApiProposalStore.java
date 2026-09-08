package com.rbc.fogwall.db.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Updates;
import com.rbc.fogwall.db.ScmApiProposalStore;
import com.rbc.fogwall.db.model.ScmApiProposalRecord;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.bson.Document;

/** MongoDB-backed {@link ScmApiProposalStore} over the {@code scm_api_proposals} collection. */
public class MongoScmApiProposalStore implements ScmApiProposalStore {

    private static final String COLLECTION_NAME = "scm_api_proposals";

    private final MongoDatabase database;

    public MongoScmApiProposalStore(MongoClient mongoClient, String databaseName) {
        this.database = mongoClient.getDatabase(databaseName);
    }

    @Override
    public void initialize() {
        MongoCollection<Document> col = getCollection();
        col.createIndex(
                Indexes.ascending("provider", "repo_owner", "repo_name", "kind", "proposal_number"),
                new IndexOptions().unique(true));
        col.createIndex(Indexes.ascending("provider", "node_id"));
    }

    @Override
    public void save(ScmApiProposalRecord r) {
        getCollection()
                .insertOne(new Document("_id", r.getId())
                        .append("provider", r.getProvider())
                        .append("repo_owner", r.getRepoOwner())
                        .append("repo_name", r.getRepoName())
                        .append("kind", r.getKind().name())
                        .append("proposal_number", r.getNumber())
                        .append("url", r.getUrl())
                        .append("node_id", r.getNodeId())
                        .append("title", r.getTitle())
                        .append("state", r.getState().name())
                        .append("created_by", r.getCreatedBy())
                        .append("created_by_scm_username", r.getCreatedByScmUsername())
                        .append("created_at", Date.from(r.getCreatedAt()))
                        .append("updated_at", Date.from(r.getUpdatedAt()))
                        .append("created_action_id", r.getCreatedActionId())
                        .append("last_action_id", r.getLastActionId()));
    }

    @Override
    public void update(ScmApiProposalRecord r) {
        getCollection()
                .updateOne(
                        Filters.eq("_id", r.getId()),
                        Updates.combine(
                                Updates.set("url", r.getUrl()),
                                Updates.set("node_id", r.getNodeId()),
                                Updates.set("title", r.getTitle()),
                                Updates.set("state", r.getState().name()),
                                Updates.set("updated_at", Date.from(r.getUpdatedAt())),
                                Updates.set("last_action_id", r.getLastActionId())));
    }

    @Override
    public Optional<ScmApiProposalRecord> findById(String id) {
        return Optional.ofNullable(getCollection().find(Filters.eq("_id", id)).first())
                .map(MongoScmApiProposalStore::toRecord);
    }

    @Override
    public Optional<ScmApiProposalRecord> findByTarget(
            String provider, String repoOwner, String repoName, ScmApiProposalRecord.Kind kind, int number) {
        return Optional.ofNullable(getCollection()
                        .find(Filters.and(
                                Filters.eq("provider", provider),
                                Filters.eq("repo_owner", repoOwner),
                                Filters.eq("repo_name", repoName),
                                Filters.eq("kind", kind.name()),
                                Filters.eq("proposal_number", number)))
                        .first())
                .map(MongoScmApiProposalStore::toRecord);
    }

    @Override
    public Optional<ScmApiProposalRecord> findByNodeId(String provider, String nodeId) {
        return Optional.ofNullable(getCollection()
                        .find(Filters.and(Filters.eq("provider", provider), Filters.eq("node_id", nodeId)))
                        .first())
                .map(MongoScmApiProposalStore::toRecord);
    }

    @Override
    public List<ScmApiProposalRecord> findByIds(Collection<String> ids) {
        List<ScmApiProposalRecord> results = new ArrayList<>();
        if (ids.isEmpty()) {
            return results;
        }
        getCollection().find(Filters.in("_id", ids)).forEach(doc -> results.add(toRecord(doc)));
        return results;
    }

    private static ScmApiProposalRecord toRecord(Document doc) {
        return ScmApiProposalRecord.builder()
                .id(doc.getString("_id"))
                .provider(doc.getString("provider"))
                .repoOwner(doc.getString("repo_owner"))
                .repoName(doc.getString("repo_name"))
                .kind(ScmApiProposalRecord.Kind.valueOf(doc.getString("kind")))
                .number(doc.getInteger("proposal_number"))
                .url(doc.getString("url"))
                .nodeId(doc.getString("node_id"))
                .title(doc.getString("title"))
                .state(ScmApiProposalRecord.State.valueOf(doc.getString("state")))
                .createdBy(doc.getString("created_by"))
                .createdByScmUsername(doc.getString("created_by_scm_username"))
                .createdAt(doc.getDate("created_at").toInstant())
                .updatedAt(doc.getDate("updated_at").toInstant())
                .createdActionId(doc.getString("created_action_id"))
                .lastActionId(doc.getString("last_action_id"))
                .build();
    }

    private MongoCollection<Document> getCollection() {
        return database.getCollection(COLLECTION_NAME);
    }
}
