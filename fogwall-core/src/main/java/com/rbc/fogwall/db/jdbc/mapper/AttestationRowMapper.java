package com.rbc.fogwall.db.jdbc.mapper;

import com.rbc.fogwall.db.model.Attestation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Maps a {@code push_attestations} result-set row to an {@link Attestation}. */
public final class AttestationRowMapper implements RowMapper<Attestation> {

    public static final AttestationRowMapper INSTANCE = new AttestationRowMapper();

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> ANSWERS_TYPE = new TypeReference<>() {};

    private AttestationRowMapper() {}

    @Override
    public Attestation mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Attestation.builder()
                .pushId(rs.getString("push_id"))
                .type(Attestation.Type.valueOf(rs.getString("type")))
                .reviewerUsername(rs.getString("reviewer_username"))
                .reviewerEmail(rs.getString("reviewer_email"))
                .reason(rs.getString("reason"))
                .automated(rs.getBoolean("automated"))
                .selfApproval(rs.getBoolean("self_approval"))
                .timestamp(toInstant(rs.getTimestamp("timestamp")))
                .answers(parseAnswers(rs.getString("answers")))
                .build();
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }

    private static Map<String, String> parseAnswers(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, ANSWERS_TYPE);
        } catch (Exception e) {
            return null;
        }
    }
}
