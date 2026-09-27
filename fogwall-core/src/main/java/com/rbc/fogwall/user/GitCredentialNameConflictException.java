package com.rbc.fogwall.user;

/** Thrown when a user already holds a git credential with the name a new one was given. */
public class GitCredentialNameConflictException extends RuntimeException {

    public GitCredentialNameConflictException(String username, String name) {
        this(username, name, null);
    }

    public GitCredentialNameConflictException(String username, String name, Throwable cause) {
        super("User '" + username + "' already has a git credential named '" + name + "'", cause);
    }
}
