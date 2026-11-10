package com.rbc.fogwall.db.jdbc;

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
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * JDBC-based {@link ScmApiActionStore} implementation. Works with H2, PostgreSQL, MySQL, and MariaDB.
 *
 * <p>Records are write-once — a mutation's decision and outcome are known synchronously in one pass, so unlike
 * {@link JdbcPushStore} there is no update path to support.
 */
public class JdbcScmApiActionStore implements ScmApiActionStore {

    private final DataSource dataSource;
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcScmApiActionStore(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    @Override
    public void initialize() {
        DatabaseMigrator.migrate(dataSource);
    }

    @Override
    public void save(ScmApiActionRecord record) {
        jdbc.update("""
                INSERT INTO scm_api_action_records (id, timestamp, provider, scm_username, resolved_user, repo_owner,
                    repo_name, mutation_field, node_id, node_type, status, origin, reason, variables_json,
                    user_agent, client_type, client_version, upstream_status, entity_id, merge_commit_sha)
                VALUES (:id, :timestamp, :provider, :scmUsername, :resolvedUser, :repoOwner, :repoName, :mutationField,
                    :nodeId, :nodeType, :status, :origin, :reason, :variablesJson,
                    :userAgent, :clientType, :clientVersion, :upstreamStatus, :entityId, :mergeCommitSha)
                """, toParams(record));
    }

    @Override
    public Optional<ScmApiActionRecord> findById(String id) {
        List<ScmApiActionRecord> rows =
                jdbc.query("SELECT * FROM scm_api_action_records WHERE id = :id", Map.of("id", id), ROW_MAPPER);
        return rows.stream().findFirst();
    }

    @Override
    public List<ScmApiActionRecord> find(ScmApiActionQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = buildWhere(query, params);
        String sql = "SELECT * FROM scm_api_action_records" + where
                + JdbcKeysetPaging.orderAndPage(
                        params, "timestamp", query.isNewestFirst(), query.getLimit(), query.getOffset());
        return jdbc.query(sql, params, ROW_MAPPER);
    }

    @Override
    public List<ScmApiActivityCount> summarizeActivity(ActivityQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        JdbcActivitySql sql = new JdbcActivitySql(query, params);
        String inner = "SELECT " + sql.keyColumns("a", "repo_owner", "resolved_user") + ", a.status AS status, "
                + sql.activity("a.mutation_field") + " AS activity,"
                + " CONCAT(COALESCE(a.resolved_user, ''), '|', COALESCE(a.entity_id, a.node_id, a.id)) AS target"
                + " FROM scm_api_action_records a" + sql.where("a", "repo_owner", "resolved_user");
        String t = JdbcActivitySql.DERIVED;
        return jdbc.query(
                "SELECT " + sql.groupedColumns() + t + ".status, " + t + ".activity, COUNT(*) AS actions,"
                        + " COUNT(DISTINCT " + t + ".target) AS targets FROM (" + inner + ") " + t
                        + sql.groupBy("status", "activity"),
                params,
                (rs, rowNum) -> new ScmApiActivityCount(
                        sql.key(rs),
                        ScmApiActionStatus.valueOf(rs.getString("status")),
                        ScmApiActivity.valueOf(rs.getString("activity")),
                        rs.getLong("actions"),
                        rs.getLong("targets")));
    }

    @Override
    public List<ProposalOutcomeCount> summarizeOutcomes(ActivityQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        JdbcActivitySql sql = new JdbcActivitySql(query, params);
        params.addValue("forwarded", ScmApiActionStatus.FORWARDED.name());
        params.addValue(
                "openFields",
                ScmApiActivity.classified().stream()
                        .filter(ScmApiActivity::opens)
                        .flatMap(a -> a.mutationFields().stream())
                        .toList());
        String inner = "SELECT " + sql.keyColumns("a", "repo_owner", "resolved_user") + ", "
                + sql.activity("a.mutation_field") + " AS activity, e.state AS state"
                + " FROM scm_api_action_records a LEFT JOIN scm_api_entities e ON e.id = a.entity_id"
                + sql.where("a", "repo_owner", "resolved_user")
                + " AND a.status = :forwarded AND a.mutation_field IN (:openFields)";
        String t = JdbcActivitySql.DERIVED;
        return jdbc.query(
                "SELECT " + sql.groupedColumns() + t + ".activity, " + t + ".state, COUNT(*) AS total FROM (" + inner
                        + ") " + t + sql.groupBy("activity", "state"),
                params,
                (rs, rowNum) -> new ProposalOutcomeCount(
                        sql.key(rs),
                        ScmApiActivity.valueOf(rs.getString("activity")),
                        rs.getString("state") == null ? null : ScmApiEntityRecord.State.valueOf(rs.getString("state")),
                        rs.getLong("total")));
    }

    private static String buildWhere(ScmApiActionQuery query, MapSqlParameterSource params) {
        StringBuilder sql = new StringBuilder(" WHERE 1=1");

        if (query.getStatus() != null) {
            sql.append(" AND status = :status");
            params.addValue("status", query.getStatus().name());
        }
        if (query.getOrigin() != null) {
            sql.append(" AND origin = :origin");
            params.addValue("origin", query.getOrigin().name());
        }
        if (query.getProvider() != null) {
            sql.append(" AND provider = :provider");
            params.addValue("provider", query.getProvider());
        }
        if (query.getUser() != null) {
            sql.append(" AND resolved_user = :user");
            params.addValue("user", query.getUser());
        }
        if (query.getRepoOwner() != null) {
            sql.append(" AND repo_owner = :repoOwner");
            params.addValue("repoOwner", query.getRepoOwner());
        }
        if (query.getRepoName() != null) {
            sql.append(" AND repo_name = :repoName");
            params.addValue("repoName", query.getRepoName());
        }
        if (query.getSearch() != null && !query.getSearch().isBlank()) {
            sql.append(" AND (LOWER(repo_owner) LIKE :search OR LOWER(repo_name) LIKE :search)");
            params.addValue("search", "%" + query.getSearch().toLowerCase() + "%");
        }
        if (query.getFrom() != null) {
            sql.append(" AND timestamp >= :from");
            params.addValue("from", Timestamp.from(query.getFrom()));
        }
        if (query.getTo() != null) {
            sql.append(" AND timestamp < :to");
            params.addValue("to", Timestamp.from(query.getTo()));
        }
        JdbcKeysetPaging.appendAfter(sql, params, "timestamp", query.getAfter(), query.isNewestFirst());

        return sql.toString();
    }

    private static final RowMapper<ScmApiActionRecord> ROW_MAPPER = (rs, rowNum) -> ScmApiActionRecord.builder()
            .id(rs.getString("id"))
            .timestamp(rs.getTimestamp("timestamp").toInstant())
            .provider(rs.getString("provider"))
            .scmUsername(rs.getString("scm_username"))
            .resolvedUser(rs.getString("resolved_user"))
            .repoOwner(rs.getString("repo_owner"))
            .repoName(rs.getString("repo_name"))
            .mutationField(rs.getString("mutation_field"))
            .nodeId(rs.getString("node_id"))
            .nodeType(rs.getString("node_type"))
            .status(ScmApiActionStatus.valueOf(rs.getString("status")))
            .origin(rs.getString("origin") == null ? null : ScmApiActionOrigin.valueOf(rs.getString("origin")))
            .reason(rs.getString("reason"))
            .variablesJson(rs.getString("variables_json"))
            .userAgent(rs.getString("user_agent"))
            .clientType(rs.getString("client_type"))
            .clientVersion(rs.getString("client_version"))
            .upstreamStatus(rs.getObject("upstream_status", Integer.class))
            .entityId(rs.getString("entity_id"))
            .mergeCommitSha(rs.getString("merge_commit_sha"))
            .build();

    private static MapSqlParameterSource toParams(ScmApiActionRecord r) {
        return new MapSqlParameterSource()
                .addValue("id", r.getId())
                .addValue("timestamp", Timestamp.from(r.getTimestamp()))
                .addValue("provider", r.getProvider())
                .addValue("scmUsername", r.getScmUsername())
                .addValue("resolvedUser", r.getResolvedUser())
                .addValue("repoOwner", r.getRepoOwner())
                .addValue("repoName", r.getRepoName())
                .addValue("mutationField", r.getMutationField())
                .addValue("nodeId", r.getNodeId())
                .addValue("nodeType", r.getNodeType())
                .addValue("status", r.getStatus().name())
                .addValue("origin", r.getOrigin() == null ? null : r.getOrigin().name())
                .addValue("reason", r.getReason())
                .addValue("variablesJson", r.getVariablesJson())
                .addValue("userAgent", r.getUserAgent())
                .addValue("clientType", r.getClientType())
                .addValue("clientVersion", r.getClientVersion())
                .addValue("upstreamStatus", r.getUpstreamStatus())
                .addValue("entityId", r.getEntityId())
                .addValue("mergeCommitSha", r.getMergeCommitSha());
    }
}
