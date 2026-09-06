package com.rbc.fogwall.db.model;

/** Outcome of a single SCM API proxy mutation. */
public enum ScmApiActionStatus {
    /** The mutation cleared the allowlist and permission check and was relayed upstream. */
    FORWARDED,
    /** The mutation was rejected — not allowlisted, node ID unresolved, or the caller lacks PROPOSE. */
    DENIED,
    /** An error occurred while parsing, resolving, or forwarding the request. */
    ERROR
}
