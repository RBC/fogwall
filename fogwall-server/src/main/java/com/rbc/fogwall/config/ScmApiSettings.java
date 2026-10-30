package com.rbc.fogwall.config;

import lombok.Data;

/**
 * Binds the {@code scm-api:} block in fogwall.yml — global settings for the SCM API proxy (opening and iterating on
 * pull/merge requests, issues and comments through fogwall), applying across all providers. Per-provider enablement
 * lives under {@code providers.<name>.scm-api} (see {@link ScmApiProviderSettings}), mirroring how {@code scm-oauth:}
 * splits global settings from per-provider app registration.
 */
@Data
public class ScmApiSettings {

    /**
     * Deprecated, ignored. Kept as a real field (defaulting to {@code null}) only so the key is still accepted by the
     * config validator instead of failing startup on an unknown property; {@link JettyConfigurationBuilder} logs a
     * warning when it is set.
     *
     * @deprecated GitHub node IDs and GitLab project IDs are resolved on every request, so there is no cache for this
     *     to bound. Remove the key — this field will be removed in a future release.
     */
    @Deprecated
    private String nodeIdCacheTtl;

    /**
     * Content block refused in the prose an SCM API entity carries — a pull/merge request or issue title or
     * description, a comment body. A list of matchers, or the deprecated {@code { literals, patterns }} object. Every
     * match blocks. Empty by default. Secret scanning is separate, keying off the global {@code secret-scan} settings.
     */
    private BlockSetting block = new BlockSetting();
}
