package com.rbc.fogwall.db.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.rbc.fogwall.db.FetchStore;
import com.rbc.fogwall.db.model.FetchRecord;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** MongoDB implementation of {@link FetchStore}. */
public class MongoFetchStore implements FetchStore {

    private static final Logger log = LoggerFactory.getLogger(MongoFetchStore.class);
    private static final String COLLECTION_NAME = "fetch_records";

    private final MongoClient mongoClient;
    private final MongoDatabase database;

    public MongoFetchStore(MongoClient mongoClient, String databaseName) {
        this.mongoClient = mongoClient;
        this.database = mongoClient.getDatabase(databaseName);
    }

    @Override
    public void initialize() {
        MongoCollection<Document> col = getCollection();
        col.createIndex(Indexes.descending("timestamp"));
        col.createIndex(Indexes.ascending("provider", "owner", "repoName"));
        log.info("MongoDB fetch store initialized");
    }

    @Override
    public void record(FetchRecord r) {
        getCollection()
                .insertOne(new Document("_id", r.getId())
                        .append("timestamp", Date.from(r.getTimestamp()))
                        .append("provider", r.getProvider())
                        .append("owner", r.getOwner())
                        .append("repoName", r.getRepoName())
                        .append("result", r.getResult().name())
                        .append("pushUsername", r.getPushUsername())
                        .append("resolvedUser", r.getResolvedUser()));
    }

    @Override
    public List<FetchRecord> findRecent(int limit) {
        List<FetchRecord> results = new ArrayList<>();
        getCollection()
                .find()
                .sort(Sorts.descending("timestamp"))
                .limit(limit)
                .forEach(doc -> results.add(fromDocument(doc)));
        return results;
    }

    @Override
    public List<FetchRecord> findByRepo(String provider, String owner, String repoName, int limit) {
        List<FetchRecord> results = new ArrayList<>();
        getCollection()
                .find(Filters.and(
                        Filters.eq("provider", provider), Filters.eq("owner", owner), Filters.eq("repoName", repoName)))
                .sort(Sorts.descending("timestamp"))
                .limit(limit)
                .forEach(doc -> results.add(fromDocument(doc)));
        return results;
    }

    @Override
    public List<RepoFetchSummary> summarizeByRepo() {
        // GROUP BY provider + owner + repoName, COUNT total, SUM(result == BLOCKED)
        List<RepoFetchSummary> results = new ArrayList<>();
        getCollection()
                .aggregate(Arrays.asList(
                        Aggregates.group(
                                new Document("provider", "$provider")
                                        .append("owner", "$owner")
                                        .append("repoName", "$repoName"),
                                Accumulators.sum("total", 1),
                                Accumulators.sum(
                                        "blocked",
                                        new Document(
                                                "$cond",
                                                Arrays.asList(
                                                        new Document("$eq", Arrays.asList("$result", "BLOCKED")),
                                                        1,
                                                        0)))),
                        Aggregates.sort(Sorts.descending("total"))))
                .forEach(doc -> {
                    Document id = doc.get("_id", Document.class);
                    results.add(new RepoFetchSummary(
                            id.getString("provider"),
                            id.getString("owner"),
                            id.getString("repoName"),
                            doc.getInteger("total", 0),
                            doc.getInteger("blocked", 0)));
                });
        return results;
    }

    private MongoCollection<Document> getCollection() {
        return database.getCollection(COLLECTION_NAME);
    }

    private static FetchRecord fromDocument(Document doc) {
        return FetchRecord.builder()
                .id(doc.getString("_id"))
                .timestamp(doc.getDate("timestamp").toInstant())
                .provider(doc.getString("provider"))
                .owner(doc.getString("owner"))
                .repoName(doc.getString("repoName"))
                .result(FetchRecord.Result.valueOf(doc.getString("result")))
                .pushUsername(doc.getString("pushUsername"))
                .resolvedUser(doc.getString("resolvedUser"))
                .build();
    }
}
