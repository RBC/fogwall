package com.rbc.fogwall.db.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import com.rbc.fogwall.service.SshFingerprintCache;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MongoDB-backed cache for SSH public key fingerprints fetched from upstream SCM providers.
 *
 * <p>A TTL index on {@code cached_at} handles automatic expiry at the MongoDB level; reads also filter by age to match
 * the behaviour of {@link com.rbc.fogwall.service.JdbcSshFingerprintCache}.
 */
public class MongoSshFingerprintCache implements SshFingerprintCache {

    private static final Logger log = LoggerFactory.getLogger(MongoSshFingerprintCache.class);
    private static final String COLLECTION_NAME = "ssh_fingerprint_cache";

    private final MongoDatabase database;
    private final Duration maxAge;

    public MongoSshFingerprintCache(MongoClient mongoClient, String databaseName, Duration maxAge) {
        this.database = mongoClient.getDatabase(databaseName);
        this.maxAge = maxAge;
    }

    public void initialize() {
        MongoCollection<Document> col = getCollection();
        col.createIndex(Indexes.ascending("provider", "scm_login"), new IndexOptions().unique(true));
        col.createIndex(
                Indexes.ascending("cached_at"),
                new IndexOptions().expireAfter(maxAge.getSeconds(), java.util.concurrent.TimeUnit.SECONDS));
        log.info("MongoDB SSH fingerprint cache initialized (max age {} days)", maxAge.toDays());
    }

    @Override
    public Set<String> lookup(String provider, String scmLogin) {
        Date cutoff = Date.from(Instant.now().minus(maxAge));
        Document doc = getCollection()
                .find(Filters.and(
                        Filters.eq("provider", provider),
                        Filters.eq("scm_login", scmLogin),
                        Filters.gte("cached_at", cutoff)))
                .first();
        if (doc == null) return Set.of();
        log.debug("SSH fingerprint cache hit: provider={}, login={}", provider, scmLogin);
        List<String> list = doc.getList("fingerprints", String.class);
        return list == null ? Set.of() : Set.copyOf(list);
    }

    @Override
    public void store(String provider, String scmLogin, Set<String> fingerprints) {
        Document doc = new Document("provider", provider)
                .append("scm_login", scmLogin)
                .append("fingerprints", List.copyOf(fingerprints))
                .append("cached_at", Date.from(Instant.now()));
        getCollection()
                .replaceOne(
                        Filters.and(Filters.eq("provider", provider), Filters.eq("scm_login", scmLogin)),
                        doc,
                        new ReplaceOptions().upsert(true));
        log.debug("SSH fingerprints cached: provider={}, login={}", provider, scmLogin);
    }

    @Override
    public void evict(String provider, String scmLogin) {
        long deleted = getCollection()
                .deleteMany(Filters.and(Filters.eq("provider", provider), Filters.eq("scm_login", scmLogin)))
                .getDeletedCount();
        if (deleted > 0) {
            log.debug("SSH fingerprint cache evicted: provider={}, login={}", provider, scmLogin);
        }
    }

    private MongoCollection<Document> getCollection() {
        return database.getCollection(COLLECTION_NAME);
    }
}
