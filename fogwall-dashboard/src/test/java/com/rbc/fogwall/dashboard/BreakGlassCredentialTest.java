package com.rbc.fogwall.dashboard;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class BreakGlassCredentialTest {

    @Test
    void generateKey_returnsNonBlank32CharBase64() {
        String key = SecurityConfig.generateBreakGlassKey();
        assertNotNull(key);
        assertFalse(key.isBlank());
        // 24 raw bytes → 32 URL-safe base64 chars (no padding)
        assertEquals(32, key.length());
    }

    @Test
    void generateKey_eachCallProducesDifferentValue() {
        String a = SecurityConfig.generateBreakGlassKey();
        String b = SecurityConfig.generateBreakGlassKey();
        assertNotEquals(a, b);
    }

    @Test
    void generateKey_onlyUrlSafeBase64Chars() {
        String key = SecurityConfig.generateBreakGlassKey();
        assertTrue(key.matches("[A-Za-z0-9_-]+"), "Key contains non-URL-safe characters: " + key);
    }

    @Test
    void writeToken_writesKeyFollowedByNewline(@TempDir Path tempDir) throws Exception {
        Path tokenFile = tempDir.resolve("break-glass.token");
        String key = "test-key-value";
        SecurityConfig.writeBreakGlassToken(key, tokenFile);

        assertTrue(Files.exists(tokenFile));
        String content = Files.readString(tokenFile);
        assertEquals(key + System.lineSeparator(), content);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void writeToken_setsOwnerReadWritePermissions(@TempDir Path tempDir) throws Exception {
        Path tokenFile = tempDir.resolve("break-glass.token");
        SecurityConfig.writeBreakGlassToken("any-key", tokenFile);

        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(tokenFile);
        assertTrue(perms.contains(PosixFilePermission.OWNER_READ));
        assertTrue(perms.contains(PosixFilePermission.OWNER_WRITE));
        assertFalse(perms.contains(PosixFilePermission.GROUP_READ));
        assertFalse(perms.contains(PosixFilePermission.GROUP_WRITE));
        assertFalse(perms.contains(PosixFilePermission.OTHERS_READ));
        assertFalse(perms.contains(PosixFilePermission.OTHERS_WRITE));
    }
}
