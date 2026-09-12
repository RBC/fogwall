package com.rbc.fogwall.db.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Updates;
import com.rbc.fogwall.db.ScmApiEntityStore;
import com.rbc.fogwall.db.model.ScmApiEntityRecord;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.bson.Document;

/** MongoDB-backed {@link ScmApiEntityStore} over the {@code scm_api_entities} collection. */
public class MongoScmApiEntityStore implements ScmApiEntityStore {

    private static final String COLLECTION_NAME = "scm_api_entities";

    private final MongoDatabase database;

    public MongoScmApiEntityStore(MongoClient mongoClient, String databaseName) {
        this.database = mongoClient.getDatabase(databaseName);
    }

    @Override
    public void initialize() {
        MongoCollection<Document> col = getCollection();
        col.createIndex(
                Indexes.ascending("provider", "repo_owner", "repo_name", "kind", "entity_number"),
                new IndexOptions().unique(true));
        col.createIndex(Indexes.ascending("provider", "node_id"));
    }

    @Override
    public void save(ScmApiEntityRecord r) {
        getCollection()
                .insertOne(new Document("_id", r.getId())
                        .append("provider", r.getProvider())
                        .append("repo_owner", r.getRepoOwner())
                        .append("repo_name", r.getRepoName())
                        .append("kind", r.getKind().name())
                        .append("entity_number", r.getNumber())
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
    public void update(ScmApiEntityRecord r) {
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
    public Optional<ScmApiEntityRecord> findById(String id) {
        return Optional.ofNullable(getCollection().find(Filters.eq("_id", id)).first())
                .map(MongoScmApiEntityStore::toRecord);
    }

    @Override
    public Optional<ScmApiEntityRecord> findByTarget(
            String provider, String repoOwner, String repoName, ScmApiEntityRecord.Kind kind, int number) {
        return Optional.ofNullable(getCollection()
                        .find(Filters.and(
                                Filters.eq("provider", provider),
                                Filters.eq("repo_owner", repoOwner),
                                Filters.eq("repo_name", repoName),
                                Filters.eq("kind", kind.name()),
                                Filters.eq("entity_number", number)))
                        .first())
                .map(MongoScmApiEntityStore::toRecord);
    }

    @Override
    public Optional<ScmApiEntityRecord> findByNodeId(String provider, String nodeId) {
        return Optional.ofNullable(getCollection()
                        .find(Filters.and(Filters.eq("provider", provider), Filters.eq("node_id", nodeId)))
                        .first())
                .map(MongoScmApiEntityStore::toRecord);
    }

    @Override
    public List<ScmApiEntityRecord> findByIds(Collection<String> ids) {
        List<ScmApiEntityRecord> results = new ArrayList<>();
        if (ids.isEmpty()) {
            return results;
        }
        getCollection().find(Filters.in("_id", ids)).forEach(doc -> results.add(toRecord(doc)));
        return results;
    }

    private static ScmApiEntityRecord toRecord(Document doc) {
        return ScmApiEntityRecord.builder()
                .id(doc.getString("_id"))
                .provider(doc.getString("provider"))
                .repoOwner(doc.getString("repo_owner"))
                .repoName(doc.getString("repo_name"))
                .kind(ScmApiEntityRecord.Kind.valueOf(doc.getString("kind")))
                .number(doc.getInteger("entity_number"))
                .url(doc.getString("url"))
                .nodeId(doc.getString("node_id"))
                .title(doc.getString("title"))
                .state(ScmApiEntityRecord.State.valueOf(doc.getString("state")))
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
