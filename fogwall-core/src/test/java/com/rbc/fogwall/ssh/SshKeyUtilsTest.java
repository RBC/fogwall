package com.rbc.fogwall.ssh;

import static org.junit.jupiter.api.Assertions.*;

import java.security.PublicKey;
import org.junit.jupiter.api.Test;

class SshKeyUtilsTest {

    // Generated test key — not used for any real authentication.
    private static final String ED25519_KEY =
            "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIIQiTzhWg82OVGUGpUMctA7FoBSZteJQ5R/TPaVfCC95";
    private static final String ED25519_FINGERPRINT = "SHA256:How4qIe4Zv++YhwM7SHXXTsLbFhIYNNJ7zPVfVQX3OA";

    @Test
    void fingerprint_ed25519_returnsExpectedSha256() {
        String fp = SshKeyUtils.fingerprint(ED25519_KEY);
        assertEquals(ED25519_FINGERPRINT, fp);
    }

    @Test
    void fingerprint_withTrailingComment_matchesSameKeyWithout() {
        String withComment = ED25519_KEY + " user@host";
        assertEquals(ED25519_FINGERPRINT, SshKeyUtils.fingerprint(withComment));
    }

    @Test
    void parse_returnsParsedPublicKey() {
        PublicKey key = SshKeyUtils.parse(ED25519_KEY);
        assertNotNull(key);
        assertEquals("Ed25519", key.getAlgorithm());
    }

    @Test
    void parse_withComment_stripsCommentAndParses() {
        PublicKey key = SshKeyUtils.parse(ED25519_KEY + " alice@laptop");
        assertNotNull(key);
    }

    @Test
    void parse_blank_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> SshKeyUtils.parse(""));
        assertThrows(IllegalArgumentException.class, () -> SshKeyUtils.parse("   "));
        assertThrows(IllegalArgumentException.class, () -> SshKeyUtils.parse(null));
    }

    @Test
    void parse_missingBase64_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> SshKeyUtils.parse("ssh-ed25519"));
    }

    @Test
    void parse_garbledBase64_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> SshKeyUtils.parse("ssh-ed25519 notbase64!!!"));
    }

    @Test
    void normalise_stripsComment() {
        String result = SshKeyUtils.normalise(ED25519_KEY + " alice@laptop");
        assertEquals(ED25519_KEY, result);
    }

    @Test
    void normalise_keyWithoutComment_returnsUnchanged() {
        assertEquals(ED25519_KEY, SshKeyUtils.normalise(ED25519_KEY));
    }

    @Test
    void normalise_extraWhitespace_normalised() {
        String result = SshKeyUtils.normalise(
                "  ssh-ed25519   AAAAC3NzaC1lZDI1NTE5AAAAIIQiTzhWg82OVGUGpUMctA7FoBSZteJQ5R/TPaVfCC95   comment  ");
        assertEquals(ED25519_KEY, result);
    }

    @Test
    void fingerprintPublicKey_matchesStringVariant() {
        PublicKey key = SshKeyUtils.parse(ED25519_KEY);
        assertEquals(ED25519_FINGERPRINT, SshKeyUtils.fingerprint(key));
    }
}
