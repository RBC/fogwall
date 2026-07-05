package com.rbc.fogwall.user;

/** Thrown when attempting to register an SSH key whose fingerprint is already claimed by another proxy user. */
public class SshKeyConflictException extends RuntimeException {

    private final String owner;

    public SshKeyConflictException(String fingerprint, String owner) {
        super("SSH key " + fingerprint + " is already registered to user: " + owner);
        this.owner = owner;
    }

    public String getOwner() {
        return owner;
    }
}
