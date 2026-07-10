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

public class JdbcGroupPermissionStore implements GroupPermissionStore {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcGroupPermissionStore(DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    // ---- groups ----

    @Override
    public void saveGroup(PermissionGroup g) {
        jdbc.update(
                """
                INSERT INTO permission_groups (id, name, description, source)
                VALUES (:id, :name, :description, :source)
                """,
                new MapSqlParameterSource()
                        .addValue("id", g.getId())
                        .addValue("name", g.getName())
                        .addValue("description", g.getDescription())
                        .addValue("source", g.getSource().name()));
    }

    @Override
    public void updateGroup(String id, String name, String description) {
        jdbc.update(
                "UPDATE permission_groups SET name = :name, description = :description WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("name", name)
                        .addValue("description", description));
    }

    @Override
    public void deleteGroup(String id) {
        jdbc.update("DELETE FROM permission_groups WHERE id = :id", Map.of("id", id));
    }

    @Override
    public Optional<PermissionGroup> findGroupById(String id) {
        var rows = jdbc.query("SELECT * FROM permission_groups WHERE id = :id", Map.of("id", id), GROUP_MAPPER);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public Optional<PermissionGroup> findGroupByName(String name) {
        var rows = jdbc.query("SELECT * FROM permission_groups WHERE name = :name", Map.of("name", name), GROUP_MAPPER);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<PermissionGroup> findAllGroups() {
        return jdbc.query("SELECT * FROM permission_groups ORDER BY name", GROUP_MAPPER);
    }

    // ---- members ----

    @Override
    public void addMember(String groupId, String username) {
        jdbc.update(
                "INSERT INTO group_members (group_id, username) VALUES (:groupId, :username)",
                Map.of("groupId", groupId, "username", username));
    }

    @Override
    public void removeMember(String groupId, String username) {
        jdbc.update(
                "DELETE FROM group_members WHERE group_id = :groupId AND username = :username",
                Map.of("groupId", groupId, "username", username));
    }

    @Override
    public List<String> findMembers(String groupId) {
        return jdbc.queryForList(
                "SELECT username FROM group_members WHERE group_id = :groupId ORDER BY username",
                Map.of("groupId", groupId),
                String.class);
    }

    @Override
    public List<String> findGroupIdsForUser(String username) {
        return jdbc.queryForList(
                "SELECT group_id FROM group_members WHERE username = :username",
                Map.of("username", username),
                String.class);
    }

    // ---- rules ----

    @Override
    public void saveRule(GroupPermissionRule r) {
        jdbc.update(
                """
                INSERT INTO group_permissions (id, group_id, provider, target, match_value, match_type, operation)
                VALUES (:id, :groupId, :provider, :target, :matchValue, :matchType, :operation)
                """,
                new MapSqlParameterSource()
                        .addValue("id", r.getId())
                        .addValue("groupId", r.getGroupId())
                        .addValue("provider", r.getProvider())
                        .addValue("target", r.getTarget().name())
                        .addValue("matchValue", r.getValue())
                        .addValue("matchType", r.getMatchType().name())
                        .addValue("operation", r.getGrant().name()));
    }

    @Override
    public void deleteRule(String ruleId) {
        jdbc.update("DELETE FROM group_permissions WHERE id = :id", Map.of("id", ruleId));
    }

    @Override
    public Optional<GroupPermissionRule> findRuleById(String id) {
        var rows = jdbc.query("SELECT * FROM group_permissions WHERE id = :id", Map.of("id", id), RULE_MAPPER);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<GroupPermissionRule> findRulesForGroup(String groupId) {
        return jdbc.query(
                "SELECT * FROM group_permissions WHERE group_id = :groupId ORDER BY provider, match_value",
                Map.of("groupId", groupId),
                RULE_MAPPER);
    }

    @Override
    public List<GroupPermissionRule> findRulesByProvider(String provider) {
        return jdbc.query(
                "SELECT * FROM group_permissions WHERE provider = :provider ORDER BY group_id, match_value",
                Map.of("provider", provider),
                RULE_MAPPER);
    }

    private static final RowMapper<PermissionGroup> GROUP_MAPPER = JdbcGroupPermissionStore::mapGroup;

    private static PermissionGroup mapGroup(ResultSet rs, int i) throws SQLException {
        return PermissionGroup.builder()
                .id(rs.getString("id"))
                .name(rs.getString("name"))
                .description(rs.getString("description"))
                .source(PermissionGroup.Source.valueOf(rs.getString("source")))
                .build();
    }

    private static final RowMapper<GroupPermissionRule> RULE_MAPPER = JdbcGroupPermissionStore::mapRule;

    private static GroupPermissionRule mapRule(ResultSet rs, int i) throws SQLException {
        return GroupPermissionRule.builder()
                .id(rs.getString("id"))
                .groupId(rs.getString("group_id"))
                .provider(rs.getString("provider"))
                .target(MatchTarget.valueOf(rs.getString("target")))
                .value(rs.getString("match_value"))
                .matchType(MatchType.valueOf(rs.getString("match_type")))
                .grant(RepoPermission.Grant.valueOf(rs.getString("operation")))
                .build();
    }
}
