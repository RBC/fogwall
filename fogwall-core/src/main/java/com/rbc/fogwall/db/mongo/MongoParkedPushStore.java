package com.rbc.fogwall.db.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.rbc.fogwall.db.PackChunks;
import com.rbc.fogwall.db.ParkedPushStore;
import com.rbc.fogwall.db.model.ParkedPush;
import com.rbc.fogwall.db.model.ParkedRefUpdate;
import com.rbc.fogwall.db.model.PushStatus;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.Binary;
import org.eclipse.jgit.transport.ReceiveCommand;

/**
 * MongoDB-backed {@link ParkedPushStore}, the peer of the JDBC {@code parked_pushes} tables: one document per parked
 * push in {@code parked_pushes} with its ref updates embedded, and the pack in {@code parked_push_chunks}.
 *
 * <p>Without a transaction, the push document is written first and completed with the pack's length and SHA-256 only
 * after every chunk is stored. A push is readable only once complete, and an incomplete one left by a crash is still
 * listed, so {@link #findReclaimable} finds it.
 */
@Slf4j
public class MongoParkedPushStore implements ParkedPushStore {

    public static final String COLLECTION_NAME = "parked_pushes";
    public static final String CHUNKS_COLLECTION_NAME = "parked_push_chunks";

    /** Record states in which a push may still be forwarded; ERROR only within the retention window. */
    private static final Set<String> FORWARDABLE_STATES =
            Set.of(PushStatus.PENDING.name(), PushStatus.APPROVED.name(), PushStatus.ERROR.name());

    private final MongoDatabase database;

    public MongoParkedPushStore(MongoClient client, String databaseName) {
        this.database = client.getDatabase(databaseName);
    }

    /** Creates the index that orders chunks within a pack and keeps each chunk unique. */
    public void initialize() {
        chunks().createIndex(Indexes.ascending("pushId", "seq"), new IndexOptions().unique(true));
        pushes().createIndex(Indexes.ascending("parkedAt"));
        log.info("MongoDB parked push store initialized");
    }

    @Override
    public ParkedPush park(
            String pushId,
            String providerName,
            String forwardUser,
            String upstreamUrl,
            List<ParkedRefUpdate> refs,
            InputStream pack)
            throws IOException {
        Instant parkedAt = Instant.now();
        pushes().insertOne(new Document("_id", pushId)
                .append("providerName", providerName)
                .append("forwardUser", forwardUser)
                .append("upstreamUrl", upstreamUrl)
                .append(
                        "refs",
                        refs.stream().map(MongoParkedPushStore::refToDocument).toList())
                .append("parkedAt", Date.from(parkedAt)));
        try {
            PackChunks.Written written = PackChunks.write(
                    pack,
                    (seq, data) -> chunks().insertOne(new Document("pushId", pushId)
                            .append("seq", seq)
                            .append("data", new Binary(data))));
            pushes().updateOne(
                            Filters.eq("_id", pushId),
                            Updates.combine(
                                    Updates.set("packBytes", written.length()),
                                    Updates.set("packSha256", written.sha256())));
            return new ParkedPush(pushId, providerName, forwardUser, upstreamUrl, refs, written.length(), parkedAt);
        } catch (IOException | RuntimeException e) {
            delete(pushId);
            throw e;
        }
    }

    @Override
    public Optional<ParkedPush> find(String pushId) {
        return Optional.ofNullable(pushes().find(complete(pushId)).first())
                .map(doc -> new ParkedPush(
                        pushId,
                        doc.getString("providerName"),
                        doc.getString("forwardUser"),
                        doc.getString("upstreamUrl"),
                        doc.getList("refs", Document.class).stream()
                                .map(MongoParkedPushStore::refFromDocument)
                                .toList(),
                        doc.getLong("packBytes"),
                        doc.getDate("parkedAt").toInstant()));
    }

    @Override
    public InputStream openPack(String pushId) throws IOException {
        Document doc = Optional.ofNullable(pushes().find(complete(pushId))
                        .projection(Projections.include("packBytes", "packSha256"))
                        .first())
                .orElseThrow(() -> new IOException("No pack is stored for push " + pushId));
        return PackChunks.read(
                doc.getLong("packBytes"),
                doc.getString("packSha256"),
                seq -> Optional.ofNullable(
                                chunks().find(Filters.and(Filters.eq("pushId", pushId), Filters.eq("seq", seq)))
                                        .first())
                        .map(chunk -> chunk.get("data", Binary.class).getData()));
    }

    @Override
    public void delete(String pushId) {
        // Chunks first: a crash in between leaves the push document, which findReclaimable still lists.
        chunks().deleteMany(Filters.eq("pushId", pushId));
        pushes().deleteOne(Filters.eq("_id", pushId));
    }

    @Override
    public List<String> findReclaimable(Instant failedBefore, Instant orphanedBefore, int limit) {
        List<String> ids = new ArrayList<>();
        for (Document doc : pushes().aggregate(List.of(
                Aggregates.sort(Sorts.ascending("parkedAt")),
                Aggregates.lookup(MongoPushStore.COLLECTION_NAME, "_id", "_id", "record"),
                Aggregates.project(Projections.fields(
                        Projections.include("parkedAt"),
                        Projections.computed("record", new Document("$first", "$record"))))))) {
            if (reclaimable(doc, failedBefore, orphanedBefore)) {
                ids.add(doc.getString("_id"));
                if (ids.size() == limit) {
                    break;
                }
            }
        }
        return ids;
    }

    private static boolean reclaimable(Document doc, Instant failedBefore, Instant orphanedBefore) {
        Instant parkedAt = doc.getDate("parkedAt").toInstant();
        Optional<Document> record = Optional.ofNullable(doc.get("record", Document.class));
        if (record.isEmpty()) {
            return parkedAt.isBefore(orphanedBefore);
        }
        String status = record.get().getString("status");
        if (!FORWARDABLE_STATES.contains(status)) {
            return true;
        }
        if (PushStatus.ERROR.name().equals(status)) {
            Instant failedAt = Optional.ofNullable(record.get().getDate("forwardedAt"))
                    .map(Date::toInstant)
                    .orElse(parkedAt);
            return failedAt.isBefore(failedBefore);
        }
        return false;
    }

    /** The push document with this id, only once its pack is completely stored. */
    private static Bson complete(String pushId) {
        return Filters.and(Filters.eq("_id", pushId), Filters.exists("packSha256"));
    }

    private static Document refToDocument(ParkedRefUpdate ref) {
        return new Document("refName", ref.refName())
                .append("oldId", ref.oldId())
                .append("newId", ref.newId())
                .append("type", ref.type().name());
    }

    private static ParkedRefUpdate refFromDocument(Document doc) {
        return new ParkedRefUpdate(
                doc.getString("refName"),
                doc.getString("oldId"),
                doc.getString("newId"),
                ReceiveCommand.Type.valueOf(doc.getString("type")));
    }

    private MongoCollection<Document> pushes() {
        return database.getCollection(COLLECTION_NAME);
    }

    private MongoCollection<Document> chunks() {
        return database.getCollection(CHUNKS_COLLECTION_NAME);
    }
}
