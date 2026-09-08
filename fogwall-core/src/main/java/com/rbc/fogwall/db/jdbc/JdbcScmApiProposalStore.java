package com.rbc.fogwall.db.jdbc;

import com.rbc.fogwall.db.ScmApiProposalStore;
import com.rbc.fogwall.db.model.ScmApiProposalRecord;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** JDBC-backed {@link ScmApiProposalStore} over the {@code scm_api_proposals} table. */
public class JdbcScmApiProposalStore implements ScmApiProposalStore {

    private final DataSource dataSource;
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcScmApiProposalStore(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    @Override
    public void initialize() {
        DatabaseMigrator.migrate(dataSource);
    }

    @Override
    public void save(ScmApiProposalRecord record) {
        jdbc.update("""
                INSERT INTO scm_api_proposals (id, provider, repo_owner, repo_name, kind, proposal_number, url, node_id,
                    title, state, created_by, created_by_scm_username, created_at, updated_at, created_action_id,
                    last_action_id)
                VALUES (:id, :provider, :repoOwner, :repoName, :kind, :number, :url, :nodeId, :title, :state,
                    :createdBy, :createdByScmUsername, :createdAt, :updatedAt, :createdActionId, :lastActionId)
                """, toParams(record));
    }

    @Override
    public void update(ScmApiProposalRecord record) {
        jdbc.update("""
                UPDATE scm_api_proposals SET url = :url, node_id = :nodeId, title = :title, state = :state,
                    updated_at = :updatedAt, last_action_id = :lastActionId
                WHERE id = :id
                """, toParams(record));
    }

    @Override
    public Optional<ScmApiProposalRecord> findById(String id) {
        return jdbc.query("SELECT * FROM scm_api_proposals WHERE id = :id", Map.of("id", id), ROW_MAPPER).stream()
                .findFirst();
    }

    @Override
    public Optional<ScmApiProposalRecord> findByTarget(
            String provider, String repoOwner, String repoName, ScmApiProposalRecord.Kind kind, int number) {
        return jdbc
                .query(
                        """
                        SELECT * FROM scm_api_proposals WHERE provider = :provider AND repo_owner = :repoOwner
                            AND repo_name = :repoName AND kind = :kind AND proposal_number = :number
                        """,
                        new MapSqlParameterSource()
                                .addValue("provider", provider)
                                .addValue("repoOwner", repoOwner)
                                .addValue("repoName", repoName)
                                .addValue("kind", kind.name())
                                .addValue("number", number),
                        ROW_MAPPER)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<ScmApiProposalRecord> findByNodeId(String provider, String nodeId) {
        return jdbc
                .query(
                        "SELECT * FROM scm_api_proposals WHERE provider = :provider AND node_id = :nodeId",
                        Map.of("provider", provider, "nodeId", nodeId),
                        ROW_MAPPER)
                .stream()
                .findFirst();
    }

    @Override
    public List<ScmApiProposalRecord> findByIds(Collection<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.query("SELECT * FROM scm_api_proposals WHERE id IN (:ids)", Map.of("ids", ids), ROW_MAPPER);
    }

    private static final RowMapper<ScmApiProposalRecord> ROW_MAPPER = (rs, rowNum) -> ScmApiProposalRecord.builder()
            .id(rs.getString("id"))
            .provider(rs.getString("provider"))
            .repoOwner(rs.getString("repo_owner"))
            .repoName(rs.getString("repo_name"))
            .kind(ScmApiProposalRecord.Kind.valueOf(rs.getString("kind")))
            .number(rs.getInt("proposal_number"))
            .url(rs.getString("url"))
            .nodeId(rs.getString("node_id"))
            .title(rs.getString("title"))
            .state(ScmApiProposalRecord.State.valueOf(rs.getString("state")))
            .createdBy(rs.getString("created_by"))
            .createdByScmUsername(rs.getString("created_by_scm_username"))
            .createdAt(rs.getTimestamp("created_at").toInstant())
            .updatedAt(rs.getTimestamp("updated_at").toInstant())
            .createdActionId(rs.getString("created_action_id"))
            .lastActionId(rs.getString("last_action_id"))
            .build();

    private static MapSqlParameterSource toParams(ScmApiProposalRecord r) {
        return new MapSqlParameterSource()
                .addValue("id", r.getId())
                .addValue("provider", r.getProvider())
                .addValue("repoOwner", r.getRepoOwner())
                .addValue("repoName", r.getRepoName())
                .addValue("kind", r.getKind().name())
                .addValue("number", r.getNumber())
                .addValue("url", r.getUrl())
                .addValue("nodeId", r.getNodeId())
                .addValue("title", r.getTitle())
                .addValue("state", r.getState().name())
                .addValue("createdBy", r.getCreatedBy())
                .addValue("createdByScmUsername", r.getCreatedByScmUsername())
                .addValue("createdAt", Timestamp.from(r.getCreatedAt()))
                .addValue("updatedAt", Timestamp.from(r.getUpdatedAt()))
                .addValue("createdActionId", r.getCreatedActionId())
                .addValue("lastActionId", r.getLastActionId());
    }
}
