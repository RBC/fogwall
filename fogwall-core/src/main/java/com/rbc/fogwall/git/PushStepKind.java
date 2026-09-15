package com.rbc.fogwall.git;

import java.util.Optional;
import java.util.Set;

/**
 * Canonical identity for a step in the push pipeline, shared by a server-mode hook and its transparent-proxy filter
 * counterpart. This is the single source of a step's name wherever it surfaces — the persisted audit record, and later
 * traces and metrics — so the two modes report the same step under the same {@link #key()}.
 *
 * <p>This is only an <em>identity</em>: which step. It is intentionally orthogonal to where a step runs in the request
 * lifecycle and to which chain positions are open for extension; that ordering/extensibility model is a separate axis.
 *
 * <p>{@link #modes()} declares the proxy modes a step applies to. Most steps run in both; a step that legitimately
 * exists in only one mode declares just that one, with the reason on the constant, so the fact is in the domain model
 * rather than buried in a test.
 */
public enum PushStepKind {

    // Declared in execution order; displayOrder is that flat position. A step declares its StepVisibility so the
    // client-output and dashboard layers read intent instead of inferring it from the number. Steps present in only
    // one mode carry the reason on the constant.

    /** Parses the HTTP request into a git operation and repo path; server mode parses inside JGit before any hook. */
    PARSE_REQUEST("parse-request", 1, StepVisibility.INTERNAL, ProxyMode.TRANSPARENT),
    /**
     * Lets an approved re-push through without repeating validation; server mode blocks synchronously in one request.
     */
    ALLOW_APPROVED_PUSH("allow-approved-push", 2, StepVisibility.INTERNAL, ProxyMode.TRANSPARENT),
    /** Buffers and enriches commit data the proxy can't re-read from the pack; server-mode hooks read JGit directly. */
    ENRICH_COMMITS("enrich-commits", 3, StepVisibility.INTERNAL, ProxyMode.TRANSPARENT),

    URL_RULE("url-rule", 4, StepVisibility.SUMMARY, ProxyMode.SERVER, ProxyMode.TRANSPARENT),
    /** Rewrites Bitbucket credentials before forwarding — internal plumbing, not a developer-facing check. */
    BITBUCKET_CREDENTIAL_REWRITE(
            "bitbucket-credential-rewrite", 5, StepVisibility.INTERNAL, ProxyMode.SERVER, ProxyMode.TRANSPARENT),
    PUSH_PERMISSION("push-permission", 6, StepVisibility.SUMMARY, ProxyMode.SERVER, ProxyMode.TRANSPARENT),
    COMMIT_ATTRIBUTION("commit-attribution", 7, StepVisibility.SUMMARY, ProxyMode.SERVER, ProxyMode.TRANSPARENT),
    PRIOR_PUSH_ENRICHMENT("prior-push-enrichment", 8, StepVisibility.INTERNAL, ProxyMode.SERVER),
    EMPTY_BRANCH("empty-branch", 9, StepVisibility.SUMMARY, ProxyMode.SERVER, ProxyMode.TRANSPARENT),
    HIDDEN_COMMITS("hidden-commits", 10, StepVisibility.SUMMARY, ProxyMode.SERVER, ProxyMode.TRANSPARENT),
    AUTHOR_EMAIL("author-email", 11, StepVisibility.SUMMARY, ProxyMode.SERVER, ProxyMode.TRANSPARENT),
    TRAILERS("trailers", 12, StepVisibility.SUMMARY, ProxyMode.SERVER, ProxyMode.TRANSPARENT),
    COMMIT_MESSAGE("commit-message", 13, StepVisibility.SUMMARY, ProxyMode.SERVER, ProxyMode.TRANSPARENT),
    CONTENT_PATTERN_MESSAGE(
            "content-pattern-message", 14, StepVisibility.SUMMARY, ProxyMode.SERVER, ProxyMode.TRANSPARENT),
    /** JGit commit inspection specific to the pre-receive transport; no transparent-proxy object model for it. */
    COMMIT_INSPECTION("commit-inspection", 15, StepVisibility.INTERNAL, ProxyMode.SERVER),
    /** Generates the diff server mode's own hooks scan; the proxy computes diffs inline in its filters instead. */
    DIFF_GENERATION("diff-generation", 16, StepVisibility.INTERNAL, ProxyMode.SERVER),
    BINARY_BLOB("binary-blob", 17, StepVisibility.SUMMARY, ProxyMode.SERVER, ProxyMode.TRANSPARENT),
    DIFF_SCAN("diff-scan", 18, StepVisibility.SUMMARY, ProxyMode.SERVER, ProxyMode.TRANSPARENT),
    GPG_SIGNATURE("gpg-signature", 19, StepVisibility.SUMMARY, ProxyMode.SERVER, ProxyMode.TRANSPARENT),
    SECRET_SCAN("secret-scan", 20, StepVisibility.SUMMARY, ProxyMode.SERVER, ProxyMode.TRANSPARENT),
    CONTENT_PATTERN_DIFF("content-pattern-diff", 21, StepVisibility.SUMMARY, ProxyMode.SERVER, ProxyMode.TRANSPARENT),

