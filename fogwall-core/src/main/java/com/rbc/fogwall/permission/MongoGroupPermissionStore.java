package com.rbc.fogwall.permission;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.rbc.fogwall.db.model.MatchTarget;
import com.rbc.fogwall.db.model.MatchType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MongoGroupPermissionStore implements GroupPermissionStore {

    private static final Logger log = LoggerFactory.getLogger(MongoGroupPermissionStore.class);
    private static final String GROUPS_COLLECTION = "permission_groups";
    private static final String RULES_COLLECTION = "group_permissions";
    private static final String MEMBERS_COLLECTION = "group_members";

    private final MongoDatabase database;

    public MongoGroupPermissionStore(MongoClient mongoClient, String databaseName) {
        this.database = mongoClient.getDatabase(databaseName);
    }

    @Override
    public void initialize() {
        database.getCollection(GROUPS_COLLECTION).createIndex(Indexes.ascending("name"));
        database.getCollection(RULES_COLLECTION).createIndex(Indexes.ascending("groupId"));
        database.getCollection(RULES_COLLECTION).createIndex(Indexes.ascending("provider"));
        database.getCollection(MEMBERS_COLLECTION).createIndex(Indexes.ascending("username"));
        database.getCollection(MEMBERS_COLLECTION).createIndex(Indexes.ascending("groupId"));
        log.info("MongoDB group permission store initialized");
    }

    // ---- groups ----

    @Override
    public void saveGroup(PermissionGroup g) {
        groups().insertOne(new Document("_id", g.getId())
                .append("name", g.getName())
                .append("description", g.getDescription())
                .append("source", g.getSource().name()));
    }

    @Override
    public void updateGroup(String id, String name, String description) {
        groups().updateOne(
                        Filters.eq("_id", id),
                        new Document("$set", new Document("name", name).append("description", description)));
    }

    @Override
    public void deleteGroup(String id) {
        groups().deleteOne(Filters.eq("_id", id));
        rules().deleteMany(Filters.eq("groupId", id));
        members().deleteMany(Filters.eq("groupId", id));
    }

    @Override
    public Optional<PermissionGroup> findGroupById(String id) {
        Document doc = groups().find(Filters.eq("_id", id)).first();
        return Optional.ofNullable(doc).map(MongoGroupPermissionStore::toGroup);
    }

    @Override
    public Optional<PermissionGroup> findGroupByName(String name) {
        Document doc = groups().find(Filters.eq("name", name)).first();
        return Optional.ofNullable(doc).map(MongoGroupPermissionStore::toGroup);
    }

    @Override
    public List<PermissionGroup> findAllGroups() {
        List<PermissionGroup> result = new ArrayList<>();
        groups().find().sort(Sorts.ascending("name")).forEach(doc -> result.add(toGroup(doc)));
        return result;
    }

    // ---- members ----

    @Override
    public void addMember(String groupId, String username) {
        members().insertOne(new Document("groupId", groupId).append("username", username));
    }

    @Override
    public void removeMember(String groupId, String username) {
        members().deleteOne(Filters.and(Filters.eq("groupId", groupId), Filters.eq("username", username)));
    }

    @Override
    public List<String> findMembers(String groupId) {
        List<String> result = new ArrayList<>();
        members()
                .find(Filters.eq("groupId", groupId))
                .sort(Sorts.ascending("username"))
                .forEach(doc -> result.add(doc.getString("username")));
        return result;
    }

    @Override
    public List<String> findGroupIdsForUser(String username) {
        List<String> result = new ArrayList<>();
        members().find(Filters.eq("username", username)).forEach(doc -> result.add(doc.getString("groupId")));
        return result;
    }

    // ---- rules ----

    @Override
    public void saveRule(GroupPermissionRule r) {
        rules().insertOne(new Document("_id", r.getId())
                .append("groupId", r.getGroupId())
                .append("provider", r.getProvider())
                .append("target", r.getTarget().name())
                .append("value", r.getValue())
                .append("matchType", r.getMatchType().name())
                .append("operation", r.getGrant().name()));
    }

    @Override
    public void deleteRule(String ruleId) {
        rules().deleteOne(Filters.eq("_id", ruleId));
    }

    @Override
    public Optional<GroupPermissionRule> findRuleById(String id) {
        Document doc = rules().find(Filters.eq("_id", id)).first();
        return Optional.ofNullable(doc).map(MongoGroupPermissionStore::toRule);
    }

    @Override
    public List<GroupPermissionRule> findRulesForGroup(String groupId) {
        List<GroupPermissionRule> result = new ArrayList<>();
        rules().find(Filters.eq("groupId", groupId))
                .sort(Sorts.ascending("provider", "value"))
                .forEach(doc -> result.add(toRule(doc)));
        return result;
    }

    @Override
    public List<GroupPermissionRule> findRulesByProvider(String provider) {
        List<GroupPermissionRule> result = new ArrayList<>();
        rules().find(Filters.eq("provider", provider))
                .sort(Sorts.ascending("groupId", "value"))
                .forEach(doc -> result.add(toRule(doc)));
        return result;
    }

    private MongoCollection<Document> groups() {
        return database.getCollection(GROUPS_COLLECTION);
    }

    private MongoCollection<Document> rules() {
        return database.getCollection(RULES_COLLECTION);
    }

    private MongoCollection<Document> members() {
        return database.getCollection(MEMBERS_COLLECTION);
    }

    private static PermissionGroup toGroup(Document doc) {
        return PermissionGroup.builder()
                .id(doc.getString("_id"))
                .name(doc.getString("name"))
                .description(doc.getString("description"))
                .source(PermissionGroup.Source.valueOf(doc.getString("source")))
                .build();
    }

    private static GroupPermissionRule toRule(Document doc) {
        return GroupPermissionRule.builder()
                .id(doc.getString("_id"))
                .groupId(doc.getString("groupId"))
                .provider(doc.getString("provider"))
                .target(MatchTarget.valueOf(doc.getString("target")))
                .value(doc.getString("value"))
                .matchType(MatchType.valueOf(doc.getString("matchType")))
                .grant(RepoPermission.Grant.valueOf(doc.getString("operation")))
                .build();
    }
}
