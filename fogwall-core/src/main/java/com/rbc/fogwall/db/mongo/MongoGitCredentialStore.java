package com.rbc.fogwall.db.mongo;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.rbc.fogwall.user.GitCredential;
import com.rbc.fogwall.user.GitCredentialNameConflictException;
import com.rbc.fogwall.user.GitCredentialStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

/** MongoDB-backed {@link GitCredentialStore}, the peer of the JDBC {@code user_git_credentials} table. */
@Slf4j
public class MongoGitCredentialStore implements GitCredentialStore {

    public static final String COLLECTION_NAME = "user_git_credentials";

    private final MongoDatabase database;

    public MongoGitCredentialStore(MongoClient client, String databaseName) {
        this.database = client.getDatabase(databaseName);
    }

    /** Creates the index that keeps credential names unique per user and serves lookups by user. */
    public void initialize() {
        getCollection().createIndex(Indexes.ascending("username", "name"), new IndexOptions().unique(true));
        log.info("MongoDB git credential store initialized");
    }

    @Override
    public void save(GitCredential credential) {
        Document doc = new Document("_id", credential.id())
                .append("username", credential.username())
                .append("name", credential.name())
                .append("secret_hash", credential.secretHash())
                .append("created_at", date(credential.createdAt()))
                .append("expires_at", date(credential.expiresAt()))
                .append("last_used_at", date(credential.lastUsedAt()));
        try {
            getCollection().insertOne(doc);
        } catch (MongoWriteException e) {
            if (e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                throw new GitCredentialNameConflictException(credential.username(), credential.name(), e);
            }
            throw e;
        }
    }

    @Override
    public Optional<GitCredential> findById(String id) {
        return Optional.ofNullable(getCollection().find(Filters.eq("_id", id)).first())
                .map(MongoGitCredentialStore::map);
    }

    @Override
    public List<GitCredential> findByUsername(String username) {
        List<GitCredential> credentials = new ArrayList<>();
        getCollection()
                .find(Filters.eq("username", username))
                .sort(Sorts.ascending("name"))
                .map(MongoGitCredentialStore::map)
                .into(credentials);
        return credentials;
    }

    @Override
    public boolean replaceSecret(String id, String secretHash, Instant createdAt, Instant expiresAt) {
        return getCollection()
                        .updateOne(
                                Filters.eq("_id", id),
                                Updates.combine(
                                        Updates.set("secret_hash", secretHash),
                                        Updates.set("created_at", date(createdAt)),
                                        Updates.set("expires_at", date(expiresAt)),
                                        Updates.set("last_used_at", null)))
                        .getMatchedCount()
                > 0;
    }

    @Override
    public void recordUse(String id, Instant usedAt) {
        getCollection().updateOne(Filters.eq("_id", id), Updates.set("last_used_at", date(usedAt)));
    }

    @Override
    public boolean delete(String id) {
        return getCollection().deleteOne(Filters.eq("_id", id)).getDeletedCount() > 0;
    }

    @Override
    public int deleteByUsername(String username) {
        return (int)
                getCollection().deleteMany(Filters.eq("username", username)).getDeletedCount();
    }

    private static GitCredential map(Document doc) {
        return new GitCredential(
                doc.getString("_id"),
                doc.getString("username"),
                doc.getString("name"),
                doc.getString("secret_hash"),
                instant(doc.getDate("created_at")),
                instant(doc.getDate("expires_at")),
                instant(doc.getDate("last_used_at")));
    }

    private static Date date(Instant instant) {
        return instant != null ? Date.from(instant) : null;
    }

    private static Instant instant(Date date) {
        return date != null ? date.toInstant() : null;
    }

    private MongoCollection<Document> getCollection() {
        return database.getCollection(COLLECTION_NAME);
    }
}
