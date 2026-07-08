package com.rbc.fogwall.user;

import java.util.List;
import java.util.Map;

/**
 * Full user store interface: extends read access with write operations for user, email, and SCM identity management.
 *
 * <p>Mutable backends ({@link JdbcUserStore}, {@link MongoUserStore}, {@link CompositeUserStore}) implement this.
 * {@link StaticUserStore} does not — it only implements {@link ReadOnlyUserStore}. Callers should check
 * {@code instanceof UserStore} and return {@code 501 Not Implemented} when the active store is read-only.
 */
public interface UserStore extends ReadOnlyUserStore {

    // ── email management ────────────────────────────────────────────────────────

    /** Add an email address claim for the given user. No-ops silently if already present. */
    void addEmail(String username, String email);

    /** Remove an email address claim for the given user. No-ops silently if not present. */
    void removeEmail(String username, String email);

    // ── SCM identity management ──────────────────────────────────────────────────

    /**
     * Add an SCM identity (provider + SCM username) for the given user. No-ops silently if already registered to this
     * user. Throws {@link ScmIdentityConflictException} if already claimed by a different user.
     */
    void addScmIdentity(String username, String provider, String scmUsername);

    /** Remove an SCM identity for the given user. No-ops silently if not present. */
    void removeScmIdentity(String username, String provider, String scmUsername);

    // ── user CRUD ────────────────────────────────────────────────────────────────

    /**
     * Create a new local user. Throws {@link IllegalArgumentException} if the username already exists.
     *
     * @param roles comma-separated roles string, e.g. {@code "USER"} or {@code "USER,ADMIN"}
     */
    void createUser(String username, String passwordHash, String roles);

    /**
     * Delete a user and all their associated data.
     *
     * @throws IllegalArgumentException if the user does not exist
     */
    void deleteUser(String username);

    /**
     * Update the password hash for an existing user.
     *
     * @throws IllegalArgumentException if the user does not exist
     */
    void setPassword(String username, String passwordHash);

    // ── IdP provisioning ─────────────────────────────────────────────────────────

    /**
     * Ensures a user row exists for IdP-authenticated users. No-op if already present. The password is left NULL so the
     * account cannot be used for form login.
     */
    void upsertUser(String username);

    /**
     * Ensures a user row exists and syncs the given roles on every IdP login. Roles are authoritative from the IdP —
     * any existing roles are overwritten so that IdP group changes take effect on next sign-in.
     */
    default void upsertUser(String username, List<String> roles) {
        upsertUser(username);
    }

    /** Inserts or updates an email for a user as locked (owned by the identity provider). */
    void upsertLockedEmail(String username, String email, String authSource);

    // ── enriched queries (for admin UI) ──────────────────────────────────────────

    /** Returns all email entries for a user with their verified, locked, and source status. */
    List<Map<String, Object>> findEmailsWithVerified(String username);

    /** Returns all SCM identity entries for a user with their verified status. */
    List<Map<String, Object>> findScmIdentitiesWithVerified(String username);

    // ── SSH key management ────────────────────────────────────────────────────────

    /**
     * Register an SSH public key for the given user.
     *
     * @param username the proxy username
     * @param fingerprint SHA-256 fingerprint in OpenSSH format ({@code SHA256:...}), pre-computed by the caller
     * @param publicKey normalised public key body (algorithm + base64, no comment)
     * @param label optional display label; may be null
     * @return the created {@link SshKeyEntry}
     * @throws IllegalArgumentException if the fingerprint is already registered to another user
     */
    SshKeyEntry addSshKey(String username, String fingerprint, String publicKey, String label);

    /**
     * Remove an SSH key by its ID. No-op if the key does not exist or does not belong to the user.
     *
     * @param username the proxy username (ownership check)
     * @param keyId the key UUID
     */
    void removeSshKey(String username, String keyId);

    /** Return all SSH keys registered for the given user. */
    List<SshKeyEntry> findSshKeys(String username);
}
