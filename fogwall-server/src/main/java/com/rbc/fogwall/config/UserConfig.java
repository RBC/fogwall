package com.rbc.fogwall.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** Binds a single entry in the {@code users:} list in fogwall.yml. */
@Data
public class UserConfig {
    private String username = "";

    /** BCrypt password hash. Generate with any BCrypt tool; 12 rounds recommended. */
    private String passwordHash = "";

    /** Email addresses linked to this user (used for commit-author matching). */
    private List<String> emails = new ArrayList<>();

    /** SCM identities: provider + username pairs (e.g. github/alice). */
    private List<ScmIdentityConfig> scmIdentities = new ArrayList<>();

    /**
     * SSH public keys for this user. Keys defined here appear as locked in the profile UI and cannot be removed through
     * the dashboard — manage them by editing this config file.
     */
    private List<SshKeyConfig> sshKeys = new ArrayList<>();

    /** Roles granted to this user. Valid values: {@code USER}, {@code ADMIN}. Defaults to {@code [USER]} if not set. */
    private List<String> roles = new ArrayList<>();
}
