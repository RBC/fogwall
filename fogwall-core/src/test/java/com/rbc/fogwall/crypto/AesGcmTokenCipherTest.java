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
    void decodeKeyAcceptsRawBytes() {
        byte[] key = randomKey();

        assertArrayEquals(key, AesGcmTokenCipher.decodeKey(key));
    }

    @Test
    void decodeKeyAcceptsBase64WithTrailingNewline() {
        byte[] key = randomKey();
        byte[] text = (Base64.getEncoder().encodeToString(key) + "\n").getBytes(StandardCharsets.US_ASCII);

        assertArrayEquals(key, AesGcmTokenCipher.decodeKey(text));
    }

    @Test
    void decodeKeyRejectsBase64OfWrongLength_namingBothForms() {
        byte[] text = Base64.getEncoder().encodeToString(new byte[16]).getBytes(StandardCharsets.US_ASCII);

        var e = assertThrows(IllegalArgumentException.class, () -> AesGcmTokenCipher.decodeKey(text));
        assertTrue(e.getMessage().contains("32 raw bytes"), e.getMessage());
        assertTrue(e.getMessage().contains("base64"), e.getMessage());
    }

    @Test
    void decodeKeyRejectsTextThatIsNotBase64() {
        byte[] text = "not a key at all, and not base64!!!!!!!!!!!".getBytes(StandardCharsets.US_ASCII);

        assertThrows(IllegalArgumentException.class, () -> AesGcmTokenCipher.decodeKey(text));
    }

    @Test
    void decodeKeyRejectsRawKeyWithTrailingNewline() {
        byte[] material = new byte[33];
        material[32] = '\n';

        assertThrows(IllegalArgumentException.class, () -> AesGcmTokenCipher.decodeKey(material));
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
