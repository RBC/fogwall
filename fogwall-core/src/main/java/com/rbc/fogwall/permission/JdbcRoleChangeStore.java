package com.rbc.fogwall.permission;

import com.rbc.fogwall.db.jdbc.JdbcKeysetPaging;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * JDBC-backed {@link RoleChangeStore}. Works with H2, PostgreSQL, MySQL, and MariaDB. Role lists are stored
 * comma-separated, the same form {@code proxy_users.roles} uses.
 */
public class JdbcRoleChangeStore implements RoleChangeStore {

    private static final String INSERT = """
            INSERT INTO role_change_events (id, occurred_at, origin, actor, username, previous_roles, roles)
            VALUES (:id, :occurredAt, :origin, :actor, :username, :previousRoles, :roles)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcRoleChangeStore(DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    @Override
    public void record(RoleChangeEvent e) {
        jdbc.update(
                INSERT,
                new MapSqlParameterSource()
                        .addValue("id", e.id())
                        .addValue("occurredAt", Timestamp.from(e.occurredAt()))
                        .addValue("origin", e.origin().name())
                        .addValue("actor", e.actor())
                        .addValue("username", e.username())
                        .addValue("previousRoles", String.join(",", e.previousRoles()))
                        .addValue("roles", String.join(",", e.roles())));
    }

    @Override
    public List<RoleChangeEvent> find(RoleChangeQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = "SELECT * FROM role_change_events" + buildWhere(query, params)
                + JdbcKeysetPaging.orderAndPage(
                        params, "occurred_at", query.isNewestFirst(), query.getLimit(), query.getOffset());
        return jdbc.query(sql, params, ROW_MAPPER);
    }

    private static String buildWhere(RoleChangeQuery query, MapSqlParameterSource params) {
        StringBuilder sql = new StringBuilder(" WHERE 1=1");

        if (query.getOrigin() != null) {
            sql.append(" AND origin = :origin");
            params.addValue("origin", query.getOrigin().name());
        }
        if (query.getUsername() != null) {
            sql.append(" AND username = :username");
            params.addValue("username", query.getUsername());
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
            sql.append(" AND LOWER(username) LIKE :search");
            params.addValue("search", "%" + query.getSearch().toLowerCase(Locale.ROOT) + "%");
        }
        JdbcKeysetPaging.appendAfter(sql, params, "occurred_at", query.getAfter(), query.isNewestFirst());

        return sql.toString();
    }

    private static List<String> split(String roles) {
        return roles == null || roles.isBlank() ? List.of() : Arrays.asList(roles.split(","));
    }

    private static final RowMapper<RoleChangeEvent> ROW_MAPPER = JdbcRoleChangeStore::mapRow;

    // rowNum is unused but required to match the RowMapper functional interface signature.
    @SuppressWarnings("PMD.UnusedFormalParameter")
    private static RoleChangeEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
        return RoleChangeEvent.builder()
                .id(rs.getString("id"))
                .occurredAt(rs.getTimestamp("occurred_at").toInstant())
                .origin(ChangeOrigin.valueOf(rs.getString("origin")))
                .actor(rs.getString("actor"))
                .username(rs.getString("username"))
                .previousRoles(split(rs.getString("previous_roles")))
                .roles(split(rs.getString("roles")))
                .build();
    }
}
