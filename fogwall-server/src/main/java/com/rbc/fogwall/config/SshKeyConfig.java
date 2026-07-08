package com.rbc.fogwall.config;

import lombok.Data;

/** Binds a single entry in the {@code ssh-keys:} list under a user in fogwall.yml. */
@Data
public class SshKeyConfig {

    /** Full public key line — algorithm + base64 + optional comment (e.g. {@code ssh-ed25519 AAAA... label}). */
    private String publicKey = "";

    /** Human-readable label shown in the profile UI. Defaults to the comment token in the key line if blank. */
    private String label = "";
}