    /** Renders accumulated per-step results into the one buffered response; server mode streams messages live. */
    VALIDATION_SUMMARY("validation-summary", 22, StepVisibility.INTERNAL, ProxyMode.TRANSPARENT),
    /** Finalizes proxied fetch (read) requests; server mode's fetch path is upload-pack, separate from this chain. */
    FETCH_FINALIZER("fetch-finalizer", 23, StepVisibility.INTERNAL, ProxyMode.TRANSPARENT),
    /** Sends the final buffered response and triggers forwarding; server mode forwards in a post-receive hook. */
    PUSH_FINALIZER("push-finalizer", 24, StepVisibility.INTERNAL, ProxyMode.TRANSPARENT),
    /** Request-level audit logging for the proxy; server mode's audit trail is a pinned lifecycle hook. */
    AUDIT_LOG("audit-log", 25, StepVisibility.INTERNAL, ProxyMode.TRANSPARENT);

    private final String key;
    private final int displayOrder;
    private final StepVisibility visibility;
    private final Set<ProxyMode> modes;

    PushStepKind(String key, int displayOrder, StepVisibility visibility, ProxyMode... modes) {
        this.key = key;
        this.displayOrder = displayOrder;
        this.visibility = visibility;
        this.modes = Set.of(modes);
    }

    /** Stable, provider- and mode-independent identifier, used as the persisted step name and the telemetry label. */
    public String key() {
        return key;
    }

    /**
     * The persisted {@code step_order} value for this step — a stable, flat display-ordering key for a push record's
     * step list, identical across proxy modes and independent of where the step runs in the chain (that is
     * {@link LifecycleStage}). It is the step's position in the pipeline; it carries no other meaning.
     */
    public int displayOrder() {
        return displayOrder;
    }

    /** How far this step surfaces toward the developer; see {@link StepVisibility}. */
    public StepVisibility visibility() {
        return visibility;
    }

    /** Whether this step is user-facing at all (i.e. not pure internal plumbing). */
    public boolean displayable() {
        return visibility.displayable();
    }

    /** Whether this step appears in the terse git-client push summary. */
    public boolean summarizable() {
        return visibility.summarizable();
    }

    /** The proxy modes this step runs in. */
    public Set<ProxyMode> modes() {
        return modes;
    }

    /** Whether this step runs in the given proxy mode. */
    public boolean runsIn(ProxyMode mode) {
        return modes.contains(mode);
    }

    /** The kind with the given {@link #key()}, or empty for a step name that is not a canonical kind. */
    public static Optional<PushStepKind> forKey(String key) {
        for (PushStepKind kind : values()) {
            if (kind.key.equals(key)) return Optional.of(kind);
        }
        return Optional.empty();
    }
}
