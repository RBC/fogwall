package com.rbc.fogwall.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import lombok.RequiredArgsConstructor;

/**
 * Shared text-scanning primitive for content-pattern bundles. Given arbitrary text, applies every rule in every
 * configured {@link PatternBundle} and returns findings that pass both the rule's context-keyword requirement and
 * structural validator (if any).
 *
 * <p>Deliberately transport- and content-source-agnostic - {@code ContentPatternDiffCheck} and
 * {@code ContentPatternCommitMessageCheck} both wrap this same scanner rather than duplicating matching logic, so a
 * future content source (issue/PR comments, etc.) can reuse it too.
 */
@RequiredArgsConstructor
public class PatternBundleScanner {

    private final List<PatternBundle> bundles;

    /**
     * @param text arbitrary text to scan; may be null or blank
     * @return findings that passed context and validation checks; empty if none or if {@code text} is blank
     */
    public List<ContentPatternFinding> scan(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<ContentPatternFinding> findings = new ArrayList<>();
        for (PatternBundle bundle : bundles) {
            for (PatternRule rule : bundle.rules()) {
                Matcher matcher = rule.regex().matcher(text);
                while (matcher.find()) {
                    String matchedText = matcher.group();
                    if (!hasRequiredContext(text, matcher.start(), matcher.end(), rule)) {
                        continue;
                    }
                    if (rule.validator() != null && !rule.validator().isValid(matchedText)) {
                        continue;
                    }
                    findings.add(new ContentPatternFinding(rule.dataType(), bundle.jurisdiction(), matchedText));
                }
            }
        }
        return findings;
    }

    private static boolean hasRequiredContext(String text, int matchStart, int matchEnd, PatternRule rule) {
        if (rule.contextKeywords().isEmpty()) {
            return true;
        }
        int windowStart = Math.max(0, matchStart - rule.contextWindow());
        int windowEnd = Math.min(text.length(), matchEnd + rule.contextWindow());
        String window = text.substring(windowStart, windowEnd).toLowerCase(Locale.ROOT);
        return rule.contextKeywords().stream().anyMatch(keyword -> window.contains(keyword.toLowerCase(Locale.ROOT)));
    }
}
