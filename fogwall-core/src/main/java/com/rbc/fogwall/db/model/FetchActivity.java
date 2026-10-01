package com.rbc.fogwall.db.model;

import com.rbc.fogwall.git.ProxyMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import lombok.Builder;
import lombok.Data;

/**
 * How many clones and fetches fogwall decided in one hour, for one combination of repository, transport, mode, and
 * outcome. Fetches are counted, not recorded one by one: fetch access is a proxy-wide URL rule, the upstream still
 * decides what the client's own credential may read, and a per-request row would put a database write on every clone.
 */
@Data
@Builder(toBuilder = true)
public class FetchActivity {

    /** {@link Key#id()} of this row's dimensions. */
    private String id;

    /** Start of the hour, UTC. */
    private Instant bucketStart;

    private String provider;

    /** Null on the row that collects fetches past the in-memory key cap. */
    private String owner;

    /** Null on the row that collects fetches past the in-memory key cap. */
    private String repoName;

    private Transport transport;
    private ProxyMode mode;
    private Result result;

    /** Why a {@link Result#BLOCKED} fetch was refused. Null for an allowed one. */
    private FetchRefusal refusal;

    /** The URL rule that matched, allowing or denying. Null when no rule matched or a refusal was not a rule's. */
    private String ruleId;

    private long fetchCount;

    /** The most recent fetch counted in this row. */
    private Instant lastSeen;

    public enum Result {
        ALLOWED,
        BLOCKED
    }

    public enum Transport {
        HTTP,
        SSH
    }

    /** The dimensions a row is counted under. */
    public record Key(
            Instant bucketStart,
            String provider,
            String owner,
            String repoName,
            Transport transport,
            ProxyMode mode,
            Result result,
            FetchRefusal refusal,
            String ruleId) {

        /** The same fetch with its repository and rule folded away, for when the in-memory key cap is reached. */
        public Key overflow() {
            return new Key(bucketStart, provider, null, null, transport, mode, result, refusal, null);
        }

        /** A stable id for the row these dimensions count under, equal on every fogwall instance. */
        public String id() {
            StringBuilder joined = new StringBuilder();
            for (Object part :
                    new Object[] {bucketStart, provider, owner, repoName, transport, mode, result, refusal, ruleId}) {
                // A separator no dimension can contain, and a marker null cannot collide with.
                joined.append(part == null ? "\u0001" : part.toString()).append('\u0000');
            }
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256")
                        .digest(joined.toString().getBytes(StandardCharsets.UTF_8));
                return HexFormat.of().formatHex(digest);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 is required of every JVM", e);
            }
        }

        public FetchActivity toActivity(long fetchCount, Instant lastSeen) {
            return FetchActivity.builder()
                    .id(id())
                    .bucketStart(bucketStart)
                    .provider(provider)
                    .owner(owner)
                    .repoName(repoName)
                    .transport(transport)
                    .mode(mode)
                    .result(result)
                    .refusal(refusal)
                    .ruleId(ruleId)
                    .fetchCount(fetchCount)
                    .lastSeen(lastSeen)
                    .build();
        }
    }
}
