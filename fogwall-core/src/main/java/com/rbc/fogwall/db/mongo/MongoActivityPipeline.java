package com.rbc.fogwall.db.mongo;

import com.mongodb.client.model.Filters;
import com.rbc.fogwall.db.model.ActivityDimension;
import com.rbc.fogwall.db.model.ActivityKey;
import com.rbc.fogwall.db.model.ActivityQuery;
import com.rbc.fogwall.db.model.ScmApiActivity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.bson.conversions.Bson;

/**
 * Builds the {@code $match} and {@code $group} key behind an {@link ActivityQuery}, the same way for every collection
 * that answers one. The Mongo counterpart of the JDBC stores' shared builder: the period is a {@code $switch} over the
 * period boundaries, so both store families put a record in the same period.
 *
 * @param fields the collection's names for the provider, owner, repository name and user fields
 */
record MongoActivityPipeline(ActivityQuery query, Fields fields) {

    /** A collection's names for the fields an activity query reads; the timestamp is {@code timestamp} in each. */
    record Fields(String provider, String owner, String repo, String user) {}

    /** The window and filters. */
    Bson match() {
        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.gte("timestamp", Date.from(query.from())));
        filters.add(Filters.lt("timestamp", Date.from(query.to())));
        if (query.provider() != null) filters.add(Filters.eq(fields.provider(), query.provider()));
        if (query.owner() != null) filters.add(Filters.eq(fields.owner(), query.owner()));
        if (query.repo() != null) filters.add(Filters.eq(fields.repo(), query.repo()));
        if (query.user() != null) filters.add(Filters.eq(fields.user(), query.user()));
        return Filters.and(filters);
    }

    /** The {@code $group} key: the grouped dimensions, then each of {@code extra} as a name and expression pair. */
    Document groupKey(Object... extra) {
        Document id = new Document();
        if (query.groupsProvider()) id.append("provider", "$" + fields.provider());
        if (query.groups(ActivityDimension.REPO)) {
            id.append("owner", "$" + fields.owner());
            id.append("repo", "$" + fields.repo());
        }
        if (query.groups(ActivityDimension.USER)) id.append("user", "$" + fields.user());
        if (query.groups(ActivityDimension.PERIOD)) id.append("bucket", bucket());
        for (int i = 0; i < extra.length; i += 2) {
            id.append((String) extra[i], extra[i + 1]);
        }
        return id;
    }

    /** The key of a {@link #groupKey} {@code _id}. */
    ActivityKey key(Document id) {
        boolean repo = query.groups(ActivityDimension.REPO);
        return new ActivityKey(
                query.groupsProvider() ? id.getString("provider") : null,
                repo ? id.getString("owner") : null,
                repo ? id.getString("repo") : null,
                query.groups(ActivityDimension.USER) ? id.getString("user") : null,
                query.groups(ActivityDimension.PERIOD) ? id.getInteger("bucket") : null);
    }

    private Object bucket() {
        List<Instant> boundaries = query.periodBoundaries();
        if (boundaries.isEmpty()) {
            return 0;
        }
        List<Document> branches = new ArrayList<>();
        for (int i = 0; i < boundaries.size(); i++) {
            branches.add(new Document("case", new Document("$lt", List.of("$timestamp", Date.from(boundaries.get(i)))))
                    .append("then", i));
        }
        return new Document("$switch", new Document("branches", branches).append("default", boundaries.size()));
    }

    /** The {@link ScmApiActivity} name of the mutation field {@code field}. */
    static Document activity(String field) {
        List<Document> branches = new ArrayList<>();
        for (ScmApiActivity activity : ScmApiActivity.classified()) {
            branches.add(new Document(
                            "case", new Document("$in", List.of("$" + field, List.copyOf(activity.mutationFields()))))
                    .append("then", activity.name()));
        }
        return new Document(
                "$switch", new Document("branches", branches).append("default", ScmApiActivity.OTHER.name()));
    }
}
