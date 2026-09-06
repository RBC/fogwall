package com.rbc.fogwall.scmapi;

import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/**
 * A single access-control rule for the SCM API proxy: whether {@link #provider} may serve {@link #operation} traffic at
 * all.
 *
 * <p>Deliberately its own model, not a reuse of {@code AccessRule}/{@code UrlRuleRegistry}: those match on the
 * repository <em>path</em> in the request URL, which GraphQL traffic (GitHub) doesn't have — a mutation's target is an
 * opaque node ID resolved separately (see {@link MutationNodeIdRef} + {@code RepoPermissionService
 * .isAllowedToPropose}), and an ad-hoc read query has no reliable path shape at all. This rule is intentionally
 * coarser: provider-level only, not per-repo. Per-repo read gating is tracked as follow-up scope, not implemented here
 * — see docs/internals/SCM_API_PROXY.md.
 */
@Data
@Builder
public class ScmApiAccessRule {

    @Builder.Default
    private String id = UUID.randomUUID().toString();

    /** Provider name this rule applies to (e.g. "github"). */
    private String provider;

    /** Which SCM API traffic this rule applies to. */
    @Builder.Default
    private Operation operation = Operation.BOTH;

    /** Whether this rule allows or denies matched traffic. */
    @Builder.Default
    private Access access = Access.ALLOW;

    /**
     * Whether this rule was seeded from YAML configuration ({@code CONFIG}) or created via the REST API ({@code DB}).
     */
    @Builder.Default
    private Source source = Source.DB;

    public enum Operation {
        /** GraphQL {@code query} traffic. */
        READ,
        /** GraphQL {@code mutation} traffic. */
        MUTATE,
        BOTH
    }

    public enum Access {
        ALLOW,
        DENY
    }

    public enum Source {
        CONFIG,
        DB
    }
}
