package com.rbc.fogwall.config;

import lombok.Data;

/** Binds a single entry in the {@code scm-identities:} list under a user in fogwall.yml. */
@Data
public class ScmIdentityConfig {
    /**
     * Provider name (the YAML config map key, e.g. {@code github} or {@code internal-gitlab}). Must match a configured
     * provider name.
     */
    private String provider = "";

    /** Username on that provider. */
    private String username = "";
}
