package com.rbc.fogwall.db.jdbc.mapper;

import com.rbc.fogwall.db.model.FetchActivity;
import com.rbc.fogwall.db.model.FetchRefusal;
import com.rbc.fogwall.git.ProxyMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.RowMapper;

/** Maps a {@code fetch_activity} result-set row to a {@link FetchActivity}. */
public final class FetchActivityRowMapper implements RowMapper<FetchActivity> {

    public static final FetchActivityRowMapper INSTANCE = new FetchActivityRowMapper();

    private FetchActivityRowMapper() {}

    @Override
    public FetchActivity mapRow(ResultSet rs, int rowNum) throws SQLException {
        String refusal = rs.getString("refusal");
        return FetchActivity.builder()
                .id(rs.getString("id"))
                .bucketStart(rs.getTimestamp("bucket_start").toInstant())
                .provider(rs.getString("provider"))
                .owner(rs.getString("owner"))
                .repoName(rs.getString("repo_name"))
                .transport(FetchActivity.Transport.valueOf(rs.getString("transport")))
                .mode(ProxyMode.valueOf(rs.getString("proxy_mode")))
                .result(FetchActivity.Result.valueOf(rs.getString("result")))
                .refusal(refusal == null ? null : FetchRefusal.valueOf(refusal))
                .ruleId(rs.getString("rule_id"))
                .fetchCount(rs.getLong("fetch_count"))
                .lastSeen(rs.getTimestamp("last_seen").toInstant())
                .build();
    }
}
