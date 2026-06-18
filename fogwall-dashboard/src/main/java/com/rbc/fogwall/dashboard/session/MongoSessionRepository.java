package com.rbc.fogwall.dashboard.session;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.bson.types.Binary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.session.MapSession;
import org.springframework.session.SessionRepository;

/**
 * Spring Session {@link SessionRepository} backed directly by the MongoDB sync driver — no spring-data-mongodb
 * dependency. Sessions are stored as documents in the {@code proxy_sessions} collection with attributes serialized as
 * JDK-serialized {@link HashMap} in a BSON {@link Binary} field. A TTL index on {@code expireAt} lets Mongo handle
 * idle-session cleanup server-side.
 *
 * <p>Document shape:
 *
 * <pre>{@code
 * {
 *   "_id":                "<session-id>",
 *   "createdAt":          <Date>,
 *   "lastAccessedAt":     <Date>,
 *   "maxInactiveSeconds": <long>,
 *   "expireAt":           <Date>,   // TTL anchor — Mongo deletes when now() > expireAt
 *   "attributes":         <Binary> // JDK-serialized HashMap<String,Object>
 * }
 * }</pre>
 *
 * <p>JDK serialization is used because Spring Security's {@code SecurityContextImpl} and all standard
 * {@code Authentication} tokens are {@link java.io.Serializable}. If a future attribute is not serializable,
 * {@link #save(MapSession)} will fail fast with an {@link UncheckedIOException}.
 */
public class MongoSessionRepository implements SessionRepository<MapSession> {

    private static final Logger log = LoggerFactory.getLogger(MongoSessionRepository.class);
    private static final String COLLECTION_NAME = "proxy_sessions";

    private final MongoCollection<Document> collection;
    private final Duration defaultMaxInactiveInterval;

    public MongoSessionRepository(MongoClient mongoClient, String databaseName, Duration defaultMaxInactiveInterval) {
        MongoDatabase database = mongoClient.getDatabase(databaseName);
        this.collection = database.getCollection(COLLECTION_NAME);
        this.defaultMaxInactiveInterval = defaultMaxInactiveInterval;
        collection.createIndex(Indexes.ascending("expireAt"), new IndexOptions().expireAfter(0L, TimeUnit.SECONDS));
        log.info(
                "Mongo session store initialized: db={}, collection={}, default-timeout={}s",
                databaseName,
                COLLECTION_NAME,
                defaultMaxInactiveInterval.getSeconds());
    }

    @Override
    public MapSession createSession() {
        MapSession session = new MapSession();
        session.setMaxInactiveInterval(defaultMaxInactiveInterval);
        return session;
    }

    @Override
    public void save(MapSession session) {
        Map<String, Object> attrs = new HashMap<>();
        for (String name : session.getAttributeNames()) {
            attrs.put(name, session.getAttribute(name));
        }
        Date expireAt = Date.from(session.getLastAccessedTime().plus(session.getMaxInactiveInterval()));
        Document doc = new Document()
                .append("_id", session.getId())
                .append("createdAt", Date.from(session.getCreationTime()))
                .append("lastAccessedAt", Date.from(session.getLastAccessedTime()))
                .append("maxInactiveSeconds", session.getMaxInactiveInterval().getSeconds())
                .append("expireAt", expireAt)
                .append("attributes", new Binary(serialize(attrs)));
        collection.replaceOne(Filters.eq("_id", session.getId()), doc, new ReplaceOptions().upsert(true));
    }

    @Override
    public MapSession findById(String id) {
        Document doc = collection.find(Filters.eq("_id", id)).first();
        if (doc == null) {
            return null;
        }
        MapSession session = new MapSession(id);
        session.setCreationTime(doc.getDate("createdAt").toInstant());
        session.setLastAccessedTime(doc.getDate("lastAccessedAt").toInstant());
        session.setMaxInactiveInterval(Duration.ofSeconds(doc.getLong("maxInactiveSeconds")));
        Binary attrBlob = doc.get("attributes", Binary.class);
        if (attrBlob != null) {
            Map<String, Object> attrs = deserialize(attrBlob.getData());
            attrs.forEach(session::setAttribute);
        }
        if (session.isExpired()) {
            deleteById(id);
            return null;
        }
        return session;
    }

    @Override
    public void deleteById(String id) {
        collection.deleteOne(Filters.eq("_id", id));
    }

    private static byte[] serialize(Map<String, Object> attrs) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(new HashMap<>(attrs));
            oos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize session attributes", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deserialize(byte[] data) {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
            return (Map<String, Object>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new UncheckedIOException(
                    "Failed to deserialize session attributes", e instanceof IOException io ? io : new IOException(e));
        }
    }
}
