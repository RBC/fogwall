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
     * TTL for the node-ID → owner/repo resolution cache, an ISO-8601 duration (e.g. {@code PT5M}). This is a security
     * parameter, not just a perf knob — see the SCM API proxy notes: a node ID can outlive a repo rename/transfer while
     * the owner/repo it resolves to changes underneath it. Kept conservative by default.
     */
    private String nodeIdCacheTtl = "PT5M";

    /**
     * Literals and patterns refused in the prose an SCM API entity carries — a pull/merge request or issue title or
     * description, a comment body. Empty by default. Secret scanning is separate, keying off the global
     * {@code secret-scan} settings.
     */
    private BlockSettings block = new BlockSettings();
}
