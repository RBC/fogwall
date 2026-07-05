package com.rbc.fogwall.user;

import java.util.List;
import java.util.Optional;

/** Read-access store for proxy users. Implemented by {@link StaticUserStore} for config-defined users. */
public interface ReadOnlyUserStore {

    /** Look up a user by proxy username. */
    Optional<UserEntry> findByUsername(String username);

    /** Look up a user by any of their registered email addresses. */
    Optional<UserEntry> findByEmail(String email);

    /** Look up a user by a provider-specific SCM username (e.g. GitHub login, GitLab username). */
    Optional<UserEntry> findByScmIdentity(String provider, String scmUsername);

    /**
     * Look up a user by an SSH public key fingerprint (SHA-256, OpenSSH format). Used during SSH authentication to map
     * a connecting client's key to a proxy user.
     */
    default Optional<UserEntry> findBySshFingerprint(String fingerprint) {
        return Optional.empty();
    }

    /** Return all known users. */
    List<UserEntry> findAll();
}
