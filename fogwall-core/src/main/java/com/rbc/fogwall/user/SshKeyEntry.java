package com.rbc.fogwall.user;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

/** An SSH public key registered by a proxy user for SSH git transport authentication. */
@Value
@Builder
public class SshKeyEntry {
    String id;
    String username;

    /** SHA-256 fingerprint in OpenSSH format ({@code SHA256:...}). Used as the lookup key at auth time. */
    String fingerprint;

    /** Full public key body — algorithm + base64, no comment (e.g. {@code ssh-ed25519 AAAA...}). */
    String publicKey;

    /** Optional human-readable label (e.g. "work laptop"). */
    String label;

    Instant createdAt;
}
