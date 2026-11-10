package com.rbc.fogwall.db.jdbc;

import com.rbc.fogwall.db.model.ActivityDimension;
import com.rbc.fogwall.db.model.ActivityKey;
import com.rbc.fogwall.db.model.ActivityQuery;
import com.rbc.fogwall.db.model.ScmApiActivity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 * Builds the grouped counts behind an {@link ActivityQuery}, the same way for every table that answers one. Each table
 * is read through a derived table that names its columns {@code provider}, {@code repo_owner}, {@code repo_name},
 * {@code username} and {@code bucket}, so the outer {@code GROUP BY} is one statement on H2, PostgreSQL, MySQL and
 * MariaDB: the period is a {@code CASE} over the period boundaries, bound as parameters, not a dialect's
 * date-truncation function.
 */
final class JdbcActivitySql {

    /** The derived table's alias. */
    static final String DERIVED = "t";

    private final ActivityQuery query;
    private final MapSqlParameterSource params;

    JdbcActivitySql(ActivityQuery query, MapSqlParameterSource params) {
        this.query = query;
        this.params = params;
    }

    /**
     * The inner select's key columns, read from {@code alias}'s columns of the given names: one per column the outer
     * query can group by, whether or not this query does.
     */
    String keyColumns(String alias, String ownerColumn, String userColumn) {
        return String.join(
                ", ",
                alias + ".provider AS provider",
                alias + "." + ownerColumn + " AS repo_owner",
                alias + ".repo_name AS repo_name",
                alias + "." + userColumn + " AS username",
                bucket(alias + ".timestamp") + " AS bucket");
    }

    /** The window and filters, on {@code alias}'s columns. Begins with {@code WHERE}. */
    String where(String alias, String ownerColumn, String userColumn) {
        StringBuilder sql = new StringBuilder(" WHERE ")
                .append(alias)
                .append(".timestamp >= :activityFrom AND ")
                .append(alias)
                .append(".timestamp < :activityTo");
        params.addValue("activityFrom", Timestamp.from(query.from()));
        params.addValue("activityTo", Timestamp.from(query.to()));
        filter(sql, alias + ".provider", "activityProvider", query.provider());
        filter(sql, alias + "." + ownerColumn, "activityOwner", query.owner());
        filter(sql, alias + ".repo_name", "activityRepo", query.repo());
        filter(sql, alias + "." + userColumn, "activityUser", query.user());
        return sql.toString();
    }

    private void filter(StringBuilder sql, String column, String param, String value) {
        if (value != null) {
            sql.append(" AND ").append(column).append(" = :").append(param);
            params.addValue(param, value);
        }
    }

    /** The outer query's grouped key columns, each followed by a comma; empty when the query groups by nothing. */
    String groupedColumns() {
        StringBuilder sql = new StringBuilder();
        for (String column : grouped()) {
            sql.append(DERIVED).append('.').append(column).append(", ");
        }
        return sql.toString();
    }

    /** {@code GROUP BY} the grouped key columns and then {@code extra}. */
    String groupBy(String... extra) {
        List<String> columns = new ArrayList<>();
        grouped().forEach(c -> columns.add(DERIVED + "." + c));
        for (String column : extra) {
            columns.add(DERIVED + "." + column);
        }
        return " GROUP BY " + String.join(", ", columns);
    }

    private List<String> grouped() {
        List<String> columns = new ArrayList<>();
        if (query.groupsProvider()) columns.add("provider");
        if (query.groups(ActivityDimension.REPO)) {
            columns.add("repo_owner");
            columns.add("repo_name");
        }
        if (query.groups(ActivityDimension.USER)) columns.add("username");
        if (query.groups(ActivityDimension.PERIOD)) columns.add("bucket");
        return columns;
    }

    /** The key of the current row of a result set selected with {@link #groupedColumns()}. */
    ActivityKey key(ResultSet rs) throws SQLException {
        boolean repo = query.groups(ActivityDimension.REPO);
        return new ActivityKey(
                query.groupsProvider() ? rs.getString("provider") : null,
                repo ? rs.getString("repo_owner") : null,
                repo ? rs.getString("repo_name") : null,
                query.groups(ActivityDimension.USER) ? rs.getString("username") : null,
                query.groups(ActivityDimension.PERIOD) ? rs.getInt("bucket") : null);
    }

    /** The index of the period {@code column} falls in; a constant when the query is not grouped by period. */
    private String bucket(String column) {
        List<Instant> boundaries = query.groups(ActivityDimension.PERIOD) ? query.periodBoundaries() : List.of();
        if (boundaries.isEmpty()) {
            return "0";
        }
        StringBuilder sql = new StringBuilder("CASE");
        for (int i = 0; i < boundaries.size(); i++) {
            String param = "activityPeriod" + i;
            params.addValue(param, Timestamp.from(boundaries.get(i)));
            sql.append(" WHEN ")
                    .append(column)
                    .append(" < :")
                    .append(param)
                    .append(" THEN ")
                    .append(i);
        }
        return sql.append(" ELSE ").append(boundaries.size()).append(" END").toString();
    }

    /**
     * The {@link ScmApiActivity} name of {@code column}'s mutation field, as a {@code CASE} over each activity's
     * fields, bound as parameters.
     */
    String activity(String column) {
        StringBuilder sql = new StringBuilder("CASE");
        for (ScmApiActivity activity : ScmApiActivity.classified()) {
            String param = "activityFields" + activity.name();
            params.addValue(param, activity.mutationFields());
            sql.append(" WHEN ")
                    .append(column)
                    .append(" IN (:")
                    .append(param)
                    .append(") THEN '")
                    .append(activity.name())
                    .append('\'');
        }
        return sql.append(" ELSE '")
                .append(ScmApiActivity.OTHER.name())
                .append("' END")
                .toString();
    }
}
