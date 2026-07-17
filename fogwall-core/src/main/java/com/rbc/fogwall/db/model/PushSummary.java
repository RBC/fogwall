package com.rbc.fogwall.db.model;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

/**
 * Lightweight projection of a push record for list views. Omits all {@link PushStep}, {@link PushCommit}, and
 * {@link Attestation} data to keep list-endpoint responses small.
 */
@Value
@Builder(toBuilder = true)
public class PushSummary {
    String id;
    PushStatus status;
    String url;
    String upstreamUrl;
    String provider;
    String project;
    String repoName;
    String branch;
    String commitTo;
    String author;
    String user;
    String resolvedUser;
    Instant timestamp;

    /** Browsable web URL for the repository, computed from the provider at query time. Absent for generic providers. */
    String repoUrl;

    /**
     * Browsable web URL for {@link #commitTo}, computed from the provider at query time. Absent for generic providers.
     */
    String commitUrl;
}
