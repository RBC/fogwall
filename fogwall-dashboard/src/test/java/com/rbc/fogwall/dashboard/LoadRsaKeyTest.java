package com.rbc.fogwall.dashboard;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoadRsaKeyTest {

    @TempDir
    static Path tempDir;

    static Path keyFile;

    @BeforeAll
    static void generateKey() throws Exception {
        var kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        var kp = kpg.generateKeyPair();
        var privateKey = (RSAPrivateCrtKey) kp.getPrivate();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(privateKey.getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        keyFile = tempDir.resolve("test-key.pem");
        Files.writeString(keyFile, pem);
    }

    @Test
    void explicitKeyId_usedAsKid() throws Exception {
        var rsaKey = SecurityConfig.loadRsaKey(keyFile.toString(), "", "my-registered-kid");

        assertEquals("my-registered-kid", rsaKey.getKeyID());
        assertNull(rsaKey.getX509CertSHA256Thumbprint(), "x5t#S256 should not be set when using key-id");
    }

    @Test
    void noKeyIdNoCert_randomUuidKid() throws Exception {
        var rsaKey = SecurityConfig.loadRsaKey(keyFile.toString(), "", "");

        assertNotNull(rsaKey.getKeyID(), "A kid should always be set");
        assertDoesNotThrow(() -> java.util.UUID.fromString(rsaKey.getKeyID()), "kid should be a UUID");
        assertNull(rsaKey.getX509CertSHA256Thumbprint());
    }

    @Test
    void randomUuidKid_changesOnEachCall() throws Exception {
        var key1 = SecurityConfig.loadRsaKey(keyFile.toString(), "", "");
        var key2 = SecurityConfig.loadRsaKey(keyFile.toString(), "", "");

        assertNotEquals(key1.getKeyID(), key2.getKeyID(), "Each call without key-id should produce a distinct kid");
    }
}
