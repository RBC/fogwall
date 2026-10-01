package com.rbc.fogwall.db.jdbc;

import com.rbc.fogwall.db.FetchStore;
import com.rbc.fogwall.db.jdbc.mapper.FetchActivityRowMapper;
import com.rbc.fogwall.db.model.FetchActivity;
import com.rbc.fogwall.db.model.FetchActivityQuery;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** JDBC-backed {@link FetchStore}. Works with H2, PostgreSQL, MySQL, and MariaDB. */
public class JdbcFetchStore implements FetchStore {

    private static final String INCREMENT = """
            UPDATE fetch_activity
            SET fetch_count = fetch_count + :fetchCount,
                last_seen = CASE WHEN last_seen < :lastSeen THEN :lastSeen ELSE last_seen END
            WHERE id = :id
            """;

    private static final String INSERT = """
            INSERT INTO fetch_activity
                (id, bucket_start, provider, owner, repo_name, transport, proxy_mode, result, refusal, rule_id,
                 fetch_count, last_seen)
            VALUES
                (:id, :bucketStart, :provider, :owner, :repoName, :transport, :mode, :result, :refusal, :ruleId,
                 :fetchCount, :lastSeen)
            """;

    private final DataSource dataSource;
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcFetchStore(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    @Override
    public void initialize() {
        DatabaseMigrator.migrate(dataSource);
    }

    /**
     * Update first, since after the first flush of an hour most rows exist. A row that does not is inserted; when
     * another instance inserted it in between, the insert fails on the key and the update then finds it.
     */
    @Override
    public void add(Collection<FetchActivity> increments) {
        for (FetchActivity row : increments) {
            MapSqlParameterSource params = toParams(row);
            if (jdbc.update(INCREMENT, params) > 0) {
                continue;
            }
            try {
                jdbc.update(INSERT, params);
            } catch (DuplicateKeyException e) {
                jdbc.update(INCREMENT, params);
            }
        }
    }

    @Override
    public List<FetchActivity> find(FetchActivityQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = buildWhere(query, params);
        String direction = query.isNewestFirst() ? "DESC" : "ASC";
        String sql = "SELECT * FROM fetch_activity" + where
                + " ORDER BY bucket_start " + direction + ", last_seen " + direction
                + " LIMIT :limit OFFSET :offset";
        params.addValue("limit", query.getLimit());
        params.addValue("offset", query.getOffset());
        return jdbc.query(sql, params, FetchActivityRowMapper.INSTANCE);
    }

    private static String buildWhere(FetchActivityQuery query, MapSqlParameterSource params) {
        StringBuilder sql = new StringBuilder(" WHERE 1=1");

        if (query.getResult() != null) {
            sql.append(" AND result = :result");
            params.addValue("result", query.getResult().name());
        }
        if (query.getTransport() != null) {
            sql.append(" AND transport = :transport");
            params.addValue("transport", query.getTransport().name());
        }
        if (query.getProvider() != null) {
            sql.append(" AND provider = :provider");
            params.addValue("provider", query.getProvider());
        }
        if (query.getOwner() != null) {
            sql.append(" AND owner = :owner");
            params.addValue("owner", query.getOwner());
        }
        if (query.getRepoName() != null) {
            sql.append(" AND repo_name = :repoName");
            params.addValue("repoName", query.getRepoName());
        }
        if (query.getSearch() != null && !query.getSearch().isBlank()) {
            sql.append(" AND (LOWER(owner) LIKE :search OR LOWER(repo_name) LIKE :search)");
            params.addValue("search", "%" + query.getSearch().toLowerCase() + "%");
        }

        return sql.toString();
    }

    @Override
    public List<RepoFetchSummary> summarizeByRepo() {
        return jdbc.query(
                """
                SELECT provider, owner, repo_name,
                       SUM(fetch_count) AS total,
                       SUM(CASE WHEN result = 'BLOCKED' THEN fetch_count ELSE 0 END) AS blocked
                FROM fetch_activity
                WHERE owner IS NOT NULL AND repo_name IS NOT NULL
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

    @Override
    public void pruneBefore(Instant cutoff) {
        jdbc.update(
                "DELETE FROM fetch_activity WHERE bucket_start < :cutoff", Map.of("cutoff", Timestamp.from(cutoff)));
    }

    private static MapSqlParameterSource toParams(FetchActivity r) {
        return new MapSqlParameterSource()
                .addValue("id", r.getId())
                .addValue("bucketStart", Timestamp.from(r.getBucketStart()))
                .addValue("provider", r.getProvider())
                .addValue("owner", r.getOwner())
                .addValue("repoName", r.getRepoName())
                .addValue("transport", r.getTransport().name())
                .addValue("mode", r.getMode().name())
                .addValue("result", r.getResult().name())
                .addValue(
                        "refusal",
                        r.getRefusal() == null ? null : r.getRefusal().name())
                .addValue("ruleId", r.getRuleId())
                .addValue("fetchCount", r.getFetchCount())
                .addValue("lastSeen", Timestamp.from(r.getLastSeen()));
    }
}
