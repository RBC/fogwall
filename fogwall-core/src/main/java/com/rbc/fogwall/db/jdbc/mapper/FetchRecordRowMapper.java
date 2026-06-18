package com.rbc.fogwall.db.jdbc.mapper;

import com.rbc.fogwall.db.model.FetchRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.RowMapper;

/** Maps a {@code fetch_records} result-set row to a {@link FetchRecord}. */
public final class FetchRecordRowMapper implements RowMapper<FetchRecord> {

    public static final FetchRecordRowMapper INSTANCE = new FetchRecordRowMapper();

    private FetchRecordRowMapper() {}

    @Override
    public FetchRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return FetchRecord.builder()
                .id(rs.getString("id"))
                .timestamp(toInstant(rs.getTimestamp("timestamp")))
                .provider(rs.getString("provider"))
                .owner(rs.getString("owner"))
                .repoName(rs.getString("repo_name"))
                .result(FetchRecord.Result.valueOf(rs.getString("result")))
                .pushUsername(rs.getString("push_username"))
                .resolvedUser(rs.getString("resolved_user"))
                .build();
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }
}
