package com.rbc.fogwall.user;

/** Thrown when attempting to remove an SSH key that was locked by an identity provider (e.g. SCM OAuth import, #40). */
public class LockedSshKeyException extends RuntimeException {

    public LockedSshKeyException(String fingerprint) {
        super("SSH key is locked by identity provider and cannot be removed: " + fingerprint);
    }
}
