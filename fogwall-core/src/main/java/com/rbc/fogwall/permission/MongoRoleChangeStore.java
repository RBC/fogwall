package com.rbc.fogwall.permission;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.rbc.fogwall.db.mongo.MongoKeysetPaging;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** MongoDB implementation of {@link RoleChangeStore}. */
public class MongoRoleChangeStore implements RoleChangeStore {

    private static final Logger log = LoggerFactory.getLogger(MongoRoleChangeStore.class);
    private static final String COLLECTION_NAME = "role_change_events";

    private final MongoDatabase database;

    public MongoRoleChangeStore(MongoClient mongoClient, String databaseName) {
        this.database = mongoClient.getDatabase(databaseName);
    }

    @Override
    public void initialize() {
        MongoCollection<Document> col = getCollection();
        col.createIndex(Indexes.ascending("occurredAt", "_id"));
        col.createIndex(Indexes.ascending("username", "occurredAt"));
        log.info("MongoDB role change store initialized");
    }

    @Override
    public void record(RoleChangeEvent e) {
        getCollection()
                .insertOne(new Document("_id", e.id())
                        .append("occurredAt", Date.from(e.occurredAt()))
                        .append("origin", e.origin().name())
                        .append("actor", e.actor())
                        .append("username", e.username())
                        .append("previousRoles", e.previousRoles())
                        .append("roles", e.roles()));
    }

    @Override
    public List<RoleChangeEvent> find(RoleChangeQuery query) {
        List<Bson> filters = new ArrayList<>();

        if (query.getOrigin() != null) {
            filters.add(Filters.eq("origin", query.getOrigin().name()));
        }
        if (query.getUsername() != null) {
            filters.add(Filters.eq("username", query.getUsername()));
        }
        if (query.getActor() != null) {
            filters.add(Filters.eq("actor", query.getActor()));
        }
        if (query.getFrom() != null) {
            filters.add(Filters.gte("occurredAt", Date.from(query.getFrom())));
        }
        if (query.getTo() != null) {
            filters.add(Filters.lt("occurredAt", Date.from(query.getTo())));
        }
        if (query.getSearch() != null && !query.getSearch().isBlank()) {
            filters.add(Filters.regex("username", "(?i).*" + Pattern.quote(query.getSearch()) + ".*"));
        }
        if (query.getAfter() != null) {
            filters.add(MongoKeysetPaging.after("occurredAt", query.getAfter(), query.isNewestFirst()));
        }

        Bson filter = filters.isEmpty() ? new Document() : Filters.and(filters);

        List<RoleChangeEvent> results = new ArrayList<>();
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

    private static RoleChangeEvent fromDocument(Document doc) {
        return RoleChangeEvent.builder()
                .id(doc.getString("_id"))
                .occurredAt(doc.getDate("occurredAt").toInstant())
                .origin(ChangeOrigin.valueOf(doc.getString("origin")))
                .actor(doc.getString("actor"))
                .username(doc.getString("username"))
                .previousRoles(doc.getList("previousRoles", String.class))
                .roles(doc.getList("roles", String.class))
                .build();
    }
}
