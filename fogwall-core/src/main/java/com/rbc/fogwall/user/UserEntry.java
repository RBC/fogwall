package com.rbc.fogwall.user;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/** Domain model for a proxy user loaded from config or the database. */
@Value
@Builder
public class UserEntry {
    String username;

    /** BCrypt password hash. Null when the user authenticates exclusively via an external IdP. */
    String passwordHash;

    /** Email addresses associated with this user (for commit author matching). */
    List<String> emails;

    /** SCM identities (provider + username) for this user. */
    List<ScmIdentity> scmIdentities;

    /** SSH public keys declared in config for this user. Locked — cannot be removed via the dashboard. */
    @Builder.Default
    List<SshKeyEntry> sshKeys = List.of();

    /**
     * Roles granted to this user (e.g. {@code USER}, {@code ADMIN}). Defaults to an empty list which is treated as
     * {@code [USER]} by callers that need at least one role.
     */
    @Builder.Default
    List<String> roles = List.of("USER");
}
