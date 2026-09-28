package com.rbc.fogwall.db.jdbc;

import com.rbc.fogwall.db.PackChunks;
import com.rbc.fogwall.db.ParkedPushStore;
import com.rbc.fogwall.db.model.ParkedPush;
import com.rbc.fogwall.db.model.ParkedRefUpdate;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * JDBC-backed {@link ParkedPushStore}, keeping parked pushes in {@code parked_pushes}, their ref updates in
 * {@code parked_push_refs} and their packs in {@code parked_push_chunks}. A push is parked in one transaction, so a
 * reader never sees a partial one.
 */
public class JdbcParkedPushStore implements ParkedPushStore {

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcParkedPushStore(DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        this.tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Override
    public ParkedPush park(
            String pushId,
            String providerName,
            String forwardUser,
            String upstreamUrl,
            List<ParkedRefUpdate> refs,
            InputStream pack)
            throws IOException {
        Instant parkedAt = Instant.now();
        try {
            return tx.execute(status -> {
                PackChunks.Written written;
                try {
                    written = PackChunks.write(
                            pack,
                            (seq, data) -> jdbc.update(
                                    "INSERT INTO parked_push_chunks (push_id, seq, data) VALUES (:pushId, :seq, :data)",
                                    new MapSqlParameterSource()
                                            .addValue("pushId", pushId)
                                            .addValue("seq", seq)
                                            .addValue("data", data)));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                for (int i = 0; i < refs.size(); i++) {
                    ParkedRefUpdate ref = refs.get(i);
                    jdbc.update(
                            """
                            INSERT INTO parked_push_refs (push_id, ordinal, ref_name, old_id, new_id, update_type)
                            VALUES (:pushId, :ordinal, :refName, :oldId, :newId, :type)
                            """,
                            new MapSqlParameterSource()
                                    .addValue("pushId", pushId)
                                    .addValue("ordinal", i)
                                    .addValue("refName", ref.refName())
                                    .addValue("oldId", ref.oldId())
                                    .addValue("newId", ref.newId())
                                    .addValue("type", ref.type().name()));
                }
                jdbc.update(
                        """
                        INSERT INTO parked_pushes (push_id, provider_name, forward_user, upstream_url, pack_bytes,
                            pack_sha256, parked_at)
                        VALUES (:pushId, :providerName, :forwardUser, :upstreamUrl, :packBytes, :packSha256, :parkedAt)
                        """,
                        new MapSqlParameterSource()
                                .addValue("pushId", pushId)
                                .addValue("providerName", providerName)
                                .addValue("forwardUser", forwardUser)
                                .addValue("upstreamUrl", upstreamUrl)
                                .addValue("packBytes", written.length())
                                .addValue("packSha256", written.sha256())
                                .addValue("parkedAt", Timestamp.from(parkedAt)));
                return new ParkedPush(pushId, providerName, forwardUser, upstreamUrl, refs, written.length(), parkedAt);
            });
        } catch (UncheckedIOException e) {
            throw new IOException("Could not read the pushed pack", e);
        }
    }

    @Override
    public Optional<ParkedPush> find(String pushId) {
        List<ParkedRefUpdate> refs = jdbc.query(
                "SELECT ref_name, old_id, new_id, update_type FROM parked_push_refs WHERE push_id = :pushId"
                        + " ORDER BY ordinal",
                Map.of("pushId", pushId),
                (rs, rowNum) -> new ParkedRefUpdate(
                        rs.getString("ref_name"),
                        rs.getString("old_id"),
                        rs.getString("new_id"),
                        ReceiveCommand.Type.valueOf(rs.getString("update_type"))));
        return jdbc
                .query(
                        "SELECT provider_name, forward_user, upstream_url, pack_bytes, parked_at FROM parked_pushes"
                                + " WHERE push_id = :pushId",
                        Map.of("pushId", pushId),
                        (rs, rowNum) -> new ParkedPush(
                                pushId,
                                rs.getString("provider_name"),
                                rs.getString("forward_user"),
                                rs.getString("upstream_url"),
                                refs,
                                rs.getLong("pack_bytes"),
                                rs.getTimestamp("parked_at").toInstant()))
                .stream()
                .findFirst();
    }

    @Override
    public InputStream openPack(String pushId) throws IOException {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT pack_bytes, pack_sha256 FROM parked_pushes WHERE push_id = :pushId", Map.of("pushId", pushId));
        if (rows.isEmpty()) {
            throw new IOException("No pack is stored for push " + pushId);
        }
        long length = ((Number) rows.get(0).get("pack_bytes")).longValue();
        String sha256 = (String) rows.get(0).get("pack_sha256");
        return PackChunks.read(
                length,
                sha256,
                seq -> jdbc
                        .query(
                                "SELECT data FROM parked_push_chunks WHERE push_id = :pushId AND seq = :seq",
                                Map.of("pushId", pushId, "seq", seq),
                                (rs, rowNum) -> rs.getBytes("data"))
                        .stream()
                        .findFirst());
    }

    @Override
    public void delete(String pushId) {
        tx.executeWithoutResult(status -> {
            Map<String, String> params = Map.of("pushId", pushId);
            jdbc.update("DELETE FROM parked_pushes WHERE push_id = :pushId", params);
            jdbc.update("DELETE FROM parked_push_refs WHERE push_id = :pushId", params);
            jdbc.update("DELETE FROM parked_push_chunks WHERE push_id = :pushId", params);
        });
    }

    @Override
    public List<String> findReclaimable(Instant failedBefore, Instant orphanedBefore, int limit) {
        return jdbc.queryForList(
                """
                SELECT p.push_id FROM parked_pushes p
                LEFT JOIN push_records r ON r.id = p.push_id
                WHERE (r.id IS NULL AND p.parked_at < :orphanedBefore)
                    OR r.status NOT IN ('PENDING', 'APPROVED', 'ERROR')
                    OR (r.status = 'ERROR' AND COALESCE(r.forwarded_at, p.parked_at) < :failedBefore)
                ORDER BY p.parked_at LIMIT :limit
                """,
                new MapSqlParameterSource()
                        .addValue("orphanedBefore", Timestamp.from(orphanedBefore))
                        .addValue("failedBefore", Timestamp.from(failedBefore))
                        .addValue("limit", limit),
                String.class);
    }
}
