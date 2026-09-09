package com.rbc.fogwall.config;

import lombok.Data;

/**
 * Proposal settings for a single provider instance, nested under {@code providers.<name>.proposals}. See the
 * configuration reference and the SCM API proxy notes.
 *
 * <p>Opt-in per provider, default {@code false}: a deployment that doesn't proxy pull requests pays zero
 * registration/runtime cost for it, per CLAUDE.md's "don't raise baseline complexity for non-users" principle.
 */
@Data
public class ProposalsProviderSettings {

    /** Whether fogwall proxies proposal traffic for this provider. */
    private boolean enabled = false;

    /**
     * Dedicated listener port for this provider. Required when {@link #enabled} is set.
     *
     * <p>Each provider gets its own port because the CLIs cannot be redirected any other way: {@code gh} and {@code fj}
     * address the API from the host root and silently discard any path prefix (verified — see the "Client redirection"
     * section of the SCM API proxy notes), so the dialect must be mounted at {@code /} on a listener of its own. A port
     * per provider also keeps two instances of the same platform from colliding, since every GitLab speaks
     * {@code /api/v4} and every Gitea/Forgejo speaks {@code /api/v1}.
     */
    private int port = 0;

    /**
     * Refuse a proposal whose head commit fogwall has no push record for. Default {@code true}.
     *
     * <p>Relax it (set {@code false}) where these workflows are common: a rebase, amend, force-push, or a commit
     * authored in the SCM's own web UI changes only the SHA that identifies the code, not the code itself, yet leaves
     * the head with no matching push record — so the proposal is refused until the branch is pushed through fogwall.
     */
    private boolean requireValidatedHead = true;

    /**
     * Allow merging a pull/merge request through this provider's SCM API proxy. Default {@code false}. Independent of
     * the {@code MERGE} grant: both are required — the capability must be enabled here, and the caller must hold the
     * grant. Off by default because merge is the highest-consequence operation on this path, so exposing it is an
     * explicit operator decision rather than a side effect of enabling proposals.
     */
    private boolean mergeEnabled = false;
}
