package com.rbc.fogwall.db.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Updates;
import com.rbc.fogwall.db.ScmApiActionStore;
import com.rbc.fogwall.db.model.ActivityQuery;
import com.rbc.fogwall.db.model.ProposalOutcomeCount;
import com.rbc.fogwall.db.model.ScmApiActionOrigin;
import com.rbc.fogwall.db.model.ScmApiActionQuery;
import com.rbc.fogwall.db.model.ScmApiActionRecord;
import com.rbc.fogwall.db.model.ScmApiActionStatus;
import com.rbc.fogwall.db.model.ScmApiActivity;
import com.rbc.fogwall.db.model.ScmApiActivityCount;
import com.rbc.fogwall.db.model.ScmApiEntityRecord;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.bson.Document;
import org.bson.conversions.Bson;

/** MongoDB-based {@link ScmApiActionStore} implementation. */
public class MongoScmApiActionStore implements ScmApiActionStore {

    private static final String COLLECTION_NAME = "scm_api_action_records";

    private final MongoDatabase database;

    public MongoScmApiActionStore(MongoClient mongoClient, String databaseName) {
        this.database = mongoClient.getDatabase(databaseName);
    }

    @Override
    public void initialize() {
        MongoCollection<Document> col = getCollection();
        col.createIndex(Indexes.ascending("resolved_user"));
        col.createIndex(Indexes.descending("timestamp"));
        // Serves the Activity view's "my actions on this surface" filter.
        col.createIndex(Indexes.ascending("origin", "resolved_user"));
        backfillOrigin(col);
        foldDeniedIntoRejected(col);
    }

    /**
     * Records written while a refusal by policy had its own {@code DENIED} status read as
     * {@link ScmApiActionStatus#REJECTED}, as the JDBC migration rewrites them; their reason still says why.
     * Idempotent.
     */
    private static void foldDeniedIntoRejected(MongoCollection<Document> col) {
        col.updateMany(Filters.eq("status", "DENIED"), Updates.set("status", ScmApiActionStatus.REJECTED.name()));
    }

    private static final MongoActivityPipeline.Fields ACTIVITY_FIELDS =
            new MongoActivityPipeline.Fields("provider", "repo_owner", "repo_name", "resolved_user");

    /** The entity registry's collection, joined to read an opened entity's state. */
    private static final String ENTITY_COLLECTION_NAME = "scm_api_entities";

    @Override
    public List<ScmApiActivityCount> summarizeActivity(ActivityQuery query) {
        MongoActivityPipeline pipeline = new MongoActivityPipeline(query, ACTIVITY_FIELDS);
        Document target = new Document("user", "$resolved_user")
                .append(
                        "target",
                        new Document(
                                "$ifNull",
                                List.of("$entity_id", new Document("$ifNull", List.of("$node_id", "$_id")))));
        List<ScmApiActivityCount> counts = new ArrayList<>();
        getCollection()
                .aggregate(List.of(
                        Aggregates.match(pipeline.match()),
                        Aggregates.group(
                                pipeline.groupKey(
                                        "status",
                                        "$status",
                                        "activity",
                                        MongoActivityPipeline.activity("mutation_field")),
                                Accumulators.sum("actions", 1),
                                Accumulators.addToSet("targets", target)),
                        Aggregates.project(
                                new Document("actions", 1).append("targets", new Document("$size", "$targets")))))
                .allowDiskUse(true)
                .forEach(doc -> {
                    Document id = doc.get("_id", Document.class);
                    counts.add(new ScmApiActivityCount(
                            pipeline.key(id),
                            ScmApiActionStatus.valueOf(id.getString("status")),
                            ScmApiActivity.valueOf(id.getString("activity")),
                            ((Number) doc.get("actions")).longValue(),
                            ((Number) doc.get("targets")).longValue()));
                });
        return counts;
    }

    @Override
    public List<ProposalOutcomeCount> summarizeOutcomes(ActivityQuery query) {
        MongoActivityPipeline pipeline = new MongoActivityPipeline(query, ACTIVITY_FIELDS);
        List<String> openFields = ScmApiActivity.classified().stream()
                .filter(ScmApiActivity::opens)
                .flatMap(a -> a.mutationFields().stream())
                .toList();
        List<ProposalOutcomeCount> counts = new ArrayList<>();
        getCollection()
                .aggregate(List.of(
                        Aggregates.match(Filters.and(
                                pipeline.match(),
                                Filters.eq("status", ScmApiActionStatus.FORWARDED.name()),
                                Filters.in("mutation_field", openFields))),
                        Aggregates.lookup(ENTITY_COLLECTION_NAME, "entity_id", "_id", "entity"),
                        Aggregates.group(
                                pipeline.groupKey(
                                        "activity",
                                        MongoActivityPipeline.activity("mutation_field"),
                                        "state",
                                        new Document("$arrayElemAt", List.of("$entity.state", 0))),
                                Accumulators.sum("total", 1))))
                .allowDiskUse(true)
                .forEach(doc -> {
                    Document id = doc.get("_id", Document.class);
                    String state = id.getString("state");
                    counts.add(new ProposalOutcomeCount(
                            pipeline.key(id),
                            ScmApiActivity.valueOf(id.getString("activity")),
                            state == null ? null : ScmApiEntityRecord.State.valueOf(state),
                            ((Number) doc.get("total")).longValue()));
                });
        return counts;
    }

