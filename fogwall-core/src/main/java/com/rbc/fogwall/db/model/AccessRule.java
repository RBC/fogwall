package com.rbc.fogwall.db.model;

import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * A single access control rule governing which repositories may be fetched from or pushed to through the proxy. Rules
 * are evaluated by {@code UrlRuleEvaluator} (see #60).
 *
 * <p>{@link #target} selects which part of the repo URL is compared; {@link #value} is the pattern string;
 * {@link #matchType} controls how the pattern is interpreted (literal equality, glob, or regex).
 */
@Data
@Builder
@Jacksonized
public class AccessRule {

    @Builder.Default
    private String id = UUID.randomUUID().toString();

    /** Provider name this rule applies to. Null = applies to all providers. */
    private String provider;

    /** Which part of the repository URL is matched. Defaults to {@link MatchTarget#SLUG}. */
    @Builder.Default
    private MatchTarget target = MatchTarget.SLUG;

    /** Pattern to match against the {@link #target} portion of the URL. */
    private String value;

    /** How {@link #value} is interpreted when matching. Defaults to {@link MatchType#GLOB}. */
    @Builder.Default
    private MatchType matchType = MatchType.GLOB;

    /** Whether this rule allows or denies matched repositories. */
    @Builder.Default
    private Access access = Access.ALLOW;

    /** Which git operations this rule applies to. */
    @Builder.Default
    private Operations operations = Operations.BOTH;

    private String description;

    @Builder.Default
    private boolean enabled = true;

    @Builder.Default
    private int ruleOrder = 100;

    /**
     * Whether this rule was seeded from YAML configuration ({@code CONFIG}) or created via the REST API ({@code DB}).
     */
    @Builder.Default
    private Source source = Source.DB;

    public enum Access {
        ALLOW,
        DENY
    }

    public enum Operations {
        FETCH,
        PUSH,
        BOTH
    }

    public enum Source {
        CONFIG,
        DB
    }
}
