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

/** MongoDB implementation of {@link MembershipChangeStore}. */
public class MongoMembershipChangeStore implements MembershipChangeStore {

    private static final Logger log = LoggerFactory.getLogger(MongoMembershipChangeStore.class);
    private static final String COLLECTION_NAME = "membership_change_events";

    private final MongoDatabase database;

    public MongoMembershipChangeStore(MongoClient mongoClient, String databaseName) {
        this.database = mongoClient.getDatabase(databaseName);
    }

    @Override
    public void initialize() {
        MongoCollection<Document> col = getCollection();
        col.createIndex(Indexes.ascending("occurredAt", "_id"));
        col.createIndex(Indexes.ascending("member", "occurredAt"));
        col.createIndex(Indexes.ascending("groupName", "occurredAt"));
        log.info("MongoDB membership change store initialized");
    }

    @Override
    public void record(MembershipChangeEvent e) {
        getCollection()
                .insertOne(new Document("_id", e.id())
                        .append("occurredAt", Date.from(e.occurredAt()))
                        .append("action", e.action().name())
                        .append("origin", e.origin().name())
                        .append("actor", e.actor())
                        .append("sourceRevision", e.sourceRevision())
                        .append("groupId", e.groupId())
                        .append("groupName", e.groupName())
                        .append("member", e.member()));
    }

    @Override
    public List<MembershipChangeEvent> find(MembershipChangeQuery query) {
        List<Bson> filters = new ArrayList<>();

        if (query.getAction() != null) {
            filters.add(Filters.eq("action", query.getAction().name()));
        }
        if (query.getOrigin() != null) {
            filters.add(Filters.eq("origin", query.getOrigin().name()));
        }
        if (query.getGroupName() != null) {
            filters.add(Filters.eq("groupName", query.getGroupName()));
        }
        if (query.getMember() != null) {
            filters.add(Filters.eq("member", query.getMember()));
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
            String pattern = "(?i).*" + Pattern.quote(query.getSearch()) + ".*";
            filters.add(Filters.or(Filters.regex("groupName", pattern), Filters.regex("member", pattern)));
        }
        if (query.getAfter() != null) {
            filters.add(MongoKeysetPaging.after("occurredAt", query.getAfter(), query.isNewestFirst()));
        }

        Bson filter = filters.isEmpty() ? new Document() : Filters.and(filters);

        List<MembershipChangeEvent> results = new ArrayList<>();
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

    private static MembershipChangeEvent fromDocument(Document doc) {
        return MembershipChangeEvent.builder()
                .id(doc.getString("_id"))
                .occurredAt(doc.getDate("occurredAt").toInstant())
                .action(MembershipChangeEvent.Action.valueOf(doc.getString("action")))
                .origin(ChangeOrigin.valueOf(doc.getString("origin")))
                .actor(doc.getString("actor"))
                .sourceRevision(doc.getString("sourceRevision"))
                .groupId(doc.getString("groupId"))
                .groupName(doc.getString("groupName"))
                .member(doc.getString("member"))
                .build();
    }
}
