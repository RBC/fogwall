package com.rbc.fogwall.dashboard.compose;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Runs a real command-line tool and collects what it wrote.
 *
 * <p>The compose suite runs git and the SCM CLIs as real binaries, so it exercises the packaged stack against the
 * clients developers actually use.
 */
final class Cli {

    /** Long enough for a held push to be refused, short enough that a hung one fails the test rather than the run. */
    private static final int TIMEOUT_SECONDS = 120;

    private Cli() {}

    /** Exit status and output, streams merged as a user would see them. */
    record Result(int exitCode, String output) {
        boolean succeeded() {
            return exitCode == 0;
        }

        boolean mentions(String text) {
            return output.contains(text);
        }
    }

    static Result run(Path directory, Map<String, String> environment, String... command)
            throws IOException, InterruptedException {
        var builder = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true);
        builder.environment().putAll(environment);
        Process process = builder.start();
        List<String> lines = new ArrayList<>();
        try (var reader = process.inputReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException(String.join(" ", command) + " did not finish in " + TIMEOUT_SECONDS + "s");
        }
        return new Result(process.exitValue(), String.join("\n", lines));
    }

    /** Whether a binary is on the PATH, so a missing tool becomes a skipped test rather than a failure. */
    static boolean available(String binary) {
        try {
            return run(Path.of("."), Map.of(), binary, "--version").succeeded();
        } catch (Exception e) {
            return false;
        }
    }
}