    /**
     * Gives records written before origin existed the same treatment the JDBC migration gives them, since there is no
     * migrator on this side to carry it: the dashboard stamped the literal {@code dashboard} into {@code client_type},
     * and every other value there is a {@code User-Agent} classification, which only the proxy path produces. Both
     * updates are idempotent and match nothing once they have run.
     */
    private static void backfillOrigin(MongoCollection<Document> col) {
        Bson unset = Filters.eq("origin", null);
        col.updateMany(
                Filters.and(unset, Filters.regex("client_type", "^dashboard$", "i")),
                Updates.set("origin", ScmApiActionOrigin.DASHBOARD.name()));
        col.updateMany(unset, Updates.set("origin", ScmApiActionOrigin.SCM_API.name()));
    }

    @Override
    public void save(ScmApiActionRecord r) {
        Document doc = new Document("_id", r.getId())
                .append("timestamp", Date.from(r.getTimestamp()))
                .append("provider", r.getProvider())
                .append("scm_username", r.getScmUsername())
                .append("resolved_user", r.getResolvedUser())
                .append("repo_owner", r.getRepoOwner())
                .append("repo_name", r.getRepoName())
                .append("mutation_field", r.getMutationField())
                .append("node_id", r.getNodeId())
                .append("node_type", r.getNodeType())
                .append("status", r.getStatus().name())
                .append("origin", r.getOrigin() == null ? null : r.getOrigin().name())
                .append("reason", r.getReason())
                .append("variables_json", r.getVariablesJson())
                .append("user_agent", r.getUserAgent())
                .append("client_type", r.getClientType())
                .append("client_version", r.getClientVersion())
                .append("upstream_status", r.getUpstreamStatus())
                .append("entity_id", r.getEntityId())
                .append("merge_commit_sha", r.getMergeCommitSha());
        getCollection().insertOne(doc);
    }

    @Override
    public Optional<ScmApiActionRecord> findById(String id) {
        Document doc = getCollection().find(Filters.eq("_id", id)).first();
        return Optional.ofNullable(doc).map(MongoScmApiActionStore::toRecord);
    }

    @Override
    public List<ScmApiActionRecord> find(ScmApiActionQuery query) {
        List<Bson> filters = new ArrayList<>();

        if (query.getStatus() != null) {
            filters.add(Filters.eq("status", query.getStatus().name()));
        }
        if (query.getOrigin() != null) {
            filters.add(Filters.eq("origin", query.getOrigin().name()));
        }
        if (query.getProvider() != null) {
            filters.add(Filters.eq("provider", query.getProvider()));
        }
        if (query.getUser() != null) {
            filters.add(Filters.eq("resolved_user", query.getUser()));
        }
        if (query.getRepoOwner() != null) {
            filters.add(Filters.eq("repo_owner", query.getRepoOwner()));
        }
        if (query.getRepoName() != null) {
            filters.add(Filters.eq("repo_name", query.getRepoName()));
        }
        if (query.getSearch() != null && !query.getSearch().isBlank()) {
            String pattern = "(?i).*" + Pattern.quote(query.getSearch()) + ".*";
            filters.add(Filters.or(Filters.regex("repo_owner", pattern), Filters.regex("repo_name", pattern)));
        }
        if (query.getFrom() != null) {
            filters.add(Filters.gte("timestamp", Date.from(query.getFrom())));
        }
        if (query.getTo() != null) {
            filters.add(Filters.lt("timestamp", Date.from(query.getTo())));
        }
        if (query.getAfter() != null) {
            filters.add(MongoKeysetPaging.after("timestamp", query.getAfter(), query.isNewestFirst()));
        }

        Bson filter = filters.isEmpty() ? new Document() : Filters.and(filters);

        List<ScmApiActionRecord> results = new ArrayList<>();
        getCollection()
                .find(filter)
                .sort(MongoKeysetPaging.sort("timestamp", query.isNewestFirst()))
                .skip(query.getOffset())
                .limit(query.getLimit())
                .forEach(doc -> results.add(toRecord(doc)));
        return results;
    }

    private static ScmApiActionRecord toRecord(Document doc) {
        return ScmApiActionRecord.builder()
                .id(doc.getString("_id"))
                .timestamp(doc.getDate("timestamp").toInstant())
                .provider(doc.getString("provider"))
                .scmUsername(doc.getString("scm_username"))
                .resolvedUser(doc.getString("resolved_user"))
                .repoOwner(doc.getString("repo_owner"))
                .repoName(doc.getString("repo_name"))
                .mutationField(doc.getString("mutation_field"))
                .nodeId(doc.getString("node_id"))
                .nodeType(doc.getString("node_type"))
                .status(ScmApiActionStatus.valueOf(doc.getString("status")))
                .origin(doc.getString("origin") == null ? null : ScmApiActionOrigin.valueOf(doc.getString("origin")))
                .reason(doc.getString("reason"))
                .variablesJson(doc.getString("variables_json"))
                .userAgent(doc.getString("user_agent"))
                .clientType(doc.getString("client_type"))
                .clientVersion(doc.getString("client_version"))
                .upstreamStatus(doc.getInteger("upstream_status"))
                .entityId(doc.getString("entity_id"))
                .mergeCommitSha(doc.getString("merge_commit_sha"))
                .build();
    }

    private MongoCollection<Document> getCollection() {
        return database.getCollection(COLLECTION_NAME);
    }
}
