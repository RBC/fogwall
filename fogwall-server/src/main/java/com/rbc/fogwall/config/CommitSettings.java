package com.rbc.fogwall.config;

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

    @Data
    public static class AuthorSettings {
        private EmailSettings email = new EmailSettings();
    }

    @Data
    public static class CommitterSettings {
        private EmailSettings email = new EmailSettings();
    }

    @Data
    public static class EmailSettings {
        private DomainSettings domain = new DomainSettings();
        private LocalSettings local = new LocalSettings();
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
}
