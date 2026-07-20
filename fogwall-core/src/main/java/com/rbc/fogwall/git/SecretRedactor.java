package com.rbc.fogwall.git;

import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStep;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Redacts raw secret values (from {@link GitleaksRunner.Finding#getSecret()}) out of a {@link PushRecord}'s step
 * content before it is persisted, so gitleaks findings never sit verbatim in the stored audit log. Called synchronously
 * before {@code pushStore.save(record)} in both proxy modes - see {@code PushStorePersistenceHook} (store-and-forward)
 * and {@code PushStoreAuditFilter} (transparent proxy).
 *
 * <p>A unified diff interleaves a {@code +}/{@code -}/space prefix between every original line, so a multi-line secret
 * (e.g. a PEM key) never appears contiguously in stored diff text - it has to be redacted line-by-line. Each secret is
 * split into individual lines up front; redaction then works line-by-line against the target text too, preferring an
 * exact match against a diff content line's de-prefixed body (the common case for a secret that occupies whole lines on
 * its own) and falling back to a substring replace within the line (handles a secret embedded mid-line, e.g.
 * {@code stripe_key = "sk_test_..."}, and non-diff text like step error/blocked messages).
 */
public final class SecretRedactor {

    private static final String REDACTED = "[REDACTED]";

    private SecretRedactor() {}

    /** No-op if {@code secrets} is null/empty or the record has no steps. */
    public static void redact(PushRecord record, List<String> secrets) {
        Set<String> secretLines = toLines(secrets);
        if (secretLines.isEmpty() || record.getSteps() == null) {
            return;
        }
        for (PushStep step : record.getSteps()) {
            step.setContent(redactText(step.getContent(), secretLines));
            step.setErrorMessage(redactText(step.getErrorMessage(), secretLines));
            step.setBlockedMessage(redactText(step.getBlockedMessage(), secretLines));
        }
        record.setBlockedMessage(redactText(record.getBlockedMessage(), secretLines));
    }

    /** Splits every secret into individual, trimmed, non-blank lines. */
    private static Set<String> toLines(List<String> secrets) {
        Set<String> lines = new LinkedHashSet<>();
        if (secrets == null) {
            return lines;
        }
        for (String secret : secrets) {
            if (secret == null || secret.isEmpty()) {
                continue;
            }
            for (String line : secret.split("\n")) {
                String trimmed = line.strip();
                if (!trimmed.isEmpty()) {
                    lines.add(trimmed);
                }
            }
        }
        return lines;
    }

    private static String redactText(String text, Set<String> secretLines) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            lines[i] = redactLine(lines[i], secretLines);
        }
        return String.join("\n", lines);
    }

    private static String redactLine(String line, Set<String> secretLines) {
        // Whole-line match: the entire de-prefixed line is nothing but secret material (e.g. a PEM body line)
        // - safe to replace the whole content, since there's nothing else on the line to preserve. Deliberately
        // stricter than a substring check here so a line like "API_KEY=sk_test_..." never takes this branch and
        // loses the variable name - see the fallback below.
        if (!line.isEmpty() && isDiffContentPrefix(line.charAt(0))) {
            String content = line.substring(1);
            if (secretLines.contains(content.strip())) {
                return line.charAt(0) + REDACTED;
            }
        }
        // Partial match: the secret is embedded alongside other content (a variable assignment, an env var name,
        // surrounding code). Only the matched span is replaced, preserving everything else on the line - a dev
        // reading the redacted diff still needs to see where the secret was, not just that the line existed.
        String result = line;
        for (String secretLine : secretLines) {
            result = result.replace(secretLine, REDACTED);
        }
        return result;
    }

    private static boolean isDiffContentPrefix(char c) {
        return c == '+' || c == '-' || c == ' ';
    }
}
