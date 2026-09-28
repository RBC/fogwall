package com.rbc.fogwall.user;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Stores the git credentials fogwall issues to its users. Both database families implement this.
 *
 * <p>An implementation persists the secret's hash as it is given and never sees the secret itself.
 */
public interface GitCredentialStore {

    /**
     * Stores a new credential.
     *
     * @throws GitCredentialNameConflictException if the user already has a credential with this name
     */
    void save(GitCredential credential);

    /** Returns the credential with this id, if any. */
    Optional<GitCredential> findById(String id);

    /** Returns every credential issued to {@code username}, ordered by name. */
    List<GitCredential> findByUsername(String username);

    /**
     * Replaces the secret hash and validity of an existing credential, keeping its id and name. Returns whether the
     * credential existed.
     */
    boolean replaceSecret(String id, String secretHash, Instant createdAt, Instant expiresAt);

    /** Records that the credential authenticated a request at {@code usedAt}. */
    void recordUse(String id, Instant usedAt);

    /** Deletes the credential. Returns whether it existed. */
    boolean delete(String id);

    /** Deletes every credential issued to {@code username}, returning how many there were. */
    int deleteByUsername(String username);
}
