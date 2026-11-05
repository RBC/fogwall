package com.rbc.fogwall.permission;

import com.rbc.fogwall.db.jdbc.JdbcKeysetPaging;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** JDBC-backed {@link MembershipChangeStore}. Works with H2, PostgreSQL, MySQL, and MariaDB. */
public class JdbcMembershipChangeStore implements MembershipChangeStore {

    private static final String INSERT = """
            INSERT INTO membership_change_events
                (id, occurred_at, action, origin, actor, source_revision, group_id, group_name, username)
            VALUES
                (:id, :occurredAt, :action, :origin, :actor, :sourceRevision, :groupId, :groupName, :member)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcMembershipChangeStore(DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    @Override
    public void record(MembershipChangeEvent e) {
        jdbc.update(
                INSERT,
                new MapSqlParameterSource()
                        .addValue("id", e.id())
                        .addValue("occurredAt", Timestamp.from(e.occurredAt()))
                        .addValue("action", e.action().name())
                        .addValue("origin", e.origin().name())
                        .addValue("actor", e.actor())
                        .addValue("sourceRevision", e.sourceRevision())
                        .addValue("groupId", e.groupId())
                        .addValue("groupName", e.groupName())
                        .addValue("member", e.member()));
    }

    @Override
    public List<MembershipChangeEvent> find(MembershipChangeQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = "SELECT * FROM membership_change_events" + buildWhere(query, params)
                + JdbcKeysetPaging.orderAndPage(
                        params, "occurred_at", query.isNewestFirst(), query.getLimit(), query.getOffset());
        return jdbc.query(sql, params, ROW_MAPPER);
    }

    private static String buildWhere(MembershipChangeQuery query, MapSqlParameterSource params) {
        StringBuilder sql = new StringBuilder(" WHERE 1=1");

        if (query.getAction() != null) {
            sql.append(" AND action = :action");
            params.addValue("action", query.getAction().name());
        }
        if (query.getOrigin() != null) {
            sql.append(" AND origin = :origin");
            params.addValue("origin", query.getOrigin().name());
        }
        if (query.getGroupName() != null) {
            sql.append(" AND group_name = :groupName");
            params.addValue("groupName", query.getGroupName());
        }
        if (query.getMember() != null) {
            sql.append(" AND username = :member");
            params.addValue("member", query.getMember());
        }
        if (query.getActor() != null) {
            sql.append(" AND actor = :actor");
            params.addValue("actor", query.getActor());
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
            sql.append(" AND (LOWER(group_name) LIKE :search OR LOWER(username) LIKE :search)");
            params.addValue("search", "%" + query.getSearch().toLowerCase(Locale.ROOT) + "%");
        }
        JdbcKeysetPaging.appendAfter(sql, params, "occurred_at", query.getAfter(), query.isNewestFirst());

        return sql.toString();
    }

    private static final RowMapper<MembershipChangeEvent> ROW_MAPPER = JdbcMembershipChangeStore::mapRow;

    // rowNum is unused but required to match the RowMapper functional interface signature.
    @SuppressWarnings("PMD.UnusedFormalParameter")
    private static MembershipChangeEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
        return MembershipChangeEvent.builder()
                .id(rs.getString("id"))
                .occurredAt(rs.getTimestamp("occurred_at").toInstant())
                .action(MembershipChangeEvent.Action.valueOf(rs.getString("action")))
                .origin(ChangeOrigin.valueOf(rs.getString("origin")))
                .actor(rs.getString("actor"))
                .sourceRevision(rs.getString("source_revision"))
                .groupId(rs.getString("group_id"))
                .groupName(rs.getString("group_name"))
                .member(rs.getString("username"))
                .build();
    }
}
