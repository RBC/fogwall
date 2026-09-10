package com.rbc.fogwall.git;

import com.rbc.fogwall.db.model.PushStep;
import com.rbc.fogwall.db.model.StepStatus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

public class GitClientUtils {

    /** The zero/null OID used by git when a ref does not yet exist (new-branch push). */
    public static final String ZERO_OID = "0000000000000000000000000000000000000000";

    /**
     * ANSI color codes used to display colored messages in the console. Note that not all clients support ANSI color.
     * Certain client interactions (git fetch) may also restrict what is displayed.
     */
    @RequiredArgsConstructor
    @Getter
    public enum AnsiColor {
        RESET("\u001B[0m"),
        BLACK("\u001B[30m"),
        RED("\u001B[31m"),
        GREEN("\u001B[32m"),
        YELLOW("\u001B[33m"),
        BLUE("\u001B[34m"),
        MAGENTA("\u001B[35m"),
        CYAN("\u001B[36m"),
        WHITE("\u001B[37m");

        private final String value;

        @Override
        public String toString() {
            return value;
        }
    }

    /**
     * List of Unicode codepoints used to display symbols and/or emojis in the client response. Note that not all
     * clients support the same set of chracters. Certain client interactions (git fetch) may also restrict what is
     * displayed.
     */
    @RequiredArgsConstructor
    @Getter
    public enum SymbolCodes {
        // https://codepoints.net/U+26D4
        // https://emojipedia.org/no-entry#technical
        NO_ENTRY("\u26D4", "!!"),

        // https://codepoints.net/U+2705
        // https://emojipedia.org/check-mark-button#technical
        HEAVY_CHECK_MARK("\u2705", "ok"),

        // https://codepoints.net/U+26A0
        // https://emojipedia.org/warning#technical
        WARNING("\u26A0", "!"),

        // https://codepoints.net/U+274C
        // https://emojipedia.org/cross-mark#technical
        CROSS_MARK("\u274C", "x"),

        // Created using codepoints & the Character class due to the point values
        // falling outside the Unicode planes' range supported by Java's \\u escape
        // sequence syntax in Strings

        // https://codepoints.net/U+1F511
        // https://emojipedia.org/key#technical
        KEY(Character.toString(0x1F511), "*"),

        // https://codepoints.net/U+1F517
        // https://emojipedia.org/link#technical
        LINK(Character.toString(0x1F517), "->");

        private final String value;

        /** ASCII fallback used when {@code FOGWALL_NO_EMOJI} is set. */
        private final String text;

        /**
         * Returns the emoji representation of the symbol by appending the Unicode variation selector character. This is
         * used to ensure that the symbol is displayed as an emoji in clients that support it.
         *
         * @return the emoji representation of the symbol
         */
        public String emoji() {
            return value + "\uFE0F";
        }

