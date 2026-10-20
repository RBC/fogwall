package com.rbc.fogwall.crypto;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
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

        assertFalse(Arrays.equals(blobA, blobB), "random IV should make repeated encryptions differ");
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
    void decodeKeyAcceptsBase64WithTrailingNewline() {
        byte[] key = randomKey();

        assertArrayEquals(key, AesGcmTokenCipher.decodeKey(Base64.getEncoder().encodeToString(key) + "\n"));
    }

    @Test
    void decodeKeyRejectsBase64OfFewerThan32Bytes() {
        var e = assertThrows(
                IllegalArgumentException.class,
                () -> AesGcmTokenCipher.decodeKey(Base64.getEncoder().encodeToString(new byte[31])));
        assertTrue(e.getMessage().contains("exactly 32 bytes, got 31"), e.getMessage());
    }

    @Test
    void decodeKeyRejectsBase64OfMoreThan32Bytes_ratherThanTruncating() {
        var e = assertThrows(
                IllegalArgumentException.class,
                () -> AesGcmTokenCipher.decodeKey(Base64.getEncoder().encodeToString(new byte[33])));
        assertTrue(e.getMessage().contains("exactly 32 bytes, got 33"), e.getMessage());
    }

    @Test
    void decodeKeyRejects32CharactersOfText_sinceKeysAreBase64Only() {
        // 32 characters of base64 alphabet decode to 24 bytes; there is no raw-bytes form to fall back to.
        var e = assertThrows(
                IllegalArgumentException.class, () -> AesGcmTokenCipher.decodeKey("0123456789abcdef0123456789abcdef"));
        assertTrue(e.getMessage().contains("got 24"), e.getMessage());
    }

    @Test
    void decodeKeyRejectsTextThatIsNotBase64_withoutQuotingIt() {
        var e = assertThrows(
                IllegalArgumentException.class,
                () -> AesGcmTokenCipher.decodeKey("not a key at all, and not base64!!!!!!!!!!!"));
        assertTrue(e.getMessage().contains("openssl rand -base64 32"), e.getMessage());
        assertFalse(e.getMessage().contains("!"), e.getMessage());
    }

    @Test
    void decodeKeyRejectsBase64AlphabetWithBrokenPadding() {
        var e = assertThrows(IllegalArgumentException.class, () -> AesGcmTokenCipher.decodeKey("AAAAA"));
        assertTrue(e.getMessage().contains("standard base64"), e.getMessage());
    }

    @Test
    void decodeKeyRejectsTheUrlSafeAlphabet() {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 0xff);

        assertThrows(
                IllegalArgumentException.class,
                () -> AesGcmTokenCipher.decodeKey(Base64.getUrlEncoder().encodeToString(key)));
    }

    @Test
    void decodeKeyRejectsALineBreakInsideTheKey() {
        String encoded = Base64.getEncoder().encodeToString(randomKey());

        assertThrows(
                IllegalArgumentException.class,
                () -> AesGcmTokenCipher.decodeKey(encoded.substring(0, 20) + "\n" + encoded.substring(20)));
    }

    @Test
    void decodeKeyRejectsDataAfterThePadding() {
        String encoded = Base64.getEncoder().encodeToString(randomKey());

        assertThrows(IllegalArgumentException.class, () -> AesGcmTokenCipher.decodeKey(encoded + "AAAA"));
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
