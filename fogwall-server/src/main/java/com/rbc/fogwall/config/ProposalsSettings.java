package com.rbc.fogwall.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * Binds the {@code proposals:} block in fogwall.yml — global settings for proposing changes through fogwall (opening
 * and iterating on pull/merge requests), applying across all providers. Per-provider enablement lives under
 * {@code providers.<name>.proposals} (see {@link ProposalsProviderSettings}), mirroring how {@code scm-oauth:} splits
 * global settings from per-provider app registration.
 */
@Data
public class ProposalsSettings {

    /**
     * TTL for the node-ID → owner/repo resolution cache, an ISO-8601 duration (e.g. {@code PT5M}). This is a security
     * parameter, not just a perf knob — see docs/internals/SCM_API_PROXY.md §3c: a node ID can outlive a repo
     * rename/transfer while the owner/repo it resolves to changes underneath it. Kept conservative by default.
     */
    private String nodeIdCacheTtl = "PT5M";

    /**
     * Provider-level access rules ({@code com.rbc.fogwall.scmapi.ScmApiAccessRule}) — allow/deny READ/MUTATE/BOTH
     * proposal traffic per provider. Fail-closed: a provider with no matching allow rule denies everything even when
     * {@code providers.<name>.proposals.enabled} is true, mirroring the {@code rules:} block's allow[]/deny[] shape
     * (deny always takes precedence).
     */
    private List<ProposalsAccessRuleConfig> allow = new ArrayList<>();

    private List<ProposalsAccessRuleConfig> deny = new ArrayList<>();
}
