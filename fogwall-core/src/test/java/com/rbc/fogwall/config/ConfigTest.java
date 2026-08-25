package com.rbc.fogwall.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ConfigTest {

    // --- CommitConfig ---

    @Test
    void commitConfig_defaultConfig_hasNoRestrictions() {
        CommitConfig config = CommitConfig.defaultConfig();
        assertNotNull(config);
        assertNotNull(config.getAuthor());
        assertNotNull(config.getAuthor().getEmail());
        assertNotNull(config.getAuthor().getEmail().getDomain());
        assertNotNull(config.getAuthor().getEmail().getLocal());
        assertNull(config.getAuthor().getEmail().getDomain().getAllow());
        assertNull(config.getAuthor().getEmail().getLocal().getBlock());
        assertNotNull(config.getMessage());
        assertNotNull(config.getMessage().getBlock());
        assertTrue(config.getMessage().getBlock().getLiterals().isEmpty());
        assertTrue(config.getMessage().getBlock().getPatterns().isEmpty());
    }

    @Test
    void commitConfig_builder_setsEmailDomainAllow() {
        Pattern domainPattern = Pattern.compile("example\\.com$");
        CommitConfig config = CommitConfig.builder()
                .author(CommitConfig.AuthorConfig.builder()
                        .email(CommitConfig.EmailConfig.builder()
                                .domain(CommitConfig.DomainConfig.builder()
                                        .allow(domainPattern)
                                        .build())
                                .build())
                        .build())
                .build();
        assertSame(domainPattern, config.getAuthor().getEmail().getDomain().getAllow());
    }

    @Test
    void commitConfig_builder_setsEmailLocalBlock() {
        Pattern blockPattern = Pattern.compile("^noreply$");
        CommitConfig config = CommitConfig.builder()
                .author(CommitConfig.AuthorConfig.builder()
                        .email(CommitConfig.EmailConfig.builder()
                                .local(CommitConfig.LocalConfig.builder()
                                        .block(blockPattern)
                                        .build())
                                .build())
                        .build())
                .build();
        assertSame(blockPattern, config.getAuthor().getEmail().getLocal().getBlock());
    }

    @Test
    void commitConfig_builder_setsMessageBlockLiterals() {
        CommitConfig config = CommitConfig.builder()
                .message(CommitConfig.MessageConfig.builder()
                        .block(CommitConfig.BlockConfig.builder()
                                .literals(List.of("WIP", "DO NOT MERGE"))
                                .build())
                        .build())
                .build();
        assertEquals(
                List.of("WIP", "DO NOT MERGE"), config.getMessage().getBlock().getLiterals());
    }

    @Test
    void commitConfig_builder_setsMessageBlockPatterns() {
        Pattern p = Pattern.compile("password\\s*=");
        CommitConfig config = CommitConfig.builder()
                .message(CommitConfig.MessageConfig.builder()
                        .block(CommitConfig.BlockConfig.builder()
                                .patterns(List.of(p))
                                .build())
                        .build())
                .build();
        assertEquals(1, config.getMessage().getBlock().getPatterns().size());
        assertSame(p, config.getMessage().getBlock().getPatterns().get(0));
    }

    // --- CommitConfig.CommitAttributionPolicyMode ---

    @Test
    void commitAttributionPolicyMode_fromString_null_returnsWarn() {
        assertEquals(
                CommitConfig.CommitAttributionPolicyMode.WARN,
                CommitConfig.CommitAttributionPolicyMode.fromString(null));
    }

    @Test
    void commitAttributionPolicyMode_fromString_strict_returnsStrict() {
        assertEquals(
                CommitConfig.CommitAttributionPolicyMode.STRICT,
                CommitConfig.CommitAttributionPolicyMode.fromString("strict"));
        assertEquals(
                CommitConfig.CommitAttributionPolicyMode.STRICT,
                CommitConfig.CommitAttributionPolicyMode.fromString("STRICT"));
    }

    @Test
    void commitAttributionPolicyMode_fromString_off_returnsOff() {
        assertEquals(
                CommitConfig.CommitAttributionPolicyMode.OFF,
                CommitConfig.CommitAttributionPolicyMode.fromString("off"));
    }

    @Test
    void commitAttributionPolicyMode_fromString_unknown_returnsWarn() {
        assertEquals(
                CommitConfig.CommitAttributionPolicyMode.WARN,
                CommitConfig.CommitAttributionPolicyMode.fromString("invalid"));
    }

    // --- GpgConfig ---

    @Test
    void gpgConfig_defaultConfig_isDisabled() {
        GpgConfig config = GpgConfig.defaultConfig();
        assertFalse(config.isEnabled());
        assertFalse(config.isRequireSignedCommits());
        assertNull(config.getTrustedKeysFile());
        assertNull(config.getTrustedKeysInline());
    }

    @Test
    void gpgConfig_builder_setsEnabled() {
        GpgConfig config =
                GpgConfig.builder().enabled(true).requireSignedCommits(true).build();
        assertTrue(config.isEnabled());
        assertTrue(config.isRequireSignedCommits());
    }

    @Test
    void gpgConfig_builder_setsTrustedKeysFile() {
        GpgConfig config =
                GpgConfig.builder().trustedKeysFile("/path/to/keys.asc").build();
        assertEquals("/path/to/keys.asc", config.getTrustedKeysFile());
    }

    @Test
    void gpgConfig_builder_setsTrustedKeysInline() {
        GpgConfig config =
                GpgConfig.builder().trustedKeysInline("-----BEGIN PGP...").build();
        assertEquals("-----BEGIN PGP...", config.getTrustedKeysInline());
    }
}
