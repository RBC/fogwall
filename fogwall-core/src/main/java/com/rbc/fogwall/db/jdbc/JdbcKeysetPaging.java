package com.rbc.fogwall.db.jdbc;

import com.rbc.fogwall.db.model.RecordCursor;
import java.sql.Timestamp;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/** SQL for reading the page after a {@link RecordCursor}, on a table ordered by {@code (timeColumn, id)}. */
public final class JdbcKeysetPaging {

    private JdbcKeysetPaging() {}

    /** Appends {@code AND <rows past the cursor in the requested direction>}; nothing when {@code after} is null. */
    public static void appendAfter(
            StringBuilder sql,
            MapSqlParameterSource params,
            String timeColumn,
            RecordCursor after,
            boolean newestFirst) {
        if (after == null) {
            return;
        }
        String past = newestFirst ? "<" : ">";
        sql.append(" AND (")
                .append(timeColumn)
                .append(' ')
                .append(past)
                .append(" :afterAt OR (")
                .append(timeColumn)
                .append(" = :afterAt AND id ")
                .append(past)
                .append(" :afterId))");
        params.addValue("afterAt", Timestamp.from(after.at()));
        params.addValue("afterId", after.id());
    }

    /** {@code ORDER BY <timeColumn>, id} in the requested direction, with limit and offset. */
    public static String orderAndPage(
            MapSqlParameterSource params, String timeColumn, boolean newestFirst, int limit, int offset) {
        String direction = newestFirst ? "DESC" : "ASC";
        params.addValue("limit", limit);
        params.addValue("offset", offset);
        return " ORDER BY " + timeColumn + " " + direction + ", id " + direction + " LIMIT :limit OFFSET :offset";
    }
}
