package com.rbc.fogwall.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ScmOAuthConfigTest {

    @Test
    void defaultConfig_isPermissive() {
        assertEquals(
                ScmOAuthConfig.IdentityMode.PERMISSIVE,
                ScmOAuthConfig.defaultConfig().getIdentityMode());
    }

    @Test
    void identityMode_fromString_strict() {
        assertEquals(ScmOAuthConfig.IdentityMode.STRICT, ScmOAuthConfig.IdentityMode.fromString("strict"));
        assertEquals(ScmOAuthConfig.IdentityMode.STRICT, ScmOAuthConfig.IdentityMode.fromString("STRICT"));
        assertEquals(ScmOAuthConfig.IdentityMode.STRICT, ScmOAuthConfig.IdentityMode.fromString(" strict "));
    }

    @Test
    void identityMode_fromString_permissiveAndDefaults() {
        assertEquals(ScmOAuthConfig.IdentityMode.PERMISSIVE, ScmOAuthConfig.IdentityMode.fromString("permissive"));
        assertEquals(ScmOAuthConfig.IdentityMode.PERMISSIVE, ScmOAuthConfig.IdentityMode.fromString(null));
        assertEquals(ScmOAuthConfig.IdentityMode.PERMISSIVE, ScmOAuthConfig.IdentityMode.fromString("garbage"));
    }

    @Test
    void builder_explicitStrictMode() {
        var config = ScmOAuthConfig.builder()
                .identityMode(ScmOAuthConfig.IdentityMode.STRICT)
                .build();

        assertEquals(ScmOAuthConfig.IdentityMode.STRICT, config.getIdentityMode());
    }
}
