package com.rbc.fogwall.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * Binds the {@code commit:} block in fogwall.yml. This is the raw YAML DTO — all pattern strings are kept as
 * {@code String} fields and compiled to {@link java.util.regex.Pattern} by {@link JettyConfigurationBuilder} when
 * constructing the core {@link CommitConfig}.
 *
 * <p>Contains only per-commit checks (commit attribution policy, author email, commit message). Push-level checks live
 * in {@link DiffScanSettings} and {@link SecretScanSettings}.
 */
@Data
public class CommitSettings {

    /** Per-check commit attribution policy modes (committer/author email vs the registered proxy user). */
    private CommitAttributionPolicySettings attributionPolicy = new CommitAttributionPolicySettings();

    /**
     * Deprecated, ignored alias for {@link #attributionPolicy}. Kept as a real field (defaulting to {@code null}) only
     * so the legacy {@code commit.identity-verification} key is still accepted by the config validator instead of
     * failing startup on an unknown property. Its value is deliberately not applied — {@link JettyConfigurationBuilder}
     * logs a migration warning when it is present and otherwise ignores it, forcing operators to move to the new key.
     *
     * @deprecated The {@code commit.identity-verification} key was renamed to {@code commit.attribution-policy} to
     *     reflect that it checks client-supplied commit email metadata, not pusher authentication. The old key is
     *     accepted but has no effect; migrate your configuration — this field will be removed in a future release.
     */
    @Deprecated
    private CommitAttributionPolicySettings identityVerification;

    @Data
    public static class CommitAttributionPolicySettings {
        /** Mode for committer email check: {@code warn} (default), {@code strict}, {@code off}. */
        private String committer = "warn";
        /** Mode for author email check: {@code warn}, {@code strict}, {@code off} (default). */
        private String author = "off";
    }

    private AuthorSettings author = new AuthorSettings();
    private CommitterSettings committer = new CommitterSettings();
    private MessageSettings message = new MessageSettings();
    private TrailersSettings trailers = new TrailersSettings();

    @Data
    public static class AuthorSettings {
        private EmailSettings email = new EmailSettings();
    }

    @Data
    public static class CommitterSettings {
        private EmailSettings email = new EmailSettings();
    }

    /**
     * Email-match policy. The {@link #rules} list is the current shape: symmetric allow/block across every dimension.
     * The legacy {@link #domain} allow / {@link #local} block keys are still accepted and folded into the rule list
     * (with a deprecation warning) by {@link JettyConfigurationBuilder}, so existing configs keep working.
     */
    @Data
    public static class EmailSettings {
        /** Unified allow/block rules (domain/local/address, literal/regex). */
        private List<RuleSettings> rules = new ArrayList<>();

        /**
         * @deprecated use a {@code rules} entry {@code {action: allow, field: domain, match: regex, value: ...}}. The
         *     old {@code domain.allow} key is still honoured for one minor release.
         */
        @Deprecated
        private DomainSettings domain = new DomainSettings();

        /**
         * @deprecated use a {@code rules} entry {@code {action: block, field: local, match: regex, value: ...}}. The
         *     old {@code local.block} key is still honoured for one minor release.
         */
        @Deprecated
        private LocalSettings local = new LocalSettings();
    }

    /** One allow/block rule: {@code {action, field, match, value}}. */
    @Data
    public static class RuleSettings {
        /** {@code allow} or {@code block}. */
        private String action = "";
        /** {@code domain}, {@code local}, or {@code address}. */
        private String field = "";
        /** {@code literal} or {@code regex} (default {@code regex}). */
        private String match = "regex";
        /** The literal string or regex source. */
        private String value = "";
    }

    @Data
    public static class DomainSettings {
        /** Regex the email domain must match. Empty = allow all. */
        private String allow = "";
    }

    @Data
    public static class LocalSettings {
        /** Regex blocking specific local-parts (the part before @). Empty = allow all. */
        private String block = "";
    }

    @Data
    public static class MessageSettings {
        private BlockSettings block = new BlockSettings();
    }

    /** Commit-trailer policy: DCO {@code Signed-off-by} and {@code Co-authored-by} rules (fogwall#146). */
    @Data
    public static class TrailersSettings {
        private SignedOffBySettings signedOffBy = new SignedOffBySettings();
        private CoAuthoredBySettings coAuthoredBy = new CoAuthoredBySettings();
    }

    @Data
    public static class SignedOffBySettings {
        /** Require each commit to carry a {@code Signed-off-by} trailer (DCO). */
        private boolean require = false;
        /** When requiring sign-off, also require a trailer whose email matches the commit author. */
        private boolean requireAuthorMatch = false;
    }

    @Data
    public static class CoAuthoredBySettings {
        /** {@code off} (default), {@code ban}, {@code allowlist}, or {@code require}. */
        private String policy = "off";
        /** Email allowlist filter applied to each co-author under the {@code allowlist} policy. */
        private EmailSettings email = new EmailSettings();
    }
}
