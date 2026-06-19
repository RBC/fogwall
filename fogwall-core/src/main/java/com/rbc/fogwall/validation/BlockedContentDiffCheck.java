package com.rbc.fogwall.validation;

import com.rbc.fogwall.config.CommitConfig;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;

/**
 * {@link DiffCheck} implementation that scans unified diff content for blocked literals and patterns.
 *
 * <p>Only added lines (those prefixed with {@code +} in the unified diff, excluding the {@code +++} header) are
 * scanned. Deletions and context lines are ignored.
 *
 * <p>This check never fails-open - it always returns {@code Optional.of(...)}, with an empty list when no violations
 * are found.
 */
@RequiredArgsConstructor
public class BlockedContentDiffCheck implements DiffCheck {

    private final CommitConfig.BlockConfig block;

    @Override
    public Optional<List<Violation>> check(String diff) {
        if (block.getLiterals().isEmpty() && block.getPatterns().isEmpty()) {
            return Optional.of(List.of());
        }

        // Map from violation summary → first matching line (putIfAbsent deduplicates per pattern+file)
        Map<String, String> violations = new LinkedHashMap<>();
        String currentFile = null;

        for (String line : diff.lines().toList()) {
            if (line.startsWith("diff --git ")) {
                currentFile = extractFileName(line);
            }

            // Only scan added lines; skip the +++ file header
            if (!line.startsWith("+") || line.startsWith("+++")) {
                continue;
            }
            String content = line.substring(1);

            for (String literal : block.getLiterals()) {
                if (content.toLowerCase().contains(literal.toLowerCase())) {
                    String location = currentFile != null ? " in " + currentFile : "";
                    violations.putIfAbsent("blocked term: \"" + literal + "\"" + location, content.strip());
                }
            }

            for (Pattern pattern : block.getPatterns()) {
                if (pattern.matcher(content).find()) {
                    String location = currentFile != null ? " in " + currentFile : "";
                    violations.putIfAbsent("blocked pattern: " + pattern.pattern() + location, content.strip());
                }
            }
        }

        return Optional.of(violations.entrySet().stream()
                .map(e -> new Violation(e.getKey(), e.getKey(), e.getKey() + "\n  " + e.getValue()))
                .collect(java.util.stream.Collectors.toList()));
    }

    /** Extracts the {@code b/} path from a {@code diff --git a/... b/...} header line. */
    static String extractFileName(String diffHeader) {
        String[] parts = diffHeader.split(" ");
        if (parts.length >= 4) {
            String bPath = parts[3];
            return bPath.startsWith("b/") ? bPath.substring(2) : bPath;
        }
        return diffHeader;
    }
}
