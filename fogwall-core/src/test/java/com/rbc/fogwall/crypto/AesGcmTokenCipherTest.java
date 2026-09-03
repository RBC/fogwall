package com.rbc.fogwall.crypto;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AesGcmTokenCipherTest {

    @TempDir
    Path tempDir;

    private static byte[] randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    @Test
    void roundTripsPlaintext() {
        var cipher = new AesGcmTokenCipher(randomKey());
        byte[] plaintext = "gho_supersecrettoken".getBytes(StandardCharsets.UTF_8);

        byte[] blob = cipher.encrypt(plaintext);
        byte[] decrypted = cipher.decrypt(blob);

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void producesDifferentCiphertextEachTime() {
        var cipher = new AesGcmTokenCipher(randomKey());
        byte[] plaintext = "same-plaintext".getBytes(StandardCharsets.UTF_8);

        byte[] blobA = cipher.encrypt(plaintext);
        byte[] blobB = cipher.encrypt(plaintext);

        assertFalse(java.util.Arrays.equals(blobA, blobB), "random IV should make repeated encryptions differ");
    }

    @Test
    void rejectsTamperedCiphertext() {
        var cipher = new AesGcmTokenCipher(randomKey());
        byte[] blob = cipher.encrypt("gho_supersecrettoken".getBytes(StandardCharsets.UTF_8));
        blob[blob.length - 1] ^= 0x01; // flip a bit in the GCM tag/ciphertext tail

        assertThrows(TokenCipherException.class, () -> cipher.decrypt(blob));
    }

    @Test
    void rejectsBlobTooShortToContainIv() {
        var cipher = new AesGcmTokenCipher(randomKey());

        assertThrows(TokenCipherException.class, () -> cipher.decrypt(new byte[] {1, 2, 3}));
    }

    @Test
    void decryptFailsUnderWrongKey() {
        var cipher = new AesGcmTokenCipher(randomKey());
        byte[] blob = cipher.encrypt("gho_supersecrettoken".getBytes(StandardCharsets.UTF_8));

        var otherCipher = new AesGcmTokenCipher(randomKey());

        assertThrows(TokenCipherException.class, () -> otherCipher.decrypt(blob));
    }

    @Test
    void rejectsWrongKeyLength() {
        assertThrows(IllegalArgumentException.class, () -> new AesGcmTokenCipher(new byte[16]));
    }

    @Test
    void loadKeyFromFileReadsBase64EncodedKey() throws Exception {
        byte[] key = randomKey();
        Path keyFile = tempDir.resolve("token-key");
        Files.writeString(keyFile, Base64.getEncoder().encodeToString(key));

        byte[] loaded = AesGcmTokenCipher.loadKeyFromFile(keyFile);

        assertArrayEquals(key, loaded);
    }

    @Test
    void loadKeyFromFileRejectsWrongLength() throws Exception {
        Path keyFile = tempDir.resolve("token-key");
        Files.writeString(keyFile, Base64.getEncoder().encodeToString(new byte[16]));

        assertThrows(IllegalStateException.class, () -> AesGcmTokenCipher.loadKeyFromFile(keyFile));
    }

    @Test
    void loadOrGenerateKeyFileGeneratesAndPersistsWhenAbsent() {
        Path keyFile = tempDir.resolve("nested/dir/token-key");

        byte[] generated = AesGcmTokenCipher.loadOrGenerateKeyFile(keyFile);

        assertEquals(32, generated.length);
        assertTrue(Files.exists(keyFile));
    }

    @Test
    void loadOrGenerateKeyFileReusesExistingKeyOnSubsequentCalls() {
        Path keyFile = tempDir.resolve("token-key");

        byte[] first = AesGcmTokenCipher.loadOrGenerateKeyFile(keyFile);
        byte[] second = AesGcmTokenCipher.loadOrGenerateKeyFile(keyFile);

        assertArrayEquals(first, second);
    }

    @Test
    void loadOrGenerateKeyFileRejectsCorruptExistingFile() throws Exception {
        Path keyFile = tempDir.resolve("token-key");
        Files.writeString(keyFile, Base64.getEncoder().encodeToString(new byte[16]));

        assertThrows(IllegalStateException.class, () -> AesGcmTokenCipher.loadOrGenerateKeyFile(keyFile));
    }
}
