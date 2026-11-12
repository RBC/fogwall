package com.rbc.fogwall.db.model;

/**
 * Outcome of a single SCM API proxy mutation, in the same terms as a push's {@link PushStatus}: forwarded, rejected, or
 * an error. Why a request was rejected is in the record's reason, as it is in a push's steps.
 *
 * <p>{@link #REJECTED} and {@link #ERROR} both mean the mutation never reached the upstream; they differ in whether
 * fogwall was able to decide. Keeping them apart is what makes filtering the audit trail for rejections show real
 * policy and content violations, rather than burying them under malformed requests and upstream failures.
 */
public enum ScmApiActionStatus {
    /** The mutation cleared the allowlist, permission and content checks and was relayed upstream. */
    FORWARDED,
    /**
     * fogwall decided no. Either the operation was not allowed (not allowlisted, the SCM API proxy is not enabled for
     * the provider, or the caller lacks the {@code PROPOSE} grant on the target repository), or its content was not (a
     * blocked term, pattern, or detected secret in the title, description, or comment body).
     */
    REJECTED,
    /**
     * fogwall could not reach a decision and refused rather than guess: the request would not parse, no target could be
     * extracted from it, the target could not be resolved to a repository, or the upstream forward failed. Fails closed
     * exactly as {@link #REJECTED} does; the distinction is about what the record means, not what the caller got.
     */
    ERROR
}
