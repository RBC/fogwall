package com.rbc.fogwall.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Runtime configuration for push-time content-pattern scanning against fogwall's built-in pattern bundles (structured
 * PII/identifier detection - SIN, SSN, NINO, etc. - distinct from credential-shaped secret scanning). Scans both diff
 * content and commit messages. Operator-authored custom patterns are out of scope here - that's already covered by
 * {@code DiffScanConfig}'s {@code block.literals}/{@code block.patterns}.
 *
 * <p><b>WARN-only.</b> There is no {@code mode}/ENFORCE option - a match never blocks a push, it's recorded as a
 * {@code WARN} step so the mandatory human reviewer sees it. Blocking on these patterns with no override path for a
 * false positive would be worse than the visibility gap this closes; ENFORCE support is intentionally left out until a
 * policy exception model exists to pair with it.
 *
 * <p>Bundles are selected by name via {@code bundles} (see {@code BuiltInPatternBundleSource} for what's shipped) -
 * nothing is scanned unless both {@code enabled} is true and at least one bundle is listed.
 *
 * <p>{@code scanDiff}/{@code scanCommitMessages} independently gate the two content sources - an operator who considers
 * commit messages low-risk (or wants to reduce push-summary noise) can disable that half without affecting diff
 * scanning, and vice versa. Both default {@code true}, matching the original all-content behavior.
 */
@Data
@Builder
public class ContentPatternConfig {

    @Builder.Default
    private boolean enabled = false;

    /**
     * Names of built-in bundles to scan with, e.g. {@code national-id-ca}, {@code national-id-us},
     * {@code national-id-gb}.
     */
    @Builder.Default
    private List<String> bundles = new ArrayList<>();

    @Builder.Default
    private boolean scanDiff = true;

    @Builder.Default
    private boolean scanCommitMessages = true;

    public static ContentPatternConfig defaultConfig() {
        return ContentPatternConfig.builder().build();
    }
}
