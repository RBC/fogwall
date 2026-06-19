package com.rbc.fogwall.db.model;

/** How a pattern value is matched against a repository path component. */
public enum MatchType {
    /** Exact string equality (case-sensitive). Leading {@code /} is normalised before comparison. */
    LITERAL,
    /** Shell glob: {@code *} matches within one path segment; {@code **} matches any depth. */
    GLOB,
    /** Full Java regex matched against the value as-is. */
    REGEX
}