        /**
         * Returns the plain ASCII text representation of the symbol. Used when {@code FOGWALL_NO_EMOJI} is set to avoid
         * terminals that ignore the Unicode text-variation selector (U+FE0E) still rendering emoji.
         *
         * @return the plain text representation of the symbol
         */
        public String plain() {
            return text;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    /**
     * Returns a message with a title and a message to the git client. It contains no color coding for operations which
     * don't support it such as fetch.
     *
     * @param title the title to display
     * @param message the message to display
     * @return the formatted message for the git client to display
     */
    public static String format(String title, String message) {
        return formatTitle(title) + formatMessage(message);
    }

    /**
     * Returns a message with a title and a message to the git client. The whole client message will be colored.
     *
     * @param title the title to display
     * @param message the message to display
     * @param color the color to use for the message
     * @return the formatted message for the git client to display with color. the last line will be reset to default
     *     color
     */
    public static String format(String title, String message, AnsiColor color) {
        if (!isColorEnabled()) color = null;
        return (color != null ? color : "") + format(title, message) + (color != null ? AnsiColor.RESET : "");
    }

    /**
     * Returns a message with a title and a message to the git client. The title and message will be colored.
     *
     * @param title the title to display
     * @param message the message to display
     * @param titleColor the color to use for the message title or {@code null} to use the default color
     * @param messageColor the color to use for the message content or {@code null} to use the default color
     * @return the formatted message for the git client to display with color. the last line will be reset to default
     *     color
     */
    public static String format(String title, String message, AnsiColor titleColor, AnsiColor messageColor) {
        if (!isColorEnabled()) {
            titleColor = null;
            messageColor = null;
        }
        String formattedTitle = (titleColor != null ? titleColor.getValue() : "") + formatTitle(title);
        String formattedMessage = (messageColor != null ? messageColor.getValue() : AnsiColor.RESET)
                + formatMessage(message)
                + AnsiColor.RESET;
        return formattedTitle + formattedMessage;
    }

    /**
     * Returns a message with a title and a message to the git client. If the operation is a Fetch, it converts matching
     * symbols from emoji to plain format and removes any color codes. If it's a Push, it doesn't modify the message or
     * title content.
     *
     * @param title the title to display
     * @param message the message to display
     * @param operation the git operation (Fetch or Push)
     * @return the formatted message for the git client to display
     */
    public static String formatForOperation(String title, String message, AnsiColor color, HttpOperation operation) {
        if (operation != HttpOperation.PUSH) {
            title = convertSymbolsToPlain(title);
            title = stripColors(title);
            message = convertSymbolsToPlain(message);
            message = stripColors(message);
            return format(title, message);
        } else {
            return format(title, message, color);
        }
    }

    private static String formatTitle(String content) {
        return "\n" + content + "\n";
    }

    private static String formatMessage(String content) {
        return "\n" + content + "\n";
    }

    private static String convertSymbolsToPlain(String content) {
        for (var color : SymbolCodes.values()) {
            content = content.replace(color.emoji(), color.plain());
        }
        return content;
    }

    public static String stripColors(String content) {
        for (var color : AnsiColor.values()) {
            content = content.replace(color.getValue(), "");
        }
        return content;
    }

    /** Returns {@code true} unless the {@code NO_COLOR} environment variable is set (any value). */
    public static boolean isColorEnabled() {
        return System.getenv("NO_COLOR") == null;
    }

    /** Returns {@code true} unless {@code FOGWALL_NO_EMOJI} is set. */
    public static boolean isEmojiEnabled() {
        return System.getenv("FOGWALL_NO_EMOJI") == null;
    }

    /**
     * Wraps {@code text} in an ANSI color + reset sequence when color is enabled; otherwise returns the text unchanged.
     */
    public static String color(AnsiColor c, String text) {
        if (!isColorEnabled()) return text;
        return c.getValue() + text + AnsiColor.RESET.getValue();
    }

    /** Returns the emoji variant of {@code s} when emoji is enabled, or the plain text variant otherwise. */
    public static String sym(SymbolCodes s) {
        return isEmojiEnabled() ? s.emoji() : s.plain();
    }

    /**
     * Builds a validation summary in the same two-line format as server mode streaming output:
     *
     * <pre>
     *   🔑  Checking author emails...
     *     ✅  emails OK
     * </pre>
     *
     * <p>Used by both the transparent proxy pipeline (delivered as a single batch at the end) and the server mode
     * pipeline (shown before the approval-gate message). Data steps (diff generation) and infrastructure steps are
     * excluded. Returns an empty string if there are no relevant steps.
     */
    public static String buildValidationSummary(List<PushStep> steps) {
        // Pure data / infrastructure steps that don't represent a user-visible check
        Set<String> skipSteps = Set.of(
                DiffGenerationHook.STEP_NAME_PUSH_DIFF,
                DiffGenerationHook.STEP_NAME_BRANCH_DIFF,
                "forward",
                PushStepKind.COMMIT_INSPECTION.key(),
                PushStepKind.PRIOR_PUSH_ENRICHMENT.key());

        // Human-readable label for each step's PushStepKind (header line). A step's stepName is its kind's key; every
        // kind shown to the client needs an entry here and in passResults below — a step missing from both silently
        // falls back to printing its raw key to the git client instead of failing the build.
        Map<String, String> labels = new HashMap<>(Map.ofEntries(
                Map.entry(PushStepKind.URL_RULE.key(), "Checking URL allow rules"),
                Map.entry(PushStepKind.PUSH_PERMISSION.key(), "Checking user permission"),
                Map.entry(PushStepKind.COMMIT_ATTRIBUTION.key(), "Checking commit attribution policy"),
                Map.entry(PushStepKind.EMPTY_BRANCH.key(), "Checking branch"),
                Map.entry(PushStepKind.HIDDEN_COMMITS.key(), "Checking for hidden commits"),
                Map.entry(PushStepKind.AUTHOR_EMAIL.key(), "Checking author emails"),
                Map.entry(PushStepKind.TRAILERS.key(), "Checking Co-Authored-By/Signed-off-by trailers"),
                Map.entry(PushStepKind.COMMIT_MESSAGE.key(), "Checking commit messages"),
                Map.entry(PushStepKind.CONTENT_PATTERN_MESSAGE.key(), "Scanning commit messages for PII/identifiers"),
                Map.entry(PushStepKind.DIFF_SCAN.key(), "Scanning diff content"),
                Map.entry(PushStepKind.GPG_SIGNATURE.key(), "Checking GPG signatures"),
                Map.entry(PushStepKind.SECRET_SCAN.key(), "Scanning for secrets"),
                Map.entry(PushStepKind.CONTENT_PATTERN_DIFF.key(), "Scanning diff for PII/identifiers"),
                Map.entry(PushStepKind.BINARY_BLOB.key(), "Scanning for binary blobs")));

        // Short pass-result text shown on the second line
        Map<String, String> passResults = new HashMap<>(Map.ofEntries(
                Map.entry(PushStepKind.URL_RULE.key(), "repository allowed"),
                Map.entry(PushStepKind.PUSH_PERMISSION.key(), "user authorized"),
                Map.entry(PushStepKind.COMMIT_ATTRIBUTION.key(), "commit emails OK"),
                Map.entry(PushStepKind.EMPTY_BRANCH.key(), "branch OK"),
                Map.entry(PushStepKind.HIDDEN_COMMITS.key(), "no hidden commits"),
                Map.entry(PushStepKind.AUTHOR_EMAIL.key(), "emails OK"),
                Map.entry(PushStepKind.TRAILERS.key(), "trailers OK"),
                Map.entry(PushStepKind.COMMIT_MESSAGE.key(), "messages OK"),
                Map.entry(PushStepKind.CONTENT_PATTERN_MESSAGE.key(), "no PII/identifiers detected"),
                Map.entry(PushStepKind.DIFF_SCAN.key(), "clean"),
                Map.entry(PushStepKind.GPG_SIGNATURE.key(), "signatures OK"),
                Map.entry(PushStepKind.SECRET_SCAN.key(), "no secrets detected"),
                Map.entry(PushStepKind.CONTENT_PATTERN_DIFF.key(), "no PII/identifiers detected"),
                Map.entry(PushStepKind.BINARY_BLOB.key(), "no blocked binary content")));

        List<PushStep> relevant = steps.stream()
                .filter(s -> s.getStepOrder() >= 100 && s.getStepOrder() <= 400)
                .filter(s -> !skipSteps.contains(s.getStepName()))
                .collect(Collectors.toList());
        if (relevant.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (PushStep step : relevant) {
            String label = labels.getOrDefault(step.getStepName(), step.getStepName());
            sb.append(color(AnsiColor.CYAN, sym(SymbolCodes.KEY) + "  " + label + "..."))
                    .append("\n");
            if (step.getStatus() == StepStatus.PASS) {
                String result = passResults.getOrDefault(step.getStepName(), "passed");
                sb.append(color(AnsiColor.GREEN, "  " + sym(SymbolCodes.HEAVY_CHECK_MARK) + "  " + result))
                        .append("\n");
            } else if (step.getStatus() == StepStatus.WARN) {
                appendWarningLines(sb, step.getContent());
            } else if (step.getStatus() == StepStatus.FAIL) {
                String detail = step.getErrorMessage() != null ? step.getErrorMessage() : "failed";
                sb.append(color(AnsiColor.RED, "  " + sym(SymbolCodes.CROSS_MARK) + "  " + detail))
                        .append("\n");
            } else if (step.getStatus() == StepStatus.SKIPPED) {
                sb.append(color(AnsiColor.YELLOW, "  " + sym(SymbolCodes.WARNING) + "  skipped"))
                        .append("\n");
            }
        }
        return sb.toString();
    }

    /** Appends each line of {@code content} to {@code sb} as a yellow warning line, prefixed with a warning symbol. */
    private static void appendWarningLines(StringBuilder sb, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        for (String line : content.split("\n")) {
            sb.append(color(AnsiColor.YELLOW, "  " + sym(SymbolCodes.WARNING) + "  " + line))
                    .append("\n");
        }
    }
}
