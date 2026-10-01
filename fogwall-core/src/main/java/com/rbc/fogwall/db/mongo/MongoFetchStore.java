package com.rbc.fogwall.db.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.rbc.fogwall.db.FetchStore;
import com.rbc.fogwall.db.model.FetchActivity;
import com.rbc.fogwall.db.model.FetchActivityQuery;
import com.rbc.fogwall.db.model.FetchRefusal;
import com.rbc.fogwall.git.ProxyMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** MongoDB implementation of {@link FetchStore}. */
public class MongoFetchStore implements FetchStore {

    private static final Logger log = LoggerFactory.getLogger(MongoFetchStore.class);
    private static final String COLLECTION_NAME = "fetch_activity";

    private final MongoDatabase database;

    public MongoFetchStore(MongoClient mongoClient, String databaseName) {
        this.database = mongoClient.getDatabase(databaseName);
    }

    @Override
    public void initialize() {
        MongoCollection<Document> col = getCollection();
        col.createIndex(Indexes.ascending("bucketStart"));
        col.createIndex(Indexes.ascending("provider", "owner", "repoName", "bucketStart"));
        log.info("MongoDB fetch store initialized");
    }

    /** One upsert per row: the dimensions are set only when the row is created, and the count only ever grows. */
    @Override
    public void add(Collection<FetchActivity> increments) {
        if (increments.isEmpty()) {
            return;
        }
        List<WriteModel<Document>> writes = new ArrayList<>();
        for (FetchActivity r : increments) {
            writes.add(new UpdateOneModel<>(
                    Filters.eq("_id", r.getId()),
                    Updates.combine(
                            Updates.inc("fetchCount", r.getFetchCount()),
                            Updates.max("lastSeen", Date.from(r.getLastSeen())),
                            Updates.setOnInsert("bucketStart", Date.from(r.getBucketStart())),
                            Updates.setOnInsert("provider", r.getProvider()),
                            Updates.setOnInsert("owner", r.getOwner()),
                            Updates.setOnInsert("repoName", r.getRepoName()),
                            Updates.setOnInsert("transport", r.getTransport().name()),
                            Updates.setOnInsert("mode", r.getMode().name()),
                            Updates.setOnInsert("result", r.getResult().name()),
                            Updates.setOnInsert(
                                    "refusal",
                                    r.getRefusal() == null
                                            ? null
                                            : r.getRefusal().name()),
                            Updates.setOnInsert("ruleId", r.getRuleId())),
                    new UpdateOptions().upsert(true)));
        }
        getCollection().bulkWrite(writes, new BulkWriteOptions().ordered(false));
    }

    @Override
    public List<FetchActivity> find(FetchActivityQuery query) {
        List<Bson> filters = new ArrayList<>();

        if (query.getResult() != null) {
            filters.add(Filters.eq("result", query.getResult().name()));
        }
        if (query.getTransport() != null) {
            filters.add(Filters.eq("transport", query.getTransport().name()));
        }
        if (query.getProvider() != null) {
            filters.add(Filters.eq("provider", query.getProvider()));
        }
        if (query.getOwner() != null) {
            filters.add(Filters.eq("owner", query.getOwner()));
        }
        if (query.getRepoName() != null) {
            filters.add(Filters.eq("repoName", query.getRepoName()));
        }
        if (query.getSearch() != null && !query.getSearch().isBlank()) {
            String pattern = "(?i).*" + Pattern.quote(query.getSearch()) + ".*";
            filters.add(Filters.or(Filters.regex("owner", pattern), Filters.regex("repoName", pattern)));
        }

        Bson filter = filters.isEmpty() ? new Document() : Filters.and(filters);
        Bson sort = query.isNewestFirst()
                ? Sorts.descending("bucketStart", "lastSeen")
                : Sorts.ascending("bucketStart", "lastSeen");

        List<FetchActivity> results = new ArrayList<>();
        getCollection()
                .find(filter)
                .sort(sort)
                .skip(query.getOffset())
                .limit(query.getLimit())
                .forEach(doc -> results.add(fromDocument(doc)));
        return results;
    }

    @Override
    public List<RepoFetchSummary> summarizeByRepo() {
        List<RepoFetchSummary> results = new ArrayList<>();
        getCollection()
                .aggregate(Arrays.asList(
                        Aggregates.match(Filters.and(Filters.ne("owner", null), Filters.ne("repoName", null))),
                        Aggregates.group(
                                new Document("provider", "$provider")
                                        .append("owner", "$owner")
                                        .append("repoName", "$repoName"),
                                Accumulators.sum("total", "$fetchCount"),
                                Accumulators.sum(
                                        "blocked",
                                        new Document(
                                                "$cond",
                                                Arrays.asList(
                                                        new Document("$eq", Arrays.asList("$result", "BLOCKED")),
                                                        "$fetchCount",
                                                        0)))),
                        Aggregates.sort(Sorts.descending("total"))))
                .forEach(doc -> {
                    Document id = doc.get("_id", Document.class);
                    results.add(new RepoFetchSummary(
                            id.getString("provider"),
                            id.getString("owner"),
                            id.getString("repoName"),
                            ((Number) doc.get("total")).longValue(),
                            ((Number) doc.get("blocked")).longValue()));
                });
        return results;
    }

    @Override
    public void pruneBefore(Instant cutoff) {
        getCollection().deleteMany(Filters.lt("bucketStart", Date.from(cutoff)));
    }

    private MongoCollection<Document> getCollection() {
        return database.getCollection(COLLECTION_NAME);
    }

    private static FetchActivity fromDocument(Document doc) {
        String refusal = doc.getString("refusal");
        return FetchActivity.builder()
                .id(doc.getString("_id"))
                .bucketStart(doc.getDate("bucketStart").toInstant())
                .provider(doc.getString("provider"))
                .owner(doc.getString("owner"))
                .repoName(doc.getString("repoName"))
                .transport(FetchActivity.Transport.valueOf(doc.getString("transport")))
                .mode(ProxyMode.valueOf(doc.getString("mode")))
                .result(FetchActivity.Result.valueOf(doc.getString("result")))
                .refusal(refusal == null ? null : FetchRefusal.valueOf(refusal))
                .ruleId(doc.getString("ruleId"))
                .fetchCount(((Number) doc.get("fetchCount")).longValue())
                .lastSeen(doc.getDate("lastSeen").toInstant())
                .build();
    }
}
