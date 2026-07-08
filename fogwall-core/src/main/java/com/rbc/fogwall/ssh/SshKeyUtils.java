package com.rbc.fogwall.ssh;

import java.security.PublicKey;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import org.apache.sshd.common.digest.BuiltinDigests;

/** Utilities for parsing and fingerprinting SSH public keys. */
public final class SshKeyUtils {

    private SshKeyUtils() {}

    /**
     * Parse a public key line and compute its SHA-256 fingerprint in OpenSSH format ({@code SHA256:...}).
     *
     * @param publicKeyLine algorithm + base64 body, with or without a trailing comment
     * @return {@code SHA256:<base64>} fingerprint
     * @throws IllegalArgumentException if the key cannot be parsed
     */
    public static String fingerprint(String publicKeyLine) {
        return fingerprint(parse(publicKeyLine));
    }

    /** Compute the SHA-256 fingerprint of an already-parsed public key. */
    public static String fingerprint(PublicKey key) {
        try {
            return KeyUtils.getFingerPrint(BuiltinDigests.sha256, key);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to fingerprint SSH key", e);
        }
    }

    /**
     * Parse an OpenSSH public key line. Strips trailing comments so MINA SSHD does not misidentify them as hostname
     * patterns.
     *
     * @throws IllegalArgumentException if the line is blank, malformed, or of an unsupported type
     */
    public static PublicKey parse(String publicKeyLine) {
        if (publicKeyLine == null || publicKeyLine.isBlank()) {
            throw new IllegalArgumentException("Public key must not be blank");
        }
        String trimmed = publicKeyLine.trim();
        // Strip optional comment (third token) — keep only "algo base64"
        String[] parts = trimmed.split("\\s+", 3);
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid SSH public key format (expected 'algo base64 [comment]')");
        }
        String keyLine = parts[0] + " " + parts[1];
        try {
            AuthorizedKeyEntry entry = AuthorizedKeyEntry.parseAuthorizedKeyEntry(keyLine);
            PublicKey key = entry.resolvePublicKey(null, PublicKeyEntryResolver.IGNORING);
            if (key == null) {
                throw new IllegalArgumentException("Unsupported SSH key type: " + parts[0]);
            }
            return key;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse SSH public key: " + e.getMessage(), e);
        }
    }

    /** Normalise a public key line to {@code algo base64} with no comment. */
    public static String normalise(String publicKeyLine) {
        String[] parts = publicKeyLine.trim().split("\\s+", 3);
        if (parts.length < 2) throw new IllegalArgumentException("Invalid SSH public key format");
        return parts[0] + " " + parts[1];
    }
}
