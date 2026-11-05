package com.rbc.fogwall.permission;

import com.rbc.fogwall.db.jdbc.JdbcKeysetPaging;
import com.rbc.fogwall.db.model.MatchTarget;
import com.rbc.fogwall.db.model.MatchType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** JDBC-backed {@link PermissionChangeStore}. Works with H2, PostgreSQL, MySQL, and MariaDB. */
public class JdbcPermissionChangeStore implements PermissionChangeStore {

    private static final String INSERT = """
            INSERT INTO permission_change_events
                (id, occurred_at, action, origin, actor, source_revision, subject_type, subject, permission_id,
                 provider, target, match_value, match_type, operation)
            VALUES
                (:id, :occurredAt, :action, :origin, :actor, :sourceRevision, :subjectType, :subject, :permissionId,
                 :provider, :target, :matchValue, :matchType, :operation)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcPermissionChangeStore(DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    @Override
    public void record(PermissionChangeEvent e) {
        jdbc.update(
                INSERT,
                new MapSqlParameterSource()
                        .addValue("id", e.id())
                        .addValue("occurredAt", Timestamp.from(e.occurredAt()))
                        .addValue("action", e.action().name())
                        .addValue("origin", e.origin().name())
                        .addValue("actor", e.actor())
                        .addValue("sourceRevision", e.sourceRevision())
                        .addValue("subjectType", e.subjectType().name())
                        .addValue("subject", e.subject())
                        .addValue("permissionId", e.permissionId())
                        .addValue("provider", e.provider())
                        .addValue("target", e.target().name())
                        .addValue("matchValue", e.value())
                        .addValue("matchType", e.matchType().name())
                        .addValue("operation", e.grant().name()));
    }

    @Override
    public List<PermissionChangeEvent> find(PermissionChangeQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = "SELECT * FROM permission_change_events" + buildWhere(query, params)
                + JdbcKeysetPaging.orderAndPage(
                        params, "occurred_at", query.isNewestFirst(), query.getLimit(), query.getOffset());
        return jdbc.query(sql, params, ROW_MAPPER);
    }

    private static String buildWhere(PermissionChangeQuery query, MapSqlParameterSource params) {
        StringBuilder sql = new StringBuilder(" WHERE 1=1");

        if (query.getAction() != null) {
            sql.append(" AND action = :action");
            params.addValue("action", query.getAction().name());
        }
        if (query.getOrigin() != null) {
            sql.append(" AND origin = :origin");
            params.addValue("origin", query.getOrigin().name());
        }
        if (query.getSubjectType() != null) {
            sql.append(" AND subject_type = :subjectType");
            params.addValue("subjectType", query.getSubjectType().name());
        }
        if (query.getSubject() != null) {
            sql.append(" AND subject = :subject");
            params.addValue("subject", query.getSubject());
        }
        if (query.getActor() != null) {
            sql.append(" AND actor = :actor");
            params.addValue("actor", query.getActor());
        }
        if (query.getProvider() != null) {
            sql.append(" AND provider = :provider");
            params.addValue("provider", query.getProvider());
        }
        if (query.getFrom() != null) {
            sql.append(" AND occurred_at >= :from");
            params.addValue("from", Timestamp.from(query.getFrom()));
        }
        if (query.getTo() != null) {
            sql.append(" AND occurred_at < :to");
            params.addValue("to", Timestamp.from(query.getTo()));
        }
        if (query.getSearch() != null && !query.getSearch().isBlank()) {
            sql.append(" AND (LOWER(subject) LIKE :search OR LOWER(match_value) LIKE :search)");
            params.addValue("search", "%" + query.getSearch().toLowerCase(Locale.ROOT) + "%");
        }
        JdbcKeysetPaging.appendAfter(sql, params, "occurred_at", query.getAfter(), query.isNewestFirst());

        return sql.toString();
    }

    private static final RowMapper<PermissionChangeEvent> ROW_MAPPER = JdbcPermissionChangeStore::mapRow;

    // rowNum is unused but required to match the RowMapper functional interface signature.
    @SuppressWarnings("PMD.UnusedFormalParameter")
    private static PermissionChangeEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
        return PermissionChangeEvent.builder()
                .id(rs.getString("id"))
                .occurredAt(rs.getTimestamp("occurred_at").toInstant())
                .action(PermissionChangeEvent.Action.valueOf(rs.getString("action")))
                .origin(ChangeOrigin.valueOf(rs.getString("origin")))
                .actor(rs.getString("actor"))
                .sourceRevision(rs.getString("source_revision"))
                .subjectType(PermissionChangeEvent.SubjectType.valueOf(rs.getString("subject_type")))
                .subject(rs.getString("subject"))
                .permissionId(rs.getString("permission_id"))
                .provider(rs.getString("provider"))
                .target(MatchTarget.valueOf(rs.getString("target")))
                .value(rs.getString("match_value"))
                .matchType(MatchType.valueOf(rs.getString("match_type")))
                .grant(RepoPermission.Grant.valueOf(rs.getString("operation")))
                .build();
    }
}
