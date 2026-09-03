package com.rbc.fogwall.crypto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM {@link TokenCipher}. The stored blob is {@code IV (12 bytes) || ciphertext || GCM tag (16 bytes)} — no
 * separate IV column needed, and GCM's tag authenticates the whole blob so tampering is detected on decrypt.
 *
 * <p>The key is provided once at construction time, sourced from a platform-provided file (never generated or stored by
 * fogwall itself — see {@link #loadKeyFromFile(Path)}), consistent with fogwall's "no KMS custody" stance for
 * credential material.
 */
public class AesGcmTokenCipher implements TokenCipher {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmTokenCipher(byte[] keyBytes) {
        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException(
                    "AES-256-GCM key must be " + KEY_LENGTH_BYTES + " bytes, got " + keyBytes.length);
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    /** Reads a base64-encoded 32-byte key from a file (e.g. a mounted secret). */
    public static byte[] loadKeyFromFile(Path path) {
        try {
            String encoded = Files.readString(path).strip();
            byte[] keyBytes = Base64.getDecoder().decode(encoded);
            if (keyBytes.length != KEY_LENGTH_BYTES) {
                throw new IllegalStateException("Token encryption key at " + path + " must decode to "
                        + KEY_LENGTH_BYTES + " bytes, got " + keyBytes.length);
            }
            return keyBytes;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load token encryption key from " + path, e);
        }
    }

    /**
     * Loads the key from {@code path} if it exists, otherwise generates a fresh 256-bit key with {@link SecureRandom},
     * base64-encodes it, and writes it to {@code path} (creating parent directories, restricting permissions to owner
     * read/write where the filesystem supports POSIX permissions). Used only for local-devex convenience when no
     * {@code token-encryption-key-path} is explicitly configured — production deployments should always set that key
     * explicitly to a durable location instead of relying on this.
     *
     * @throws IllegalStateException if a file already exists at {@code path} but does not decode to a valid key
     */
    public static byte[] loadOrGenerateKeyFile(Path path) {
        if (Files.exists(path)) {
            return loadKeyFromFile(path);
        }
        byte[] keyBytes = new byte[KEY_LENGTH_BYTES];
        new SecureRandom().nextBytes(keyBytes);
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, Base64.getEncoder().encodeToString(keyBytes));
            trySetOwnerOnlyPermissions(path);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write auto-generated token encryption key to " + path, e);
        }
        return keyBytes;
    }

    private static void trySetOwnerOnlyPermissions(Path path) {
        try {
            Set<PosixFilePermission> ownerReadWrite =
                    EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(path, ownerReadWrite);
        } catch (UnsupportedOperationException | IOException e) {
            // Non-POSIX filesystem (e.g. some Windows setups) — best effort only.
        }
    }

    @Override
    public byte[] encrypt(byte[] plaintext) {
        byte[] iv = new byte[IV_LENGTH_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            byte[] blob = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, blob, 0, iv.length);
            System.arraycopy(ciphertext, 0, blob, iv.length, ciphertext.length);
            return blob;
        } catch (GeneralSecurityException e) {
            throw new TokenCipherException("Failed to encrypt token", e);
        }
    }

    @Override
    public byte[] decrypt(byte[] blob) {
        if (blob.length < IV_LENGTH_BYTES) {
            throw new TokenCipherException("Encrypted blob too short to contain an IV");
        }
        byte[] iv = Arrays.copyOfRange(blob, 0, IV_LENGTH_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(blob, IV_LENGTH_BYTES, blob.length);
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new TokenCipherException("Failed to decrypt token (wrong key or corrupted/tampered data)", e);
        }
    }
}
