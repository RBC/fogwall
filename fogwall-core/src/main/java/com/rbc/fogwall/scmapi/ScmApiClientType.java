package com.rbc.fogwall.scmapi;

import java.util.Locale;

/**
 * The kind of client behind an SCM API proxy request, classified from its {@code User-Agent}.
 *
 * <p><b>This is not an authorization boundary.</b> {@code User-Agent} is chosen by the caller and trivially forged, so
 * nothing here may ever <i>widen</i> what a request is allowed to do. It is used only to narrow — a deployment can
 * refuse client types it never intends to serve — and to record the CLI version in the audit trail. A forged header
 * therefore buys an attacker nothing beyond the baseline the allowlist and permission engine already enforce, which is
 * exactly where a deployment without this filter sits.
 *
 * <p>The version each CLI advertises is the anchor for detecting a future wire-format break (see
 * docs/internals/SCM_API_PROXY.md), which is why the raw header is audited alongside the classification.
 */
public enum ScmApiClientType {

    /** {@code gh} — {@code User-Agent: GitHub CLI 2.98.0}. */
    GH_CLI,

    /** {@code glab} — {@code User-Agent: glab/v1.116.0 (linux, amd64)}. */
    GLAB_CLI,

    /** {@code tea} — {@code User-Agent: tea/0.15.1 (linux/amd64) go-sdk/v1.2.0}. */
    TEA_CLI,

    /** {@code fj} — {@code User-Agent: forgejo-cli/0.6.0 (https://codeberg.org/forgejo-contrib/forgejo-cli/)}. */
    FJ_CLI,

    /** A web browser. Never a legitimate client of this proxy: the CLIs are the entire intended audience. */
    BROWSER,

    /** Anything else, including a missing header — a bare {@code curl}, a script, or an unrecognised CLI version. */
    UNKNOWN;

    /** Whether this is one of the SCM CLIs the proxy exists to serve. */
    public boolean isKnownCli() {
        return this == GH_CLI || this == GLAB_CLI || this == TEA_CLI || this == FJ_CLI;
    }

    /**
     * Classifies a {@code User-Agent} header. Matching is deliberately loose — a CLI's exact format may change between
     * releases, and an over-tight match would reject a legitimate upgrade — because the classification only ever
     * narrows access, so a false {@link #UNKNOWN} is a visible failure rather than a silent bypass.
     */
    public static ScmApiClientType classify(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return UNKNOWN;
        String ua = userAgent.toLowerCase(Locale.ROOT);

        if (ua.startsWith("github cli")) return GH_CLI;
        if (ua.startsWith("glab/")) return GLAB_CLI;
        if (ua.startsWith("tea/")) return TEA_CLI;
        if (ua.startsWith("forgejo-cli/")) return FJ_CLI;

        // Checked after the CLIs: "Mozilla/5.0" is the browser tell, and no SCM CLI sends it.
        if (ua.startsWith("mozilla/") || ua.contains("applewebkit") || ua.contains("gecko/")) return BROWSER;

        return UNKNOWN;
    }
}
