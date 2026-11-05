package com.rbc.fogwall.permission;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.rbc.fogwall.db.model.MatchTarget;
import com.rbc.fogwall.db.model.MatchType;
import com.rbc.fogwall.db.mongo.MongoKeysetPaging;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** MongoDB implementation of {@link PermissionChangeStore}. */
public class MongoPermissionChangeStore implements PermissionChangeStore {

    private static final Logger log = LoggerFactory.getLogger(MongoPermissionChangeStore.class);
    private static final String COLLECTION_NAME = "permission_change_events";

    private final MongoDatabase database;

    public MongoPermissionChangeStore(MongoClient mongoClient, String databaseName) {
        this.database = mongoClient.getDatabase(databaseName);
    }

    @Override
    public void initialize() {
        MongoCollection<Document> col = getCollection();
        col.createIndex(Indexes.ascending("occurredAt", "_id"));
        col.createIndex(Indexes.ascending("subject", "occurredAt"));
        col.createIndex(Indexes.ascending("actor", "occurredAt"));
        log.info("MongoDB permission change store initialized");
    }

    @Override
    public void record(PermissionChangeEvent e) {
        getCollection()
                .insertOne(new Document("_id", e.id())
                        .append("occurredAt", Date.from(e.occurredAt()))
                        .append("action", e.action().name())
                        .append("origin", e.origin().name())
                        .append("actor", e.actor())
                        .append("sourceRevision", e.sourceRevision())
                        .append("subjectType", e.subjectType().name())
                        .append("subject", e.subject())
                        .append("permissionId", e.permissionId())
                        .append("provider", e.provider())
                        .append("target", e.target().name())
                        .append("value", e.value())
                        .append("matchType", e.matchType().name())
                        .append("operation", e.grant().name()));
    }

    @Override
    public List<PermissionChangeEvent> find(PermissionChangeQuery query) {
        List<Bson> filters = new ArrayList<>();

        if (query.getAction() != null) {
            filters.add(Filters.eq("action", query.getAction().name()));
        }
        if (query.getOrigin() != null) {
            filters.add(Filters.eq("origin", query.getOrigin().name()));
        }
        if (query.getSubjectType() != null) {
            filters.add(Filters.eq("subjectType", query.getSubjectType().name()));
        }
        if (query.getSubject() != null) {
            filters.add(Filters.eq("subject", query.getSubject()));
        }
        if (query.getActor() != null) {
            filters.add(Filters.eq("actor", query.getActor()));
        }
        if (query.getProvider() != null) {
            filters.add(Filters.eq("provider", query.getProvider()));
        }
        if (query.getFrom() != null) {
            filters.add(Filters.gte("occurredAt", Date.from(query.getFrom())));
        }
        if (query.getTo() != null) {
            filters.add(Filters.lt("occurredAt", Date.from(query.getTo())));
        }
        if (query.getSearch() != null && !query.getSearch().isBlank()) {
            String pattern = "(?i).*" + Pattern.quote(query.getSearch()) + ".*";
            filters.add(Filters.or(Filters.regex("subject", pattern), Filters.regex("value", pattern)));
        }
        if (query.getAfter() != null) {
            filters.add(MongoKeysetPaging.after("occurredAt", query.getAfter(), query.isNewestFirst()));
        }

        Bson filter = filters.isEmpty() ? new Document() : Filters.and(filters);

        List<PermissionChangeEvent> results = new ArrayList<>();
        getCollection()
                .find(filter)
                .sort(MongoKeysetPaging.sort("occurredAt", query.isNewestFirst()))
                .skip(query.getOffset())
                .limit(query.getLimit())
                .forEach(doc -> results.add(fromDocument(doc)));
        return results;
    }

    private MongoCollection<Document> getCollection() {
        return database.getCollection(COLLECTION_NAME);
    }

    private static PermissionChangeEvent fromDocument(Document doc) {
        return PermissionChangeEvent.builder()
                .id(doc.getString("_id"))
                .occurredAt(doc.getDate("occurredAt").toInstant())
                .action(PermissionChangeEvent.Action.valueOf(doc.getString("action")))
                .origin(ChangeOrigin.valueOf(doc.getString("origin")))
                .actor(doc.getString("actor"))
                .sourceRevision(doc.getString("sourceRevision"))
                .subjectType(PermissionChangeEvent.SubjectType.valueOf(doc.getString("subjectType")))
                .subject(doc.getString("subject"))
                .permissionId(doc.getString("permissionId"))
                .provider(doc.getString("provider"))
                .target(MatchTarget.valueOf(doc.getString("target")))
                .value(doc.getString("value"))
                .matchType(MatchType.valueOf(doc.getString("matchType")))
                .grant(RepoPermission.Grant.valueOf(doc.getString("operation")))
                .build();
    }
}
