package com.rbc.fogwall.db.model;

/** Which part of the repository URL path the match pattern is applied to. */
public enum MatchTarget {
    /** Full {@code /owner/repo} slug. */
    SLUG,
    /** Owner or organisation portion only (e.g. {@code myorg}). */
    OWNER,
    /** Repository name portion only (e.g. {@code my-repo}). */
    NAME
}
