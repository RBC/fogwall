package com.rbc.fogwall.db.model;

/** Why fogwall refused a clone or fetch. A fixed set, so refusals aggregate into a bounded number of rows. */
public enum FetchRefusal {
    /** No URL rule allowed the repository. */
    NOT_IN_ALLOW_LIST,
    /** A URL deny rule matched the repository. */
    DENY_RULE,
    /** The provider does not serve fetches. */
    FETCH_DISABLED,
    /** A fogwall credential was invalid, expired, revoked, or not accepted for the provider. */
    CREDENTIAL_REFUSED,
    /** A fogwall credential was valid, but the linked SCM account's token could not be used. */
    LINKED_TOKEN_UNUSABLE,
    /** An SSH client connected without agent forwarding, which upstream authentication needs. */
    SSH_AGENT_MISSING
}
