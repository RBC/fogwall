package com.rbc.fogwall.db.model;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/** A lightweight record of a single fetch (or info/refs) request through the proxy. */
@Data
@Builder
public class FetchRecord {

    @Builder.Default
    private String id = UUID.randomUUID().toString();

    @Builder.Default
    private Instant timestamp = Instant.now();

    private String provider;
    private String owner;
    private String repoName;

    /** Whether the request was allowed or blocked by the access control filter. */
    private Result result;

    /** HTTP Basic username supplied by the git client. Always arbitrary — do not use for identity. */
    private String pushUsername;

    /** Resolved fogwall username, if the client's token was matched to a user account. */
    private String resolvedUser;

    public enum Result {
        ALLOWED,
        BLOCKED
    }
}
