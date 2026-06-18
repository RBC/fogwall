package com.rbc.fogwall.permission;

import com.rbc.fogwall.db.model.MatchTarget;
import com.rbc.fogwall.db.model.MatchType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** JDBC-backed {@link RepoPermissionStore}. Works with H2, PostgreSQL, and SQLite. */
public class JdbcRepoPermissionStore implements RepoPermissionStore {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcRepoPermissionStore(DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    @Override
    public void save(RepoPermission p) {
        // Config-defined users are never seeded into proxy_users at startup, so INSERT a
        // placeholder row on first permission grant to satisfy the FK constraint.
        // We only insert — never update — so existing DB users' roles are not touched.
        ensureUserRow(p.getUsername());
        jdbc.update("""
                INSERT INTO repo_permissions (id, username, provider, target, match_value, match_type, operations, source)
                VALUES (:id, :username, :provider, :target, :matchValue, :matchType, :operations, :source)
                """, params(p));
    }

    private void ensureUserRow(String username) {
        boolean exists = !jdbc.queryForList(
                        "SELECT username FROM proxy_users WHERE username = :u", Map.of("u", username), String.class)
                .isEmpty();
        if (!exists) {
            jdbc.update(
                    "INSERT INTO proxy_users (username, password_hash, roles) VALUES (:u, NULL, 'USER')",
                    Map.of("u", username));
        }
    }

    @Override
    public void delete(String id) {
        jdbc.update("DELETE FROM repo_permissions WHERE id = :id", Map.of("id", id));
    }

    @Override
    public Optional<RepoPermission> findById(String id) {
        var rows = jdbc.query("SELECT * FROM repo_permissions WHERE id = :id", Map.of("id", id), ROW_MAPPER);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<RepoPermission> findAll() {
        return jdbc.query("SELECT * FROM repo_permissions ORDER BY provider, match_value, username", ROW_MAPPER);
    }

    @Override
    public List<RepoPermission> findByUsername(String username) {
        return jdbc.query(
                "SELECT * FROM repo_permissions WHERE username = :username ORDER BY provider, match_value",
                Map.of("username", username),
                ROW_MAPPER);
    }

    @Override
    public List<RepoPermission> findByProvider(String provider) {
        return jdbc.query(
                "SELECT * FROM repo_permissions WHERE provider = :provider ORDER BY match_value, username",
                Map.of("provider", provider),
                ROW_MAPPER);
    }

    private static MapSqlParameterSource params(RepoPermission p) {
        return new MapSqlParameterSource()
                .addValue("id", p.getId())
                .addValue("username", p.getUsername())
                .addValue("provider", p.getProvider())
                .addValue("target", p.getTarget().name())
                .addValue("matchValue", p.getValue())
                .addValue("matchType", p.getMatchType().name())
                .addValue("operations", p.getOperations().name())
                .addValue("source", p.getSource().name());
    }

    private static final RowMapper<RepoPermission> ROW_MAPPER = JdbcRepoPermissionStore::mapRow;

    private static RepoPermission mapRow(ResultSet rs, int i) throws SQLException {
        return RepoPermission.builder()
                .id(rs.getString("id"))
                .username(rs.getString("username"))
                .provider(rs.getString("provider"))
                .target(MatchTarget.valueOf(rs.getString("target")))
                .value(rs.getString("match_value"))
                .matchType(MatchType.valueOf(rs.getString("match_type")))
                .operations(RepoPermission.Operations.valueOf(rs.getString("operations")))
                .source(RepoPermission.Source.valueOf(rs.getString("source")))
                .build();
    }
}
