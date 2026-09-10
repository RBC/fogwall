package com.rbc.fogwall.git;

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

    // --- steps present in both modes ---

    URL_RULE("url-rule", ProxyMode.SERVER, ProxyMode.TRANSPARENT),
    PUSH_PERMISSION("push-permission", ProxyMode.SERVER, ProxyMode.TRANSPARENT),
    COMMIT_ATTRIBUTION("commit-attribution", ProxyMode.SERVER, ProxyMode.TRANSPARENT),
    EMPTY_BRANCH("empty-branch", ProxyMode.SERVER, ProxyMode.TRANSPARENT),
    HIDDEN_COMMITS("hidden-commits", ProxyMode.SERVER, ProxyMode.TRANSPARENT),
    AUTHOR_EMAIL("author-email", ProxyMode.SERVER, ProxyMode.TRANSPARENT),
    TRAILERS("trailers", ProxyMode.SERVER, ProxyMode.TRANSPARENT),
    COMMIT_MESSAGE("commit-message", ProxyMode.SERVER, ProxyMode.TRANSPARENT),
    CONTENT_PATTERN_MESSAGE("content-pattern-message", ProxyMode.SERVER, ProxyMode.TRANSPARENT),
    BINARY_BLOB("binary-blob", ProxyMode.SERVER, ProxyMode.TRANSPARENT),
    DIFF_SCAN("diff-scan", ProxyMode.SERVER, ProxyMode.TRANSPARENT),
    GPG_SIGNATURE("gpg-signature", ProxyMode.SERVER, ProxyMode.TRANSPARENT),
    SECRET_SCAN("secret-scan", ProxyMode.SERVER, ProxyMode.TRANSPARENT),
    CONTENT_PATTERN_DIFF("content-pattern-diff", ProxyMode.SERVER, ProxyMode.TRANSPARENT),
    BITBUCKET_CREDENTIAL_REWRITE("bitbucket-credential-rewrite", ProxyMode.SERVER, ProxyMode.TRANSPARENT),

    // --- server-mode only ---

    /** JGit commit inspection specific to the pre-receive transport; no transparent-proxy object model for it. */
    COMMIT_INSPECTION("commit-inspection", ProxyMode.SERVER),
    /** Generates the diff server mode's own hooks scan; the proxy computes diffs inline in its filters instead. */
    DIFF_GENERATION("diff-generation", ProxyMode.SERVER),
    /**
     * Backfills push-record history a server-mode push always has; the proxy has no equivalent record at this stage.
     */
    PRIOR_PUSH_ENRICHMENT("prior-push-enrichment", ProxyMode.SERVER),

    // --- transparent-proxy only ---

    /** Parses the HTTP request into a git operation and repo path; server mode parses inside JGit before any hook. */
    PARSE_REQUEST("parse-request", ProxyMode.TRANSPARENT),
    /** Buffers and enriches commit data the proxy can't re-read from the pack; server-mode hooks read JGit directly. */
    ENRICH_COMMITS("enrich-commits", ProxyMode.TRANSPARENT),
    /**
     * Lets an approved re-push through without repeating validation; server mode blocks synchronously in one request.
     */
    ALLOW_APPROVED_PUSH("allow-approved-push", ProxyMode.TRANSPARENT),
    /** Renders accumulated per-step results into the one buffered response; server mode streams messages live. */
    VALIDATION_SUMMARY("validation-summary", ProxyMode.TRANSPARENT),
    /** Finalizes proxied fetch (read) requests; server mode's fetch path is upload-pack, separate from this chain. */
    FETCH_FINALIZER("fetch-finalizer", ProxyMode.TRANSPARENT),
    /** Sends the final buffered response and triggers forwarding; server mode forwards in a post-receive hook. */
    PUSH_FINALIZER("push-finalizer", ProxyMode.TRANSPARENT),
    /** Request-level audit logging for the proxy; server mode's audit trail is a pinned lifecycle hook. */
    AUDIT_LOG("audit-log", ProxyMode.TRANSPARENT);

    private final String key;
    private final Set<ProxyMode> modes;

    PushStepKind(String key, ProxyMode... modes) {
        this.key = key;
        this.modes = Set.of(modes);
    }

    /** Stable, provider- and mode-independent identifier, used as the persisted step name and the telemetry label. */
    public String key() {
        return key;
    }

    /** The proxy modes this step runs in. */
    public Set<ProxyMode> modes() {
        return modes;
    }

    /** Whether this step runs in the given proxy mode. */
    public boolean runsIn(ProxyMode mode) {
        return modes.contains(mode);
    }
}
