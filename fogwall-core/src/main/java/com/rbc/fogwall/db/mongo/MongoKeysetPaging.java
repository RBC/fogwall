package com.rbc.fogwall.db.mongo;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.rbc.fogwall.db.model.RecordCursor;
import java.util.Date;
import org.bson.conversions.Bson;

/** Filters for reading the page after a {@link RecordCursor}, on a collection ordered by {@code (timeField, _id)}. */
public final class MongoKeysetPaging {

    private MongoKeysetPaging() {}

    /** Documents past {@code after} in the requested direction. */
    public static Bson after(String timeField, RecordCursor after, boolean newestFirst) {
        Date at = Date.from(after.at());
        return newestFirst
                ? Filters.or(
                        Filters.lt(timeField, at),
                        Filters.and(Filters.eq(timeField, at), Filters.lt("_id", after.id())))
                : Filters.or(
                        Filters.gt(timeField, at),
                        Filters.and(Filters.eq(timeField, at), Filters.gt("_id", after.id())));
    }

    /** Sort by {@code (timeField, _id)} in the requested direction. */
    public static Bson sort(String timeField, boolean newestFirst) {
        return newestFirst ? Sorts.descending(timeField, "_id") : Sorts.ascending(timeField, "_id");
    }
}
