package com.rbc.fogwall.config;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.Data;

/**
 * Runtime configuration for per-commit validation: identity verification, author email rules, and commit message
 * blocking.
 *
 * <p>Push-level checks (diff content scanning and secret scanning) live in {@link DiffScanConfig} and
 * {@link SecretScanConfig} respectively.
 *
 * <p>Hot-reloadable via {@code POST /api/config/reload?section=commit}.
 */
@Data
@Builder
public class CommitConfig {

    /** Per-check mode for identity verification. */
    public enum IdentityVerificationMode {
        /** Block the push when the email is not registered to the push user. */
        STRICT,
        /** Warn the push user but allow the push through. */
        WARN,
        /** Skip this check entirely. */
        OFF;

        public static IdentityVerificationMode fromString(String value) {
            if (value == null) return WARN;
            return switch (value.trim().toLowerCase()) {
                case "strict" -> STRICT;
                case "off" -> OFF;
                default -> WARN;
            };
        }
    }

    /**
     * Independent mode settings for each identity check. Committer email is the primary mechanism for linking commit
     * data back to a fogwall identity — it identifies who last touched the commit object (or the rebaser/amender).
     * Author email is attribution metadata and should not gate push access for most workflows.
     */
    @Data
    @Builder
    public static class IdentityVerificationConfig {

        /** Check that each commit's committer email is registered to the push user. Default: {@code WARN}. */
        @Builder.Default
        private IdentityVerificationMode committer = IdentityVerificationMode.WARN;

        /** Check that each commit's author email is registered to the push user. Default: {@code OFF}. */
        @Builder.Default
        private IdentityVerificationMode author = IdentityVerificationMode.OFF;

        /** {@code true} when both checks are off — used to short-circuit resolver calls. */
        public boolean isEffectivelyOff() {
            return committer == IdentityVerificationMode.OFF && author == IdentityVerificationMode.OFF;
        }
    }

    /**
     * Per-check identity verification configuration. Committer defaults to {@code WARN}; author defaults to {@code OFF}
     * so rebase workflows (which preserve the original author email) are not blocked by default.
     */
    @Builder.Default
    private IdentityVerificationConfig identityVerification =
            IdentityVerificationConfig.builder().build();

    /** Configuration for author email validation. */
    @Builder.Default
    private AuthorConfig author = AuthorConfig.builder().build();

    /** Configuration for committer email validation. */
    @Builder.Default
    private CommitterConfig committer = CommitterConfig.builder().build();

    /** Configuration for commit message validation. */
    @Builder.Default
    private MessageConfig message = MessageConfig.builder().build();

    /** Configuration for author validation. */
    @Data
    @Builder
    public static class AuthorConfig {

        /** Configuration for email validation. */
        @Builder.Default
        private EmailConfig email = EmailConfig.builder().build();
    }

    /** Configuration for committer validation. */
    @Data
    @Builder
    public static class CommitterConfig {

        /** Configuration for email validation. */
        @Builder.Default
        private EmailConfig email = EmailConfig.builder().build();
    }

    /** Configuration for email validation. */
    @Data
    @Builder
    public static class EmailConfig {

        /** Configuration for email domain validation. */
        @Builder.Default
        private DomainConfig domain = DomainConfig.builder().build();

        /** Configuration for email local part validation. */
        @Builder.Default
        private LocalConfig local = LocalConfig.builder().build();
    }

    /** Configuration for email domain validation. */
    @Data
    @Builder
    public static class DomainConfig {

        /** Regex pattern for allowed email domains. If set, only emails matching this pattern are allowed. */
        private Pattern allow;
    }

    /** Configuration for email local part validation. */
    @Data
    @Builder
    public static class LocalConfig {

        /** Regex pattern for blocked email local parts. If set, emails matching this pattern are blocked. */
        private Pattern block;
    }

    /** Configuration for commit message validation. */
    @Data
    @Builder
    public static class MessageConfig {

        /** Configuration for blocking specific message patterns. */
        @Builder.Default
        private BlockConfig block = BlockConfig.builder().build();
    }

    /**
     * Shared block-list config: literal strings (case-insensitive match) and compiled regex patterns. Used by commit
     * message validation ({@link MessageConfig}) and diff content scanning ({@link DiffScanConfig}).
     */
    @Data
    @Builder
    public static class BlockConfig {

        /** List of literal strings that are blocked. Matching is case-insensitive. */
        @Builder.Default
        private List<String> literals = new ArrayList<>();

        /** List of compiled regex patterns that are blocked. */
        @Builder.Default
        private List<Pattern> patterns = new ArrayList<>();
    }

    /** Create a default configuration with no restrictions. */
    public static CommitConfig defaultConfig() {
        return CommitConfig.builder().build();
    }
}
