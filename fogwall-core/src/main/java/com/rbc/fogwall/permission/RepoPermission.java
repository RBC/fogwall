package com.rbc.fogwall.permission;

import com.rbc.fogwall.db.model.MatchTarget;
import com.rbc.fogwall.db.model.MatchType;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single authorization grant: {@link #username} is permitted to perform {@link #grant} on repos matching
 * {@link #value} at {@link #provider}.
 *
 * <p>{@link #target} selects which part of the repo URL is compared (default {@link MatchTarget#SLUG});
 * {@link #matchType} controls how {@link #value} is interpreted: {@code GLOB} for {@code *}/{@code ?} wildcards
 * (default), {@code LITERAL} for exact equality, {@code REGEX} for full Java regex.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RepoPermission implements FogwallPermission {

    @Builder.Default
    private String id = UUID.randomUUID().toString();

    private String username;
    private String provider;

    /** Which part of the repository URL is matched. Defaults to {@link MatchTarget#SLUG}. */
    @Builder.Default
    private MatchTarget target = MatchTarget.SLUG;

    /** Pattern to match against the {@link #target} portion of the URL. */
    private String value;

    /** How {@link #value} is interpreted when matching. Defaults to {@link MatchType#GLOB}. */
    @Builder.Default
    private MatchType matchType = MatchType.GLOB;

    @Builder.Default
    private Grant grant = Grant.PUSH;

    @Builder.Default
    private Source source = Source.DB;

    public enum Grant {
        /** Can submit pushes for review. */
        PUSH,
        /** Can review (approve or reject) pushes submitted by others. */
        REVIEW,
        /** Shorthand for {@link #PUSH} + {@link #REVIEW}. Does not include {@link #SELF_CERTIFY}. */
        PUSH_AND_REVIEW,
        /**
         * Trusted contributor: can certify their own clean pushes without a separate peer reviewer. All validation
         * still runs; the automated attestation is recorded in the audit log. Does not imply {@link #PUSH} or
         * {@link #REVIEW} — those must be granted separately if also needed.
         */
        SELF_CERTIFY
    }

    public enum Source {
        CONFIG,
        DB
    }
}
