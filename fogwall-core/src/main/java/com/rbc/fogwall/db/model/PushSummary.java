package com.rbc.fogwall.db.model;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

/**
 * Lightweight projection of a push record for list views. Omits all {@link PushStep}, {@link PushCommit}, and
 * {@link Attestation} data to keep list-endpoint responses small.
 */
@Value
@Builder
public class PushSummary {
    String id;
    PushStatus status;
    String url;
    String upstreamUrl;
    String project;
    String repoName;
    String branch;
    String commitTo;
    String author;
    String user;
    String resolvedUser;
    Instant timestamp;
}
