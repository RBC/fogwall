package com.rbc.fogwall.user;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** JDBC-backed {@link GitCredentialStore}, keeping credentials in {@code user_git_credentials}. */
public class JdbcGitCredentialStore implements GitCredentialStore {

    private static final String COLUMNS = "id, username, name, secret_hash, created_at, expires_at, last_used_at";

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcGitCredentialStore(DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    @Override
    public void save(GitCredential credential) {
        var params = new HashMap<String, Object>();
        params.put("id", credential.id());
        params.put("username", credential.username());
        params.put("name", credential.name());
        params.put("secretHash", credential.secretHash());
        params.put("createdAt", timestamp(credential.createdAt()));
        params.put("expiresAt", timestamp(credential.expiresAt()));
        params.put("lastUsedAt", timestamp(credential.lastUsedAt()));
        try {
            jdbc.update(
                    "INSERT INTO user_git_credentials (" + COLUMNS + ") VALUES "
                            + "(:id, :username, :name, :secretHash, :createdAt, :expiresAt, :lastUsedAt)",
                    params);
        } catch (DuplicateKeyException e) {
            throw new GitCredentialNameConflictException(credential.username(), credential.name(), e);
        }
    }

    @Override
    public Optional<GitCredential> findById(String id) {
        return jdbc
                .query(
                        "SELECT " + COLUMNS + " FROM user_git_credentials WHERE id = :id",
                        Map.of("id", id),
                        (rs, rowNum) -> map(rs))
                .stream()
                .findFirst();
    }

    @Override
    public List<GitCredential> findByUsername(String username) {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM user_git_credentials WHERE username = :username ORDER BY name",
                Map.of("username", username),
                (rs, rowNum) -> map(rs));
    }

    @Override
    public boolean replaceSecret(String id, String secretHash, Instant createdAt, Instant expiresAt) {
        var params = new HashMap<String, Object>();
        params.put("id", id);
        params.put("secretHash", secretHash);
        params.put("createdAt", timestamp(createdAt));
        params.put("expiresAt", timestamp(expiresAt));
        return jdbc.update(
                        "UPDATE user_git_credentials SET secret_hash = :secretHash, created_at = :createdAt, "
                                + "expires_at = :expiresAt, last_used_at = NULL WHERE id = :id",
                        params)
                > 0;
    }

    @Override
    public void recordUse(String id, Instant usedAt) {
        jdbc.update(
                "UPDATE user_git_credentials SET last_used_at = :usedAt WHERE id = :id",
                Map.of("id", id, "usedAt", Timestamp.from(usedAt)));
    }

    @Override
    public boolean delete(String id) {
        return jdbc.update("DELETE FROM user_git_credentials WHERE id = :id", Map.of("id", id)) > 0;
    }

    @Override
    public int deleteByUsername(String username) {
        return jdbc.update("DELETE FROM user_git_credentials WHERE username = :username", Map.of("username", username));
    }

    private static GitCredential map(ResultSet rs) throws SQLException {
        return new GitCredential(
                rs.getString("id"),
                rs.getString("username"),
                rs.getString("name"),
                rs.getString("secret_hash"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("expires_at")),
                instant(rs.getTimestamp("last_used_at")));
    }

    private static Timestamp timestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}
