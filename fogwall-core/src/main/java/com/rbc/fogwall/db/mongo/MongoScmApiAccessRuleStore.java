package com.rbc.fogwall.db.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.rbc.fogwall.scmapi.ScmApiAccessRule;
import com.rbc.fogwall.scmapi.ScmApiAccessRuleStore;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;

/** MongoDB-based {@link ScmApiAccessRuleStore} implementation. Mirrors {@link JdbcScmApiActionStore}'s dual. */
public class MongoScmApiAccessRuleStore implements ScmApiAccessRuleStore {

    private static final String COLLECTION_NAME = "scm_api_access_rules";

    private final MongoDatabase database;

    public MongoScmApiAccessRuleStore(MongoClient mongoClient, String databaseName) {
        this.database = mongoClient.getDatabase(databaseName);
    }

    @Override
    public void initialize() {
        // No indexes needed: a handful of rows per deployment, always scanned by provider.
    }

    @Override
    public void save(ScmApiAccessRule rule) {
        Document doc = new Document("_id", rule.getId())
                .append("provider", rule.getProvider())
                .append("operation", rule.getOperation().name())
                .append("access", rule.getAccess().name())
                .append("source", rule.getSource().name());
        getCollection().replaceOne(Filters.eq("_id", rule.getId()), doc, new ReplaceOptions().upsert(true));
    }

    @Override
    public void delete(String id) {
        getCollection().deleteOne(Filters.eq("_id", id));
    }

    @Override
    public List<ScmApiAccessRule> findAll() {
        return getCollection().find().map(MongoScmApiAccessRuleStore::toRule).into(new ArrayList<>());
    }

    @Override
    public List<ScmApiAccessRule> findByProvider(String provider) {
        return getCollection()
                .find(Filters.eq("provider", provider))
                .map(MongoScmApiAccessRuleStore::toRule)
                .into(new ArrayList<>());
    }

    private static ScmApiAccessRule toRule(Document doc) {
        return ScmApiAccessRule.builder()
                .id(doc.getString("_id"))
                .provider(doc.getString("provider"))
                .operation(ScmApiAccessRule.Operation.valueOf(doc.getString("operation")))
                .access(ScmApiAccessRule.Access.valueOf(doc.getString("access")))
                .source(ScmApiAccessRule.Source.valueOf(doc.getString("source")))
                .build();
    }

    private MongoCollection<Document> getCollection() {
        return database.getCollection(COLLECTION_NAME);
    }
}
