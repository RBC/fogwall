package com.rbc.fogwall.db.jdbc;

import com.rbc.fogwall.db.FetchStore;
import com.rbc.fogwall.db.jdbc.mapper.FetchRecordRowMapper;
import com.rbc.fogwall.db.model.FetchRecord;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** JDBC-backed {@link FetchStore}. Works with H2 and PostgreSQL. */
public class JdbcFetchStore implements FetchStore {

    private final DataSource dataSource;
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcFetchStore(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        this.tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Override
    public void initialize() {
        DatabaseMigrator.migrate(dataSource);
    }

    @Override
    public void record(FetchRecord fetchRecord) {
        jdbc.update(
                """
                INSERT INTO fetch_records
                    (id, timestamp, provider, owner, repo_name, result, push_username, resolved_user)
                VALUES
                    (:id, :timestamp, :provider, :owner, :repoName, :result, :pushUsername, :resolvedUser)
                """,
                new MapSqlParameterSource()
                        .addValue("id", fetchRecord.getId())
                        .addValue("timestamp", Timestamp.from(fetchRecord.getTimestamp()))
                        .addValue("provider", fetchRecord.getProvider())
                        .addValue("owner", fetchRecord.getOwner())
                        .addValue("repoName", fetchRecord.getRepoName())
                        .addValue("result", fetchRecord.getResult().name())
                        .addValue("pushUsername", fetchRecord.getPushUsername())
                        .addValue("resolvedUser", fetchRecord.getResolvedUser()));
    }

    @Override
    public List<FetchRecord> findRecent(int limit) {
        return jdbc.query(
                "SELECT * FROM fetch_records ORDER BY timestamp DESC LIMIT :limit",
                Map.of("limit", limit),
                FetchRecordRowMapper.INSTANCE);
    }

    @Override
    public List<FetchRecord> findByRepo(String provider, String owner, String repoName, int limit) {
        return jdbc.query(
                """
                SELECT * FROM fetch_records
                WHERE provider = :provider AND owner = :owner AND repo_name = :repoName
                ORDER BY timestamp DESC LIMIT :limit
                """,
                new MapSqlParameterSource()
                        .addValue("provider", provider)
                        .addValue("owner", owner)
                        .addValue("repoName", repoName)
                        .addValue("limit", limit),
                FetchRecordRowMapper.INSTANCE);
    }

    @Override
    public List<RepoFetchSummary> summarizeByRepo() {
        return jdbc.query(
                """
                SELECT provider, owner, repo_name,
                       COUNT(*) AS total,
                       SUM(CASE WHEN result = 'BLOCKED' THEN 1 ELSE 0 END) AS blocked
                FROM fetch_records
                GROUP BY provider, owner, repo_name
                ORDER BY total DESC
                """,
                (rs, rowNum) -> new RepoFetchSummary(
                        rs.getString("provider"),
                        rs.getString("owner"),
                        rs.getString("repo_name"),
                        rs.getLong("total"),
                        rs.getLong("blocked")));
    }
}
