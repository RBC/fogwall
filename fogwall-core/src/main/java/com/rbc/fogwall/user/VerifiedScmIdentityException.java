package com.rbc.fogwall.user;

/** Thrown when attempting to remove an OAuth-verified SCM identity via the generic remove endpoint (#40). */
public class VerifiedScmIdentityException extends RuntimeException {

    public VerifiedScmIdentityException(String provider, String scmUsername) {
        super("SCM identity " + provider + "/" + scmUsername
                + " is OAuth-verified and cannot be removed here — unlink it via the OAuth flow instead");
    }
}
